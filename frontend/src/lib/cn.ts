import clsx, { type ClassValue } from 'clsx'

/** Thin re-export so call sites don't need to know it's clsx underneath. */
export function cn(...inputs: ClassValue[]): string {
  return clsx(inputs)
}
