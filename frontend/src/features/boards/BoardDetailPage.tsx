import { Link, useParams } from 'react-router-dom'
import { EmptyState } from '../../components/EmptyState'

/**
 * Placeholder: this task builds the shell (auth, routing, workspace switcher,
 * board *list*) — the Kanban board view (lists, cards, drag-and-drop) is a
 * separate piece of work. This page exists so `BoardCard`'s link goes
 * somewhere real instead of a 404, not to stand in for that page.
 */
export default function BoardDetailPage() {
  const { workspaceId, boardKey } = useParams<{ workspaceId: string; boardKey: string }>()

  return (
    <div className="mx-auto max-w-5xl px-gutter py-section">
      <EmptyState
        title={`Board ${boardKey}`}
        description="The board view (lists, cards, drag-and-drop) isn't built yet — this page is a placeholder so the link from the board list works."
        action={
          <Link to={`/w/${workspaceId}/boards`} className="text-sm font-medium text-accent hover:underline">
            Back to boards
          </Link>
        }
      />
    </div>
  )
}
