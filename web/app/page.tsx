"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { FormEvent, KeyboardEvent, ReactNode } from "react";

type User = { id: string; username: string };
type OnlineUser = User & { lastSeen: string };
type ChatMessage = { id: string; sender: string; recipient: string | null; content: string; sentAt: string };
type ConversationKey = string;

const EVERYONE = "__everyone__";

function Icon({ name, size = 18 }: { name: string; size?: number }) {
  const common = { width: size, height: size, viewBox: "0 0 24 24", fill: "none", stroke: "currentColor", strokeWidth: 1.8, strokeLinecap: "round" as const, strokeLinejoin: "round" as const, "aria-hidden": true as const };
  const paths: Record<string, ReactNode> = {
    search: <><circle cx="10.8" cy="10.8" r="6.6" /><path d="m16 16 4 4" /></>,
    users: <><path d="M16 20v-1.3a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4V20" /><circle cx="9.5" cy="7.5" r="3.5" /><path d="M17 11a3.5 3.5 0 0 0 0-7M21 20v-1.3a4 4 0 0 0-3-3.9" /></>,
    send: <><path d="m21 3-7.4 18-3.9-7.7L2 9.4 21 3Z" /><path d="M9.7 13.3 21 3" /></>,
    moon: <path d="M20.4 15.5A8.3 8.3 0 0 1 8.5 3.6 8.5 8.5 0 1 0 20.4 15.5Z" />,
    sun: <><circle cx="12" cy="12" r="4" /><path d="M12 2v2m0 16v2M4.9 4.9l1.4 1.4m11.4 11.4 1.4 1.4M2 12h2m16 0h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" /></>,
    logout: <><path d="M10 17l5-5-5-5" /><path d="M15 12H3" /><path d="M12 3h6a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-6" /></>,
    menu: <><path d="M4 6h16M4 12h16M4 18h16" /></>,
    close: <><path d="m18 6-12 12M6 6l12 12" /></>,
    arrow: <><path d="M5 12h14" /><path d="m13 6 6 6-6 6" /></>,
    chat: <><path d="M21 11.5a8.4 8.4 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.4 8.4 0 0 1-3.8-.9L3 21l1.9-5.7a8.4 8.4 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.4 8.4 0 0 1 3.8-.9h.5a8.5 8.5 0 0 1 8 8v.5Z" /></>,
  };
  return <svg {...common}>{paths[name] ?? paths.chat}</svg>;
}

function initials(name: string) {
  return name.slice(0, 1).toUpperCase();
}

function displayTime(value: string) {
  return new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" }).format(new Date(value));
}

function displayDay(value: string) {
  const date = new Date(value);
  const now = new Date();
  if (date.toDateString() === now.toDateString()) return "Today";
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  if (date.toDateString() === yesterday.toDateString()) return "Yesterday";
  return new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric" }).format(date);
}

function errorFrom(response: Response, body: { error?: string }) {
  return body.error || (response.status === 503 ? "Chat service is unavailable." : "Something went wrong. Try again.");
}

