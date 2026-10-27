import type { Metadata } from "next";
import Link from "next/link";
import { Geist, Geist_Mono } from "next/font/google";
import { AuthBar } from "@/components/AuthBar";
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
        <CurrentUserProvider>
          <header className="flex items-center justify-between border-b px-4 py-3">
            <Link href="/" className="font-semibold">
              ShareWise
            </Link>
            <AuthBar />
          </header>
          <div className="flex-1 px-4 py-6">{children}</div>
        </CurrentUserProvider>
      </body>
    </html>
  );
}
