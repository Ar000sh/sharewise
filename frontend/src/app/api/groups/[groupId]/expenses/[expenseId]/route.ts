import { connection } from "next/server";
import { proxyToBackend } from "@/lib/backend";

type Ctx = { params: Promise<{ groupId: string; expenseId: string }> };

function path(groupId: string, expenseId: string): string {
  return `/groups/${encodeURIComponent(groupId)}/expenses/${encodeURIComponent(expenseId)}`;
}

export async function PUT(req: Request, { params }: Ctx) {
  await connection();
  const { groupId, expenseId } = await params;
  return proxyToBackend(req, path(groupId, expenseId));
}

export async function DELETE(req: Request, { params }: Ctx) {
  await connection();
  const { groupId, expenseId } = await params;
  return proxyToBackend(req, path(groupId, expenseId));
}
