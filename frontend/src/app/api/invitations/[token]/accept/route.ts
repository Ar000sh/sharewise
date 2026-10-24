import { connection } from "next/server";
import { proxyToBackend } from "@/lib/backend";

type Ctx = { params: Promise<{ token: string }> };

export async function POST(req: Request, { params }: Ctx) {
  await connection();
  const { token } = await params;
  return proxyToBackend(req, `/invitations/${encodeURIComponent(token)}/accept`);
}
