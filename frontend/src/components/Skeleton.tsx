import { cn } from '../lib/cn'

/**
 * A loading placeholder shaped like the content it stands in for — pass
 * sizing classes (`h-4 w-32`, `h-24 w-full`, ...) per use site. Prefer this
 * over a spinner for anything that has a predictable layout (a list of
 * cards, a text field) — it reserves the right amount of space so nothing
 * jumps when real content arrives, which a centered spinner can't do.
 */
export function Skeleton({ className }: { className?: string }) {
  return (
    <div
      role="presentation"
      aria-hidden="true"
      className={cn('animate-skeleton-pulse rounded-md bg-surface-sunken', className)}
    />
  )
}
