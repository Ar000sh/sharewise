import type { Metadata } from "next";
import Link from "next/link";
import { Bricolage_Grotesque, DM_Sans } from "next/font/google";
import { AuthBar } from "@/components/AuthBar";
import { QueryProvider } from "@/components/QueryProvider";
import { CurrentUserProvider } from "@/lib/currentUser";
import "./globals.css";

// The two fonts of the Journey design (design 2026-11-23, U9): headings and big amounts, and everything else.
// next/font downloads them at build time and serves them from this app, so the browser never asks Google.
const display = Bricolage_Grotesque({
  variable: "--font-bricolage",
  subsets: ["latin"],
});

const text = DM_Sans({
  variable: "--font-dm-sans",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "ShareWise",
  description: "Shared expenses made simple",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="en" className={`${display.variable} ${text.variable} h-full antialiased`}>
      <body className="min-h-full flex flex-col">
        <QueryProvider>
          <CurrentUserProvider>
            <header className="flex min-h-17 flex-wrap items-center gap-4 border-b px-4 py-3 sm:px-10">
              <Link href="/" className="flex items-center gap-2 font-display text-2xl font-extrabold tracking-tight">
                <span aria-hidden="true" className="size-6 rounded-full bg-accent" />
                ShareWise
              </Link>
              <AuthBar />
            </header>
            <div className="flex-1 px-4 py-8 sm:px-6">{children}</div>
          </CurrentUserProvider>
        </QueryProvider>
      </body>
    </html>
  );
}