export default function Home() {
  const [user, setUser] = useState<User | null>(null);
  const [ready, setReady] = useState(false);
  const [authMode, setAuthMode] = useState<"login" | "register">("login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [authError, setAuthError] = useState("");
  const [authBusy, setAuthBusy] = useState(false);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [onlineUsers, setOnlineUsers] = useState<OnlineUser[]>([]);
  const [activeConversation, setActiveConversation] = useState<ConversationKey>(EVERYONE);
  const [draft, setDraft] = useState("");
  const [search, setSearch] = useState("");
  const [searchOpen, setSearchOpen] = useState(false);
  const [sendError, setSendError] = useState("");
  const [sending, setSending] = useState(false);
  const [connected, setConnected] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [showPeople, setShowPeople] = useState(false);
  const [theme, setTheme] = useState<"light" | "dark">("light");
  const [unread, setUnread] = useState<Record<string, number>>({});
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const cursorRef = useRef("0");
  const activeConversationRef = useRef(activeConversation);
  const initialLoadedRef = useRef(false);

  useEffect(() => { activeConversationRef.current = activeConversation; }, [activeConversation]);

  useEffect(() => {
    const saved = window.localStorage.getItem("lan-chat-theme");
    if (saved === "dark") setTheme("dark");
    fetch("/api/auth/me", { cache: "no-store" })
      .then(async (response) => response.ok ? response.json() : { user: null })
      .then((data: { user: User | null }) => setUser(data.user))
      .catch(() => setUser(null))
      .finally(() => setReady(true));
  }, []);

  const refreshMessages = useCallback(async (initial = false) => {
    try {
      const url = initial ? "/api/messages" : `/api/messages?after=${encodeURIComponent(cursorRef.current)}`;
      const response = await fetch(url, { cache: "no-store" });
      if (!response.ok) {
        setConnected(false);
        return;
      }
      const data = await response.json() as { messages: ChatMessage[] };
      const incoming = data.messages ?? [];
      setConnected(true);
      if (initial) {
        setMessages(incoming);
        cursorRef.current = incoming.at(-1)?.id ?? "0";
        initialLoadedRef.current = true;
        requestAnimationFrame(() => messagesEndRef.current?.scrollIntoView({ behavior: "instant", block: "end" }));
        return;
      }
      if (incoming.length === 0) return;
      // The API only returns rows newer than cursorRef, so each result is already unique.
      const unique = incoming;
      cursorRef.current = incoming.at(-1)?.id ?? cursorRef.current;
      if (!unique.length) return;
      setMessages((current) => {
        const ids = new Set(current.map((message) => message.id));
        return [...current, ...unique.filter((message) => !ids.has(message.id))].slice(-300);
      });
      setUnread((current) => {
        const next = { ...current };
        for (const message of unique) {
          const key = message.recipient === null ? EVERYONE : (message.sender.toLowerCase() === user?.username.toLowerCase() ? message.recipient : message.sender);
          if (key && key.toLowerCase() !== activeConversationRef.current.toLowerCase()) next[key] = (next[key] ?? 0) + 1;
        }
        return next;
      });
      const list = listRef.current;
      const nearBottom = !list || list.scrollHeight - list.scrollTop - list.clientHeight < 120;
      if (nearBottom) requestAnimationFrame(() => messagesEndRef.current?.scrollIntoView({ behavior: "smooth", block: "end" }));
    } catch {
      setConnected(false);
    }
  }, [user?.username]);

  const refreshPresence = useCallback(async () => {
    try {
      const response = await fetch("/api/presence", { cache: "no-store" });
      if (!response.ok) return;
      const data = await response.json() as { users: OnlineUser[] };
      setOnlineUsers(data.users ?? []);
      setConnected(true);
    } catch { setConnected(false); }
  }, []);

  useEffect(() => {
    if (!user) return;
    let alive = true;
    let messageTimer: ReturnType<typeof setInterval> | undefined;
    let presenceTimer: ReturnType<typeof setInterval> | undefined;
    let heartbeatTimer: ReturnType<typeof setInterval> | undefined;

    const heartbeat = async () => {
      try { await fetch("/api/presence", { method: "POST", cache: "no-store" }); }
      catch { setConnected(false); }
    };
    const start = async () => {
      initialLoadedRef.current = false;
      await Promise.all([refreshMessages(true), refreshPresence(), heartbeat()]);
      if (!alive) return;
      messageTimer = setInterval(() => { if (initialLoadedRef.current) void refreshMessages(false); }, 2200);
      presenceTimer = setInterval(() => { void refreshPresence(); }, 9000);
      heartbeatTimer = setInterval(() => { void heartbeat(); }, 12000);
    };
    void start();
    return () => {
      alive = false;
      if (messageTimer) clearInterval(messageTimer);
      if (presenceTimer) clearInterval(presenceTimer);
      if (heartbeatTimer) clearInterval(heartbeatTimer);
    };
  }, [user?.id, refreshMessages, refreshPresence]);

  const people = onlineUsers.filter((person) => person.username.toLowerCase() !== user?.username.toLowerCase());

  const conversationMessages = useMemo(() => {
    const query = search.trim().toLowerCase();
    return messages.filter((message) => {
      const inConversation = activeConversation === EVERYONE
        ? message.recipient === null
        : ((message.sender.toLowerCase() === user?.username.toLowerCase() && message.recipient?.toLowerCase() === activeConversation.toLowerCase())
          || (message.recipient?.toLowerCase() === user?.username.toLowerCase() && message.sender.toLowerCase() === activeConversation.toLowerCase()));
      return inConversation && (!query || message.content.toLowerCase().includes(query) || message.sender.toLowerCase().includes(query));
    });
  }, [messages, activeConversation, search, user]);

  const conversationName = activeConversation === EVERYONE ? "Everyone" : activeConversation;
  const activePerson = people.find((person) => person.username.toLowerCase() === activeConversation.toLowerCase());

  async function submitAuth(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setAuthError("");
    setAuthBusy(true);
    try {
      const response = await fetch(`/api/auth/${authMode === "register" ? "register" : "login"}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password }),
      });
      const data = await response.json() as { user?: User; error?: string };
      if (!response.ok || !data.user) throw new Error(errorFrom(response, data));
      setUser(data.user);
      setUsername(data.user.username);
      setPassword("");
    } catch (error) {
      setAuthError(error instanceof Error ? error.message : "Could not sign in.");
    } finally { setAuthBusy(false); }
  }

  async function signOut() {
    await fetch("/api/auth/logout", { method: "POST" }).catch(() => undefined);
    setUser(null);
    setMessages([]);
    setOnlineUsers([]);
    setUnread({});
    cursorRef.current = "0";
    initialLoadedRef.current = false;
    setMobileMenuOpen(false);
  }

  async function sendMessage() {
    const content = draft.trim();
    if (!content || sending) return;
    setSending(true);
    setSendError("");
    try {
      const response = await fetch("/api/messages", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ content, recipient: activeConversation === EVERYONE ? null : activeConversation }),
      });
      const data = await response.json() as { message?: ChatMessage; error?: string };
      if (!response.ok || !data.message) throw new Error(errorFrom(response, data));
      setMessages((current) => current.some((message) => message.id === data.message!.id) ? current : [...current, data.message!].slice(-300));
      cursorRef.current = data.message.id;
      setDraft("");
      setConnected(true);
      requestAnimationFrame(() => messagesEndRef.current?.scrollIntoView({ behavior: "smooth", block: "end" }));
    } catch (error) {
      setSendError(error instanceof Error ? error.message : "Message could not be sent.");
    } finally { setSending(false); }
  }

  function chooseConversation(key: string) {
    setActiveConversation(key);
    activeConversationRef.current = key;
    setUnread((current) => ({ ...current, [key]: 0 }));
    setSearch("");
    setSearchOpen(false);
    setMobileMenuOpen(false);
  }

  function onComposerKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      void sendMessage();
    }
  }

  function toggleTheme() {
    const next = theme === "light" ? "dark" : "light";
    setTheme(next);
    window.localStorage.setItem("lan-chat-theme", next);
  }

  if (!ready) {
    return <main className="boot-screen"><span className="brand-mark"><Icon name="chat" size={22} /></span><span>Preparing your chat…</span></main>;
  }

  if (!user) {
    return (
      <main className={`auth-stage theme-${theme}`}>
        <header className="auth-topbar">
          <a className="brand-lockup" href="#home" aria-label="LAN Chat home">
            <span className="brand-mark"><Icon name="chat" size={20} /></span>
            <span>LAN<span className="brand-light">Chat</span></span>
          </a>
          <span className="topbar-note"><span className="status-dot" /> A better way to stay in sync</span>
        </header>
        <div className="auth-layout" id="home">
          <section className="auth-story">
            <div className="eyebrow"><span className="eyebrow-line" /> SIMPLE. PRIVATE. CONNECTED.</div>
            <h1>Good conversations<br /><span>start here.</span></h1>
            <p>A calm, focused place for your LAN Chat community. Sign in to catch up, or create an account to join in.</p>
            <div className="story-card">
              <div className="story-avatars"><span className="avatar avatar-coral">A</span><span className="avatar avatar-blue">R</span><span className="avatar avatar-olive">P</span><span className="avatar avatar-count">+3</span></div>
              <div><strong>Your people, in one place</strong><span>One room for the whole group.</span></div>
              <span className="story-arrow"><Icon name="arrow" size={17} /></span>
            </div>
            <div className="auth-footnote"><span className="footnote-icon">✦</span> Built for conversation, without the clutter.</div>
          </section>
          <section className="auth-card-wrap">
            <div className="auth-card">
              <div className="auth-card-heading">
                <div className="small-mark"><Icon name="chat" size={17} /></div>
                <span className="form-kicker">WELCOME {authMode === "login" ? "BACK" : "ABOARD"}</span>
                <h2>{authMode === "login" ? "Sign in" : "Create your account"}</h2>
                <p>{authMode === "login" ? "Pick up right where your group left off." : "Join the conversation in just a moment."}</p>
              </div>
              <form className="auth-form" onSubmit={submitAuth}>
                <label htmlFor="username">Username</label>
                <div className="input-wrap"><span className="input-prefix">@</span><input id="username" name="username" autoComplete="username" autoCapitalize="none" minLength={3} maxLength={20} value={username} onChange={(event) => setUsername(event.target.value)} placeholder="your_username" required /></div>
                <label htmlFor="password">Password</label>
                <div className="input-wrap"><span className="input-prefix password-dot">●</span><input id="password" name="password" type="password" autoComplete={authMode === "login" ? "current-password" : "new-password"} minLength={8} maxLength={128} value={password} onChange={(event) => setPassword(event.target.value)} placeholder="At least 8 characters" required /></div>
                {authError && <div className="form-error" role="alert">{authError}</div>}
                <button className="primary-button auth-submit" type="submit" disabled={authBusy}>
                  {authBusy ? <span className="spinner" /> : null}
                  {authBusy ? "Please wait…" : authMode === "login" ? "Sign in" : "Create account"}
                  {!authBusy && <Icon name="arrow" size={17} />}
                </button>
              </form>
              <div className="auth-switch">
                {authMode === "login" ? "New to the group?" : "Already have an account?"}
                <button type="button" onClick={() => { setAuthError(""); setAuthMode(authMode === "login" ? "register" : "login"); }}>
                  {authMode === "login" ? "Create account" : "Sign in"}
                </button>
              </div>
              <div className="secure-note"><span className="secure-lock">◈</span> Passwords are stored as secure salted hashes</div>
            </div>
          </section>
        </div>
        <footer className="auth-footer"><span>LAN CHAT</span><span>Made for your circle <span className="footer-heart">✦</span></span><span>PRIVATE BY DEFAULT</span></footer>
      </main>
    );
  }

  return (
    <main className={`chat-stage theme-${theme}`}>
      <header className="app-topbar">
        <button className="icon-button mobile-menu-button" onClick={() => setMobileMenuOpen((open) => !open)} aria-label="Open conversations"><Icon name={mobileMenuOpen ? "close" : "menu"} /></button>
        <a className="brand-lockup" href="#chat" aria-label="LAN Chat home"><span className="brand-mark"><Icon name="chat" size={19} /></span><span>LAN<span className="brand-light">Chat</span></span></a>
        <div className="topbar-center"><span className="topbar-rule" /> YOUR SPACE TO CONNECT <span className="topbar-rule" /></div>
        <div className="topbar-actions">
          <span className={`connection-pill ${connected ? "is-connected" : "is-disconnected"}`}><span className="status-dot" />{connected ? "Connected" : "Reconnecting"}</span>
          <button className="icon-button theme-toggle" onClick={toggleTheme} aria-label={`Switch to ${theme === "light" ? "dark" : "light"} mode`}><Icon name={theme === "light" ? "moon" : "sun"} /></button>
          <div className="profile-chip"><span className="avatar avatar-user">{initials(user.username)}</span><span className="profile-name">{user.username}</span></div>
          <button className="icon-button signout-button" onClick={() => void signOut()} aria-label="Sign out"><Icon name="logout" /></button>
        </div>
      </header>

      <div className="workspace-wrap" id="chat">
        {mobileMenuOpen && <button className="mobile-scrim" aria-label="Close menu" onClick={() => setMobileMenuOpen(false)} />}
        <aside className={`conversation-sidebar ${mobileMenuOpen ? "mobile-open" : ""}`}>
          <div className="sidebar-heading"><div><span className="section-label">WORKSPACE</span><h1>Your conversations</h1></div><button className="icon-button small-icon mobile-close" onClick={() => setMobileMenuOpen(false)} aria-label="Close conversations"><Icon name="close" /></button></div>
          <div className="workspace-label"><span className="workspace-symbol">L</span><span><strong>LAN Chat group</strong><small>Community workspace</small></span><span className="workspace-menu">···</span></div>
          <div className="sidebar-section-title"><span>MESSAGES</span><span>{people.length + 1}</span></div>
          <nav className="conversation-list" aria-label="Conversations">
            <button className={`conversation-row ${activeConversation === EVERYONE ? "selected" : ""}`} onClick={() => chooseConversation(EVERYONE)}>
              <span className="conversation-avatar group-avatar"><Icon name="users" size={18} /></span>
              <span className="conversation-copy"><strong>Everyone</strong><small>Group conversation</small></span>
              {unread[EVERYONE] > 0 && <span className="unread-count">{unread[EVERYONE]}</span>}
              {activeConversation === EVERYONE && <span className="selected-rail" />}
            </button>
            {people.map((person, index) => {
              const key = person.username;
              const selected = activeConversation.toLowerCase() === key.toLowerCase();
              return <button key={person.id} className={`conversation-row ${selected ? "selected" : ""}`} onClick={() => chooseConversation(key)}>
                <span className={`conversation-avatar person-avatar avatar-tone-${index % 4}`}>{initials(key)}<span className="presence-mini" /></span>
                <span className="conversation-copy"><strong>{key}</strong><small>Online now</small></span>
                {unread[key] > 0 && <span className="unread-count">{unread[key]}</span>}
                {selected && <span className="selected-rail" />}
              </button>;
            })}
          </nav>
          <div className="sidebar-bottom"><div className="privacy-card"><span className="privacy-spark">✦</span><div><strong>A little breathing room</strong><p>Your private chats stay between you and the person you choose.</p></div></div><div className="sidebar-profile"><span className="avatar avatar-user">{initials(user.username)}</span><span><strong>{user.username}</strong><small>Signed in</small></span><button className="icon-button" onClick={() => void signOut()} aria-label="Sign out"><Icon name="logout" size={17} /></button></div></div>
        </aside>

        <section className="chat-panel" aria-label={`${conversationName} conversation`}>
          <div className="chat-heading">
            <div className="chat-heading-main"><span className={`heading-avatar ${activeConversation === EVERYONE ? "heading-group" : "heading-person"}`}>{activeConversation === EVERYONE ? <Icon name="users" size={20} /> : initials(conversationName)}</span><div><div className="chat-title-row"><h2>{conversationName}</h2>{activeConversation === EVERYONE && <span className="group-tag">GROUP</span>}</div><p>{activeConversation === EVERYONE ? "A shared room for everyone in your group" : activePerson ? "Available to chat" : "Private conversation"}</p></div></div>
            <div className="chat-heading-actions">
              {searchOpen && <input className="search-input" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search messages…" aria-label="Search messages" autoFocus />}
              <button className={`icon-button ${searchOpen ? "active-tool" : ""}`} onClick={() => { setSearchOpen((open) => !open); setSearch(""); }} aria-label={searchOpen ? "Close search" : "Search conversation"}><Icon name="search" /></button>
              <button className={`icon-button people-toggle ${showPeople ? "active-tool" : ""}`} onClick={() => setShowPeople((value) => !value)} aria-label={showPeople ? "Hide online users" : "Show online users"}><Icon name="users" /></button>
            </div>
          </div>
          <div className="message-list" ref={listRef}>
            {conversationMessages.length === 0 ? <div className="empty-chat"><div className="empty-chat-icon"><Icon name="chat" size={25} /></div><h3>{search ? "No matching messages" : "A fresh start"}</h3><p>{search ? "Try another word or clear your search." : activeConversation === EVERYONE ? "Say hello to everyone and get the conversation going." : `Send ${conversationName} a private message.`}</p></div> : <>
              <div className="history-label"><span />{messages.length >= 100 ? "Recent messages" : "Conversation begins"}<span /></div>
              {conversationMessages.map((message, index) => {
                const mine = message.sender.toLowerCase() === user.username.toLowerCase();
                const previous = conversationMessages[index - 1];
                const showSender = !previous || previous.sender !== message.sender || new Date(message.sentAt).getTime() - new Date(previous.sentAt).getTime() > 5 * 60 * 1000;
                const dayChanged = !previous || new Date(previous.sentAt).toDateString() !== new Date(message.sentAt).toDateString();
                return <div key={message.id}>
                  {dayChanged && <div className="day-divider"><span>{displayDay(message.sentAt)}</span></div>}
                  <article className={`message-row ${mine ? "mine" : ""} ${showSender ? "new-sender" : "grouped-message"}`}>
                    <span className={`message-avatar ${mine ? "avatar-user" : `avatar-tone-${message.sender.charCodeAt(0) % 4}`}`}>{initials(message.sender)}</span>
                    <div className="message-body">
                      {showSender && <div className="message-meta"><strong>{mine ? "You" : message.sender}</strong>{message.recipient && <span className="private-mark">Private</span>}<time dateTime={message.sentAt}>{displayTime(message.sentAt)}</time></div>}
                      <div className={`message-bubble ${mine ? "own-bubble" : "other-bubble"}`}>{message.content}</div>
                    </div>
                  </article>
                </div>;
              })}
              <div ref={messagesEndRef} />
            </>}
          </div>
          <div className="composer-wrap">
            {sendError && <div className="send-error" role="alert">{sendError}</div>}
            <div className="composer-box"><textarea value={draft} onChange={(event) => setDraft(event.target.value)} onKeyDown={onComposerKeyDown} placeholder={`Message ${activeConversation === EVERYONE ? "everyone" : conversationName}…`} maxLength={2000} rows={1} aria-label="Write a message" /><button className="send-button" onClick={() => void sendMessage()} disabled={!draft.trim() || sending} aria-label="Send message"><Icon name="send" size={18} /></button></div>
            <div className="composer-hint"><span><kbd>Enter</kbd> to send <span className="hint-separator">·</span> <kbd>Shift</kbd> + <kbd>Enter</kbd> for a new line</span><span>{draft.length}/2000</span></div>
          </div>
        </section>

        <aside className={`people-panel ${showPeople ? "people-visible" : ""}`}>
          <div className="people-heading"><span className="section-label">YOUR CIRCLE</span><h2>Online now <span>{people.length + 1}</span></h2></div>
          <div className="people-list">
            <div className="people-group-label">ACTIVE</div>
            <div className="person-row"><span className="avatar avatar-user">{initials(user.username)}<span className="presence-dot" /></span><span><strong>{user.username}</strong><small>You</small></span></div>
            {people.map((person, index) => <div className="person-row" key={person.id}><span className={`avatar avatar-tone-${index % 4}`}>{initials(person.username)}<span className="presence-dot" /></span><span><strong>{person.username}</strong><small>Online</small></span><button className="person-chat" onClick={() => chooseConversation(person.username)} aria-label={`Message ${person.username}`}><Icon name="chat" size={16} /></button></div>)}
            {people.length === 0 && <p className="no-people">You’re the first one here.<br />Others will show up when they join.</p>}
          </div>
          <div className="people-footer"><span className="status-dot" /> Updates every few seconds</div>
        </aside>
      </div>
      <footer className="app-footer"><span>LAN CHAT <i>·</i> YOUR SPACE TO CONNECT</span><span>Keep it kind. Keep it close. <b>✦</b></span></footer>
    </main>
  );
}
