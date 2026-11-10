/**
 * Name of the browser event the bell fires after the creditor answered a payment. The Settle up tab of the same group
 * listens and looks at the ledger again, because the answer changes the plan once the ledger has booked it.
 */
export const PAYMENT_ANSWERED = "sharewise:payment-answered";

export function announcePaymentAnswered() {
  window.dispatchEvent(new Event(PAYMENT_ANSWERED));
}
