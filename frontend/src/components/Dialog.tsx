import { type ReactNode, useEffect, useRef } from 'react'

export interface DialogProps {
  open: boolean
  onClose: () => void
  title: string
  children: ReactNode
}

/**
 * Wraps the native `<dialog>` element rather than reimplementing a modal
 * from scratch: `showModal()`/`close()` give us focus trapping, Escape-to-
 * close, and a real backdrop for free, with no extra dependency.
 */
export function Dialog({ open, onClose, title, children }: DialogProps) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) {
      return
    }
    if (open && !dialog.open) {
      dialog.showModal()
    } else if (!open && dialog.open) {
      dialog.close()
    }
  }, [open])

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      // Clicking the ::backdrop lands here too, since it's the dialog
      // element itself that's the click target in that case.
      onClick={(event) => {
        if (event.target === ref.current) {
          onClose()
        }
      }}
      className={
        'w-full max-w-md rounded-lg border border-border bg-surface p-0 text-ink shadow-lift ' +
        'backdrop:bg-ink/40 dark:backdrop:bg-black/60'
      }
    >
      <div className="flex items-center justify-between border-b border-border px-4 py-3">
        <h2 className="text-sm font-semibold">{title}</h2>
        <button
          type="button"
          onClick={onClose}
          aria-label="Close"
          className="rounded-md p-1 text-ink-faint hover:bg-surface-sunken hover:text-ink"
        >
          <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
            <path d="M6 6l12 12M6 18L18 6" />
          </svg>
        </button>
      </div>
      <div className="p-4">{children}</div>
    </dialog>
  )
}
