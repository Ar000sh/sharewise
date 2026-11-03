import { connection } from "next/server";
import { proxyToBackend } from "@/lib/backend";

type Ctx = { params: Promise<{ invitationId: string }> };

export async function POST(req: Request, { params }: Ctx) {
  await connection();
  const { invitationId } = await params;
  return proxyToBackend(req, `/invitations/${encodeURIComponent(invitationId)}/decline`);
}
