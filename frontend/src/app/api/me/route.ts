import { connection } from "next/server";
import { proxyToBackend } from "@/lib/backend";

export async function GET(req: Request) {
  await connection();
  return proxyToBackend(req, "/me");
}
