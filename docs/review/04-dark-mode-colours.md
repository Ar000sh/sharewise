# 4. Readable colours in dark mode

In dark mode the page sets a light text colour and everything inherits it. The inbox dropdown had `bg-white` and no text colour of its own, so it showed pale text on white. The same pattern was in 13 files, now fixed with the theme tokens (`bg-background text-foreground`, `bg-foreground text-background`, `text-foreground/70`, `dark:` variants). Commits `f7de5cb` (fix), `55d4895` (rule and check). Mostly one-line class swaps.

**Start here:** `frontend/src/lib/themeRules.test.ts`, then the rule in `docs/conventions/04_FRONTEND.md` section 5.

| Look at | For |
|---|---|
| `themeRules.test.ts` | the three rules (no fixed black/white/grey; a fixed background brings its own text colour; coloured text needs `dark:`) and the samples, including the exact old dropdown string |
| `InboxBell.tsx`, `MemberList.tsx`, `GroupDetail.tsx` | the three places the problem was visible |
| the other 10 files | skim only |

Best check: switch your browser to dark mode and look at the bell dropdown, the Members tab, the buttons and the active tab.

```sh
git show f7de5cb -w --ignore-cr-at-eol     # all the class swaps in one screen
```
