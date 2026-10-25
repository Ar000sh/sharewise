"use client";

import Link from "next/link";
import { useState, useSyncExternalStore } from "react";
import { api } from "@/lib/api";
import { messageFor } from "@/lib/errors";

type Mode = "login" | "register";

/** Only same-site paths: "/join/abc" yes, "//evil.example" and "https://..." no. */
export function safeNext(next: string | null): string {
  return next && next.startsWith("/") && !next.startsWith("//") ? next : "/";
}

export function AuthForm({ mode }: { mode: Mode }) {
  const [email, setEmail] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);
  // "" on the server and while hydrating, then the real query string, so the first render matches the server's.
  const next = useSyncExternalStore(
    () => () => {},
    () => window.location.search,
    () => "",
  );
  const register = mode === "register";

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(undefined);
    try {
      await api(register ? "/auth/register" : "/auth/login", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(register ? { email, displayName, password } : { email, password }),
      });
      // A full load, so every page request carries the new session cookie.
      window.location.assign(safeNext(new URLSearchParams(window.location.search).get("next")));
    } catch (err) {
      setError(messageFor(err));
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="mx-auto flex max-w-sm flex-col gap-4">
      <h1 className="text-xl font-semibold">{register ? "Create your account" : "Sign in"}</h1>
      {error && (
        <p role="alert" className="rounded border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-800">
          {error}
        </p>
      )}
      {register && (
        <label className="flex flex-col gap-1 text-sm">
          Name
          <input
            className="rounded border px-3 py-2"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            autoComplete="name"
            required
            maxLength={100}
          />
        </label>
      )}
      <label className="flex flex-col gap-1 text-sm">
        Email
        <input
          className="rounded border px-3 py-2"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          autoComplete="email"
          required
        />
      </label>
      <label className="flex flex-col gap-1 text-sm">
        Password
        <input
          className="rounded border px-3 py-2"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete={register ? "new-password" : "current-password"}
          required
          minLength={register ? 8 : undefined}
          maxLength={72}
        />
      </label>
      {register && <p className="-mt-2 text-xs text-gray-500">At least 8 characters.</p>}
      <button type="submit" disabled={busy} className="rounded bg-black px-3 py-2 text-white disabled:opacity-50">
        {register ? "Sign up" : "Sign in"}
      </button>
      <p className="text-sm">
        {register ? (
          <>
            Already have an account? <Link className="underline" href={`/login${next}`}>Sign in</Link>
          </>
        ) : (
          <>
            New here? <Link className="underline" href={`/register${next}`}>Create an account</Link>
          </>
        )}
      </p>
    </form>
  );
}
