import { errorResponse, jsonResponse } from "@/lib/api";
import { getSql } from "@/lib/db";
import { getSession, isSameOrigin } from "@/lib/session";

export const runtime = "nodejs";

export async function GET() {
  const user = await getSession();
  if (!user) return errorResponse("Sign in to view online users.", 401);
  try {
    const sql = getSql();
    const users = await sql`
      SELECT u.id::text AS id, u.username, p.last_seen
      FROM presence p JOIN users u ON u.id = p.user_id
      WHERE p.last_seen > NOW() - INTERVAL '45 seconds'
      ORDER BY LOWER(u.username)
    `;
    return jsonResponse({ users: users.map((row) => ({
      id: String(row.id), username: String(row.username), lastSeen: new Date(row.last_seen as Date | string).toISOString(),
    })) });
  } catch (error) {
    console.error("Presence request failed", error);
    return errorResponse("Could not load online users.", 503);
  }
}

export async function POST(request: Request) {
  if (!isSameOrigin(request)) return errorResponse("Request origin was rejected.", 403);
  const user = await getSession();
  if (!user) return errorResponse("Sign in to update presence.", 401);
  try {
    const sql = getSql();
    await sql`
      INSERT INTO presence (user_id, last_seen) VALUES (${user.id}, NOW())
      ON CONFLICT (user_id) DO UPDATE SET last_seen = NOW()
    `;
    return jsonResponse({ ok: true });
  } catch (error) {
    console.error("Presence update failed", error);
    return errorResponse("Could not update online status.", 503);
  }
}
