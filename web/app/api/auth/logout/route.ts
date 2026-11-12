import { errorResponse, jsonResponse } from "@/lib/api";
import { clearSessionCookie, isSameOrigin } from "@/lib/session";

export const runtime = "nodejs";

export async function POST(request: Request) {
  if (!isSameOrigin(request)) return errorResponse("Request origin was rejected.", 403);
  await clearSessionCookie();
  return jsonResponse({ ok: true });
}
