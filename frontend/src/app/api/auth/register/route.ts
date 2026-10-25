import { connection } from "next/server";
import { proxyToBackend } from "@/lib/backend";
import { signInResponse } from "@/lib/session";

export async function POST(req: Request) {
  await connection();
  return signInResponse(await proxyToBackend(req, "/auth/register"));
}
