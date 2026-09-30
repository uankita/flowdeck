import { useSortable } from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import type { CardResponse, CardPriority } from '../lib/types'

const PRIORITY_STYLES: Record<CardPriority, string> = {
  LOW: 'bg-surface-sunken text-ink-muted',
  MEDIUM: 'bg-accent-soft text-accent',
  HIGH: 'bg-amber-100 text-amber-800',
  URGENT: 'bg-red-100 text-red-700',
}

interface KanbanCardProps {
  card: CardResponse
}

/** Presentational card, also reused inside the DragOverlay. */
export function CardBody({ card }: KanbanCardProps) {
  return (
    <div className="rounded-md border border-surface-sunken bg-surface p-3 shadow-card">
      <p className="text-sm leading-snug">{card.title}</p>
      <div className="mt-2 flex items-center gap-2">
        <span
          className={`rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${PRIORITY_STYLES[card.priority]}`}
        >
          {card.priority}
        </span>
        {card.assignee ? (
          <span className="text-[11px] text-ink-faint">{card.assignee}</span>
        ) : null}
      </div>
    </div>
  )
}

export default function KanbanCard({ card }: KanbanCardProps) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } =
    useSortable({ id: card.id, data: { type: 'card' } })

  return (
    <li
      ref={setNodeRef}
      style={{
        transform: CSS.Translate.toString(transform),
        transition,
        // The overlay renders the card being dragged; leave a gap behind it.
        opacity: isDragging ? 0.4 : 1,
      }}
      {...attributes}
      {...listeners}
      className="cursor-grab touch-none active:cursor-grabbing"
    >
      <CardBody card={card} />
    </li>
  )
}
