/** Cookie that holds the stub identity chosen in the dev user switcher (read by the proxy). Dev profile only. */
export const USER_COOKIE = "sw_user_id";

/** httpOnly cookie that holds the session token from /auth/login. The browser's scripts never read it. */
export const SESSION_COOKIE = "sw_session";
