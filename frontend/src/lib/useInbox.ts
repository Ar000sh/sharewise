"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback } from "react";
import type { components } from "@/api/schema";
import { ApiError, api } from "@/lib/api";
import { queryKeyOf } from "@/lib/useApi";
import { markGroupChanged } from "@/lib/useGroupChange";

type PendingInvitation = components["schemas"]["PendingInvitation"];
type NotificationList = components["schemas"]["NotificationList"];
type SettlementPayment = components["schemas"]["SettlementPayment"];

/** What `/api/inbox` returns: three parts, each null when its source failed. */
export type Inbox = {
  invitations: PendingInvitation[] | null;
  notifications: NotificationList | null;
  payments: SettlementPayment[] | null;
};

/** How often the inbox asks again. It also asks when the window gets focus (TanStack default). */
export const INBOX_POLL_MS = 30_000;

const INBOX_KEY = queryKeyOf("/inbox");

/**
 * The inbox as one query (design 2026-11-23, F3): the inbox page, the top-bar count, the payments waiting in a group's
 * Settle up tab and every waiting count read the same cached answer, so an answer given in one place disappears from
 * all of them at once.
 */
export function useInbox() {
  const { data, error } = useQuery({
    queryKey: INBOX_KEY,
    queryFn: () => api<Inbox>("/inbox"),
    refetchInterval: INBOX_POLL_MS,
  });
  return { inbox: data, error: error ?? undefined };
}

/** Asks the inbox again, after anything that changes it (an answer, an invitation accepted, an entry read). */
export function useReloadInbox(): () => Promise<void> {
  const client = useQueryClient();
  return useCallback(() => client.invalidateQueries({ queryKey: INBOX_KEY }), [client]);
}

/**
 * The one way to answer a payment that waits for the creditor (R16), wherever it is shown. After the answer the
 * payment's group is marked changed (its open tabs look again while the ledger books it) and the inbox is asked again.
 * A 404, or a payment that is no longer pending, means it was already answered (a second tab, a double click): that
 * counts as done. Any other failure marks nothing and throws for the caller to show.
 */
export function useAnswerPayment() {
  const client = useQueryClient();
  const reloadInbox = useReloadInbox();

  const answer = useCallback(
    async (payment: SettlementPayment, send: () => Promise<unknown>) => {
      try {
        await send();
      } catch (e) {
        const alreadyAnswered = e instanceof ApiError && (e.status === 404 || e.code === "PAYMENT_NOT_PENDING");
        if (!alreadyAnswered) throw e;
        await reloadInbox();
        return;
      }
      markGroupChanged(client, payment.groupId);
      await reloadInbox();
    },
    [client, reloadInbox],
  );

  const confirm = useCallback(
    (payment: SettlementPayment) =>
      answer(payment, () => api(`/settlement-payments/${payment.id}/confirm`, { method: "POST" })),
    [answer],
  );

  const reject = useCallback(
    (payment: SettlementPayment, note: string) =>
      answer(payment, () =>
        api(`/settlement-payments/${payment.id}/reject`, {
          method: "POST",
          headers: { "content-type": "application/json" },
          body: JSON.stringify({ note }),
        }),
      ),
    [answer],
  );

  return { confirm, reject };
}
