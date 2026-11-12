import { jsonResponse } from "@/lib/api";
import { getSession } from "@/lib/session";

export const runtime = "nodejs";

export async function GET() {
  return jsonResponse({ user: await getSession() });
}
