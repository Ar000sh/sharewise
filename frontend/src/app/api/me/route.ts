import { connection } from "next/server";
import { proxyToBackend } from "@/lib/backend";

export async function GET(req: Request) {
  await connection();
  return proxyToBackend(req, "/me");
}

export async function PATCH(req: Request) {
  await connection();
  return proxyToBackend(req, "/me");
}
