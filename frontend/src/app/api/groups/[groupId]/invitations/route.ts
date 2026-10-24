import { connection } from "next/server";
import { proxyToBackend } from "@/lib/backend";

type Ctx = { params: Promise<{ groupId: string }> };

export async function POST(req: Request, { params }: Ctx) {
  await connection();
  const { groupId } = await params;
  return proxyToBackend(req, `/groups/${encodeURIComponent(groupId)}/invitations`);
}
