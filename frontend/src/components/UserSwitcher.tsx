"use client";

import { useEffect, useState } from "react";
import type { components } from "@/api/schema";
import { api } from "@/lib/api";
import { USER_COOKIE } from "@/lib/user-cookie";

type User = components["schemas"]["User"];

function readUserCookie(): string | undefined {
  const match = document.cookie.split("; ").find((c) => c.startsWith(`${USER_COOKIE}=`));
  return match ? decodeURIComponent(match.split("=")[1]) : undefined;
}

function writeUserCookie(userId: string) {
  document.cookie = `${USER_COOKIE}=${encodeURIComponent(userId)}; path=/; SameSite=Lax`;
}

/** Dev-only stand-in for login: pick who you are. Replaced when Keycloak arrives. */
export function UserSwitcher() {
  const [users, setUsers] = useState<User[]>([]);
  const [selected, setSelected] = useState("");
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    api<User[]>("/users")
      .then((list) => {
        const saved = readUserCookie();
        const initial = list.find((u) => u.userId === saved)?.userId ?? list[0]?.userId ?? "";
        if (initial && initial !== saved) writeUserCookie(initial);
        setUsers(list);
        setSelected(initial);
      })
      .catch(() => setFailed(true));
  }, []);

  if (failed) return <span className="text-sm text-red-600">Users unavailable</span>;

  return (
    <label className="flex items-center gap-2 text-sm">
      Signed in as
      <select
        className="rounded border px-2 py-1"
        value={selected}
        onChange={(e) => {
          writeUserCookie(e.target.value);
          window.location.reload();
        }}
      >
        {users.map((u) => (
          <option key={u.userId} value={u.userId}>
            {u.displayName}
          </option>
        ))}
      </select>
    </label>
  );
}
