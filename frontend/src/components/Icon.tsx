const paths = {
  split: "M5 5h5v5H5z M14 14h5v5h-5z M14 5h5v5h-5z M5 14h5v5H5z",
  "arrow-right": "M5 12h14m-6-6 6 6-6 6",
  "arrow-left": "M19 12H5m6-6-6 6 6 6",
  plus: "M12 5v14M5 12h14",
  group: "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2m20 0v-2a4 4 0 0 0-3-3.87M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8m8-7.87a4 4 0 0 1 0 7.75",
  receipt: "M6 3h12v18l-3-2-3 2-3-2-3 2V3Zm3 4h6m-6 4h6m-6 4h3",
  balance: "M12 3v18m-7 0h14M4 7h16M6 7l-4 7h8L6 7Zm12 0-4 7h8l-4-7Z",
  lock: "M7 11V7a5 5 0 0 1 10 0v4M5 11h14v10H5zM12 15v2",
  bell: "M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4",
  check: "m5 12 4 4L19 6",
  close: "m6 6 12 12M6 18 18 6",
} as const;

/** Small decorative icons inherit the surrounding text colour and never replace an accessible label. */
export function Icon({ name, className = "" }: { name: keyof typeof paths; className?: string }) {
  return (
    <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7"
      strokeLinecap="round" strokeLinejoin="round" className={`size-5 shrink-0 ${className}`}>
      <path d={paths[name]} />
    </svg>
  );
}
