import { ApiError } from "@/lib/api";

const MESSAGES: Record<string, string> = {
  UNAUTHENTICATED: "You are not signed in. Please sign in.",
  INVALID_CREDENTIALS: "Wrong email or password.",
  EMAIL_TAKEN: "This email is already registered. Try signing in.",
  NOT_A_MEMBER: "You are not a member of this group.",
  GROUP_NOT_FOUND: "This group does not exist.",
  INVITATION_NOT_FOUND: "This invitation link is not valid.",
  ALREADY_MEMBER: "You are already a member of this group.",
  INVITATION_EXPIRED: "This invitation has expired. Ask for a new link.",
  AMOUNT_NOT_POSITIVE: "The amount must be greater than zero.",
  PAYER_NOT_IN_GROUP: "The payer is not a member of this group.",
  PARTICIPANT_NOT_IN_GROUP: "A participant is not a member of this group.",
  NO_PARTICIPANTS: "Select at least one participant.",
  GROUP_NOT_OPEN: "This group is no longer open for new expenses.",
  SPLIT_NOT_SUPPORTED: "Only equal splits are supported for now.",
  BACKEND_UNREACHABLE: "The server is not reachable right now. Please try again later.",
  NETWORK_ERROR: "Could not reach the server. Check your connection and try again.",
};

/** A readable message for an error: known problem code first, then the server's detail. */
export function messageFor(error: unknown): string {
  if (!(error instanceof ApiError)) return "Something went wrong.";
  return MESSAGES[error.code] ?? error.detail ?? "Something went wrong.";
}
