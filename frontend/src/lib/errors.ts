import { ApiError } from "@/lib/api";

const MESSAGES: Record<string, string> = {
  UNAUTHENTICATED: "You are not signed in. Please sign in.",
  NOT_A_MEMBER: "You are not a member of this group.",
  GROUP_NOT_FOUND: "This group does not exist.",
  INVITATION_NOT_FOUND: "This invitation is no longer valid.",
  ALREADY_MEMBER: "You are already a member of this group.",
  INVITATION_EXPIRED: "This invitation has expired. Ask for a new one.",
  ACCOUNT_NOT_FOUND: "No account found. The person must have signed in to ShareWise once.",
  ALREADY_INVITED: "This person already has an open invitation.",
  NOTIFICATION_NOT_FOUND: "This entry no longer exists.",
  AMOUNT_NOT_POSITIVE: "The amount must be greater than zero.",
  PAYER_NOT_IN_GROUP: "The payer is not a member of this group.",
  PARTICIPANT_NOT_IN_GROUP: "A participant is not a member of this group.",
  NO_PARTICIPANTS: "Select at least one participant.",
  GROUP_NOT_OPEN: "This group is no longer open for new expenses.",
  SPLIT_NOT_SUPPORTED: "Only equal splits are supported for now.",
  BACKEND_UNREACHABLE: "The server is not reachable right now. Please try again later.",
  NETWORK_ERROR: "Could not reach the server. Check your connection and try again.",
  INVALID_RESPONSE: "The server returned an unexpected response. Refresh the page to check whether your action completed.",
};

/**
 * A readable message for an error: a plain text (a validation message of a form) as it is, else the known problem
 * code, then the server's detail.
 */
export function messageFor(error: unknown): string {
  if (typeof error === "string") return error;
  if (!(error instanceof ApiError)) return "Something went wrong.";
  const message = MESSAGES[error.code] ?? error.detail;
  if (message) return message;
  if (error.status === 401) return MESSAGES.UNAUTHENTICATED;
  if (error.status === 403) return "This request could not be verified. Refresh the page and try again.";
  if (error.status === 404) return "The requested service could not be found. Please check that the ShareWise backend is running at the configured address.";
  if (error.status >= 500) return "The server could not complete this request. Please try again in a moment.";
  return "The request could not be completed. Please check your entries and try again.";
}
