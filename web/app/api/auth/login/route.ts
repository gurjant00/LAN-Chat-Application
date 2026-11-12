import { getSql } from "@/lib/db";
import { errorResponse, jsonResponse, readJson } from "@/lib/api";
import { verifyPassword } from "@/lib/password";
import { isSameOrigin, setSessionCookie } from "@/lib/session";

export const runtime = "nodejs";

export async function POST(request: Request) {
  if (!isSameOrigin(request)) return errorResponse("Request origin was rejected.", 403);
  const body = await readJson(request);
  const username = typeof body?.username === "string" ? body.username.trim() : "";
  const password = typeof body?.password === "string" ? body.password : "";
  if (!username || !password || password.length > 128) return errorResponse("Incorrect username or password.", 401);

  try {
    const sql = getSql();
    const rows = await sql`
      SELECT id::text AS id, username, password_salt, password_hash
      FROM users WHERE LOWER(username) = LOWER(${username}) LIMIT 1
    `;
    const account = rows[0] as { id: string; username: string; password_salt: string; password_hash: string } | undefined;
    if (!account || !(await verifyPassword(password, account.password_salt, account.password_hash))) {
      return errorResponse("Incorrect username or password.", 401);
    }
    await setSessionCookie({ id: account.id, username: account.username });
    await sql`
      INSERT INTO presence (user_id, last_seen) VALUES (${account.id}, NOW())
      ON CONFLICT (user_id) DO UPDATE SET last_seen = NOW()
    `;
    return jsonResponse({ user: { id: account.id, username: account.username } });
  } catch (error) {
    console.error("Login failed", error);
    return errorResponse("Account service is not configured. Check DATABASE_URL and SESSION_SECRET.", 503);
  }
}
