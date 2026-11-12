import { errorResponse, jsonResponse, readJson } from "@/lib/api";
import { getSql } from "@/lib/db";
import { getSession, isSameOrigin } from "@/lib/session";

export const runtime = "nodejs";

type DbMessage = {
  id: string;
  sender: string;
  recipient: string | null;
  content: string;
  created_at: Date | string;
};

function toMessage(row: DbMessage) {
  return {
    id: row.id,
    sender: row.sender,
    recipient: row.recipient,
    content: row.content,
    sentAt: new Date(row.created_at).toISOString(),
  };
}

export async function GET(request: Request) {
  const user = await getSession();
  if (!user) return errorResponse("Sign in to view messages.", 401);
  try {
    const sql = getSql();
    const after = new URL(request.url).searchParams.get("after");
    let rows: DbMessage[];
    if (after && /^\d+$/.test(after)) {
      rows = await sql<DbMessage>`
        SELECT m.id::text AS id, sender.username AS sender, recipient.username AS recipient,
               m.content, m.created_at
        FROM messages m
        JOIN users sender ON sender.id = m.sender_id
        LEFT JOIN users recipient ON recipient.id = m.recipient_id
        WHERE (m.recipient_id IS NULL OR m.sender_id = ${user.id} OR m.recipient_id = ${user.id})
          AND m.id > ${after}::bigint
        ORDER BY m.id ASC
        LIMIT 100
      `;
    } else {
      rows = await sql<DbMessage>`
        SELECT m.id::text AS id, sender.username AS sender, recipient.username AS recipient,
               m.content, m.created_at
        FROM messages m
        JOIN users sender ON sender.id = m.sender_id
        LEFT JOIN users recipient ON recipient.id = m.recipient_id
        WHERE m.recipient_id IS NULL OR m.sender_id = ${user.id} OR m.recipient_id = ${user.id}
        ORDER BY m.id DESC
        LIMIT 100
      `;
      rows.reverse();
    }
    return jsonResponse({ messages: rows.map(toMessage) });
  } catch (error) {
    console.error("Message history request failed", error);
    return errorResponse("Could not load chat history.", 503);
  }
}

export async function POST(request: Request) {
  if (!isSameOrigin(request)) return errorResponse("Request origin was rejected.", 403);
  const user = await getSession();
  if (!user) return errorResponse("Sign in to send a message.", 401);
  const body = await readJson(request);
  const content = typeof body?.content === "string" ? body.content.trim() : "";
  const recipientName = typeof body?.recipient === "string" ? body.recipient.trim() : "";
  if (!content) return errorResponse("Write a message first.");
  if (content.length > 2000) return errorResponse("Messages can contain up to 2000 characters.");

  try {
    const sql = getSql();
    let recipientId: string | null = null;
    let recipient: string | null = null;
    if (recipientName) {
      const targetRows = await sql`SELECT id::text AS id, username FROM users WHERE LOWER(username) = LOWER(${recipientName}) LIMIT 1`;
      const target = targetRows[0] as { id: string; username: string } | undefined;
      if (!target) return errorResponse("That user could not be found.", 404);
      recipientId = target.id;
      recipient = target.username;
    }
    const rows = await sql`
      INSERT INTO messages (sender_id, recipient_id, content)
      VALUES (${user.id}, ${recipientId}, ${content})
      RETURNING id::text AS id, created_at
    `;
    const row = rows[0] as { id: string; created_at: Date | string } | undefined;
    if (!row) return errorResponse("Message could not be sent.", 500);
    return jsonResponse({
      message: {
        id: row.id,
        sender: user.username,
        recipient,
        content,
        sentAt: new Date(row.created_at).toISOString(),
      },
    }, 201);
  } catch (error) {
    console.error("Message send failed", error);
    return errorResponse("Message could not be sent.", 503);
  }
}
