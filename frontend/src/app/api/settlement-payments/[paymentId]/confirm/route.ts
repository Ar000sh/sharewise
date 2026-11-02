import { connection } from "next/server";
import { proxyToBackend } from "@/lib/backend";

type Ctx = { params: Promise<{ paymentId: string }> };

export async function POST(req: Request, { params }: Ctx) {
  await connection();
  const { paymentId } = await params;
  return proxyToBackend(req, `/settlement-payments/${encodeURIComponent(paymentId)}/confirm`);
}
