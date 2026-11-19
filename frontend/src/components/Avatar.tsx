/** A decorative initial alongside the person's visible name; no profile image is required. */
export function Avatar({ name, className = "" }: { name: string; className?: string }) {
  return <span aria-hidden="true" className={`avatar ${className}`}>{name.trim().charAt(0).toUpperCase()}</span>;
}
