/** The five avatar colours of the theme, written out so Tailwind finds the class names. */
const PERSON_COLOURS = ["bg-person-1", "bg-person-2", "bg-person-3", "bg-person-4", "bg-person-5"] as const;

/** The avatar colour of a person: a function of their id, so the same person has the same colour on every screen. */
export function personColour(userId: string): (typeof PERSON_COLOURS)[number] {
  let hash = 0;
  for (const char of userId) hash = (hash * 31 + char.charCodeAt(0)) % 1_000_003;
  return PERSON_COLOURS[hash % PERSON_COLOURS.length];
}

/**
 * A person's initial on their colour (design 2026-11-23, requirement "Identity"). Decoration only: the name is always
 * written next to it, so screen readers skip it.
 */
export function Avatar({ userId, name }: { userId: string; name: string }) {
  return (
    <span
      aria-hidden="true"
      className={`inline-grid size-10 shrink-0 place-items-center rounded-full font-display font-extrabold text-foreground ${personColour(userId)}`}
    >
      {name.charAt(0).toUpperCase()}
    </span>
  );
}
