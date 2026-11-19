import type { Metadata } from "next";
import Link from "next/link";
import { Geist, Geist_Mono } from "next/font/google";
import { AuthBar } from "@/components/AuthBar";
import { Icon } from "@/components/Icon";
import { QueryProvider } from "@/components/QueryProvider";
import { CurrentUserProvider } from "@/lib/currentUser";
import "./globals.css";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "ShareWise",
  description: "Shared expenses made simple",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="en"
      className={`${geistSans.variable} ${geistMono.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col">
        <QueryProvider>
          <CurrentUserProvider>
            <a
              href="#main-content"
              className="sr-only focus:not-sr-only focus:absolute focus:z-50 focus:rounded-lg focus:bg-surface focus:p-3 focus:text-foreground"
            >
              Skip to content
            </a>
            <header className="border-b border-line bg-surface text-foreground">
              <div className="mx-auto flex min-h-20 max-w-6xl flex-wrap items-center justify-between gap-3 px-5 py-4 sm:px-8">
                <Link
                  href="/"
                  className="flex items-center gap-2.5 text-xl font-semibold tracking-tight"
                  aria-label="ShareWise home"
                >
                  <span className="flex size-9 items-center justify-center rounded-xl bg-accent text-accent-foreground">
                    <Icon name="split" />
                  </span>
                  ShareWise
                </Link>
                <AuthBar />
              </div>
            </header>
            <div className="flex-1">{children}</div>
            <footer className="mx-auto flex w-full max-w-6xl flex-wrap justify-between gap-2 px-5 py-6 text-xs text-muted sm:px-8">
              <span>ShareWise</span>
              <span>Shared expenses, clearly organized.</span>
            </footer>
          </CurrentUserProvider>
        </QueryProvider>
      </body>
    </html>
  );
}
