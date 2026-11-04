import { connection } from "next/server";
import { proxyToBackend } from "@/lib/backend";

type Ctx = { params: Promise<{ notificationId: string }> };

export async function POST(req: Request, { params }: Ctx) {
  await connection();
  const { notificationId } = await params;
  return proxyToBackend(req, `/notifications/${encodeURIComponent(notificationId)}/read`);
}
