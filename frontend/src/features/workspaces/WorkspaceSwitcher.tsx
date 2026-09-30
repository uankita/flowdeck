import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { Skeleton } from '../../components/Skeleton'
import { cn } from '../../lib/cn'
import { CreateWorkspaceDialog } from './CreateWorkspaceDialog'
import { useWorkspaces } from './useWorkspaces'

export function WorkspaceSwitcher({ currentWorkspaceId }: { currentWorkspaceId: string }) {
  const [isOpen, setIsOpen] = useState(false)
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const { data, isPending } = useWorkspaces()

  useEffect(() => {
    if (!isOpen) {
      return
    }
    function handleClickOutside(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setIsOpen(false)
      }
    }
    function handleEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setIsOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    document.addEventListener('keydown', handleEscape)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside)
      document.removeEventListener('keydown', handleEscape)
    }
  }, [isOpen])

  const current = data?.content.find((workspace) => workspace.id === currentWorkspaceId)

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        onClick={() => setIsOpen((prev) => !prev)}
        aria-haspopup="listbox"
        aria-expanded={isOpen}
        className="flex items-center gap-2 rounded-md px-2 py-1.5 text-sm font-medium text-ink hover:bg-surface-sunken"
      >
        {current ? (
          <span>{current.name}</span>
        ) : (
          <Skeleton className="h-4 w-24" />
        )}
        <svg className="h-4 w-4 text-ink-faint" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
          <path d="M6 9l6 6 6-6" />
        </svg>
      </button>

      {isOpen && (
        <div
          role="listbox"
          className="absolute left-0 top-full z-10 mt-1 w-64 rounded-md border border-border bg-surface py-1 shadow-lift"
        >
          {isPending ? (
            <div className="flex flex-col gap-2 p-3">
              <Skeleton className="h-8 w-full" />
              <Skeleton className="h-8 w-full" />
            </div>
          ) : (
            data?.content.map((workspace) => (
              <Link
                key={workspace.id}
                to={`/w/${workspace.id}/boards`}
                role="option"
                aria-selected={workspace.id === currentWorkspaceId}
                onClick={() => setIsOpen(false)}
                className={cn(
                  'block px-3 py-2 text-sm hover:bg-surface-sunken',
                  workspace.id === currentWorkspaceId ? 'font-medium text-accent' : 'text-ink',
                )}
              >
                {workspace.name}
              </Link>
            ))
          )}
          <div className="mt-1 border-t border-border pt-1">
            <button
              type="button"
              onClick={() => {
                setIsOpen(false)
                setIsCreateOpen(true)
              }}
              className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-ink-muted hover:bg-surface-sunken hover:text-ink"
            >
              <svg className="h-4 w-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                <path d="M12 5v14M5 12h14" />
              </svg>
              Create workspace
            </button>
          </div>
        </div>
      )}

      <CreateWorkspaceDialog open={isCreateOpen} onClose={() => setIsCreateOpen(false)} />
    </div>
  )
}
