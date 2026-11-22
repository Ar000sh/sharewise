import { connection } from "next/server";
import { proxyToBackend } from "@/lib/backend";
type Ctx = { params: Promise<{ groupId: string; expenseId: string }> };
export async function POST(req: Request, { params }: Ctx) {
  await connection();
  const { groupId, expenseId } = await params;
  return proxyToBackend(req, `/groups/${encodeURIComponent(groupId)}/expenses/${encodeURIComponent(expenseId)}/correct`);
}
