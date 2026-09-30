import { Link } from 'react-router-dom'
import type { Board } from './api'

export function BoardCard({ workspaceId, board }: { workspaceId: string; board: Board }) {
  return (
    <Link
      to={`/w/${workspaceId}/boards/${board.boardKey}`}
      className="block rounded-lg border border-border bg-surface p-4 shadow-card transition hover:shadow-lift"
    >
      <span className="text-xs font-semibold text-accent">{board.boardKey}</span>
      <p className="mt-1 truncate text-sm font-medium text-ink">{board.name}</p>
      {board.description && (
        <p className="mt-1 line-clamp-2 text-xs text-ink-muted">{board.description}</p>
      )}
    </Link>
  )
}
