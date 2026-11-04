// @vitest-environment node
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
import { describe, expect, it } from "vitest";

/**
 * Dark mode rules of docs/conventions/04 section 5 ("Colours and dark mode"), checked on the source of every component and
 * page. The page sets a light text colour in dark mode, so a hard-coded light background without its own text colour (or a
 * hard-coded dark text colour on the page background) becomes unreadable. The rules look at each string literal that holds
 * class names:
 *   1. no `bg-white`, `bg-black`, `border-white`, `border-black`, `text-black` and no grey from the fixed palette; use the
 *      theme tokens (`bg-background`, `text-foreground`, `bg-foreground`, `text-background`, `text-foreground/70`);
 *   2. a background from the fixed palette (`bg-red-600`) comes with its own text colour in the same string;
 *   3. `text-white` only on such a fixed-palette background;
 *   4. a fixed-palette text colour in a shade 500 to 900 (`text-red-700`) comes with a `dark:text-...` counterpart, unless it
 *      sits on a fixed-palette background of its own.
 */
const PALETTE = "red|green|blue|yellow|orange|amber|purple|pink|slate|gray|zinc|neutral|stone";
const FIXED_BACKGROUND = new RegExp(`^bg-(${PALETTE})-\\d{2,3}$`);
const FORBIDDEN = new RegExp(
  `^(bg-(white|black)|border-(white|black)|text-black|(bg|text|border)-(slate|gray|zinc|neutral|stone)-\\d{2,3})$`,
);
const TEXT_COLOUR = new RegExp(`^text-(white|black|foreground|background|(${PALETTE})-\\d{2,3})(/\\d+)?$`);
const DARK_SENSITIVE_TEXT = new RegExp(`^text-(red|green|blue|yellow|orange|amber|purple|pink)-[5-9]00$`);

/** The contents of every "double quoted" and `template` string literal: where class names live. */
function stringLiterals(source: string): string[] {
  return [...source.matchAll(/"([^"\n]*)"|`([^`]*)`/g)].map((match) => match[1] ?? match[2] ?? "");
}

const baseOf = (token: string) => token.slice(token.lastIndexOf(":") + 1);

export function violations(source: string): string[] {
  const problems: string[] = [];
  for (const literal of stringLiterals(source)) {
    const tokens = literal.split(/\s+/).filter(Boolean);
    const bases = tokens.map(baseOf);
    const hasFixedBackground = bases.some((token) => FIXED_BACKGROUND.test(token));
    for (const token of bases.filter((t) => FORBIDDEN.test(t))) {
      problems.push(`${token}: use a theme token (bg-background, text-foreground, bg-foreground, text-foreground/70) in "${literal}"`);
    }
    if (bases.includes("text-white") && !hasFixedBackground) {
      problems.push(`text-white without a fixed-palette background; use text-background on bg-foreground in "${literal}"`);
    }
    if (hasFixedBackground && !bases.some((t) => TEXT_COLOUR.test(t))) {
      problems.push(`a fixed-palette background needs its own text colour in "${literal}"`);
    }
    const sensitive = bases.filter((t) => DARK_SENSITIVE_TEXT.test(t)).length;
    const darkVariants = tokens.filter((t) => t.startsWith("dark:text-")).length;
    if (!hasFixedBackground && sensitive > darkVariants) {
      problems.push(`a coloured text needs a dark: counterpart (text-red-700 dark:text-red-400) in "${literal}"`);
    }
  }
  return problems;
}

function sourceFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) return sourceFiles(path);
    return entry.name.endsWith(".tsx") && !entry.name.endsWith(".test.tsx") ? [path] : [];
  });
}

describe("theme rules (dark mode)", () => {
  it.each([
    ['className="rounded border bg-white p-3 text-sm shadow"', "bg-white"],
    ['className="rounded bg-black px-3 text-white"', "bg-black"],
    ['className="rounded bg-gray-100 px-2 text-xs"', "bg-gray-100"],
    ['className="text-sm text-gray-600"', "text-gray-600"],
    ['className="border-b-2 border-black"', "border-black"],
    ['className="text-red-700"', "dark: counterpart"],
    ['className="rounded-full bg-red-600 px-1.5 text-xs"', "own text colour"],
    ['className="px-2 text-white"', "text-white without"],
  ])("flagsTheClassString%#", (source, expectedPart) => {
    expect(violations(source).join("\n")).toContain(expectedPart);
  });

  it.each([
    'className="rounded border bg-background p-3 text-sm text-foreground shadow"',
    'className="rounded bg-foreground px-2 py-1 text-background disabled:opacity-50"',
    'className="rounded-full bg-red-600 px-1.5 text-xs text-white"',
    'className="text-red-700 dark:text-red-400"',
    'className="rounded border border-red-300 bg-red-50 px-3 py-2 text-red-800"',
    'className="text-sm text-foreground/70"',
    'className={`px-3 ${tab ? "border-b-2 border-foreground font-medium" : "text-foreground/70"}`}',
    'className={`font-medium ${a ? "text-green-700 dark:text-green-400" : b ? "text-red-700 dark:text-red-400" : ""}`}',
    'import { api } from "@/lib/api";',
  ])("acceptsTheClassString%#", (source) => {
    expect(violations(source)).toEqual([]);
  });

  it("everyComponentAndPageFollowsTheThemeRules", () => {
    const root = fileURLToPath(new URL("..", import.meta.url));
    const found = [join(root, "app"), join(root, "components")]
      .flatMap(sourceFiles)
      .flatMap((file) => violations(readFileSync(file, "utf8")).map((problem) => `${file.replace(root, "src/")}: ${problem}`));

    expect(found).toEqual([]);
  });
});
