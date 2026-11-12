import { getSql } from "@/lib/db";
import { errorResponse, jsonResponse, readJson } from "@/lib/api";
import { hashPassword } from "@/lib/password";
import { isSameOrigin, setSessionCookie } from "@/lib/session";

export const runtime = "nodejs";

export async function POST(request: Request) {
  if (!isSameOrigin(request)) return errorResponse("Request origin was rejected.", 403);
  const body = await readJson(request);
  const username = typeof body?.username === "string" ? body.username.trim() : "";
  const password = typeof body?.password === "string" ? body.password : "";

  if (!/^[A-Za-z0-9_]{3,20}$/.test(username)) {
    return errorResponse("Username must be 3–20 letters, numbers, or underscores.");
  }
  if (password.length < 8 || password.length > 128) {
    return errorResponse("Password must be 8–128 characters.");
  }

  try {
    const { salt, hash } = await hashPassword(password);
    const sql = getSql();
    const rows = await sql`
      INSERT INTO users (username, password_salt, password_hash)
      VALUES (${username}, ${salt}, ${hash})
      RETURNING id::text AS id, username
    `;
    const user = rows[0] as { id: string; username: string } | undefined;
    if (!user) return errorResponse("Account could not be created.", 500);
    await setSessionCookie(user);
    return jsonResponse({ user });
  } catch (error) {
    const message = error instanceof Error ? error.message : "";
    if (message.includes("users_username_ci_idx") || message.includes("duplicate key")) {
      return errorResponse("That username is already registered.", 409);
    }
    console.error("Registration failed", error);
    return errorResponse("Account service is not configured. Check DATABASE_URL and SESSION_SECRET.", 503);
  }
}
