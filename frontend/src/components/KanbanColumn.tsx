import { useDroppable } from '@dnd-kit/core'
import { SortableContext, verticalListSortingStrategy } from '@dnd-kit/sortable'
import KanbanCard from './KanbanCard'
import type { ColumnResponse } from '../lib/types'

interface KanbanColumnProps {
  column: ColumnResponse
}

export default function KanbanColumn({ column }: KanbanColumnProps) {
  // Registered as a droppable in its own right so an empty column is still a
  // valid drop target — SortableContext alone would give it no hit area.
  const { setNodeRef, isOver } = useDroppable({
    id: column.id,
    data: { type: 'column' },
  })

  const overLimit =
    column.wipLimit !== undefined && column.cards.length > column.wipLimit

  return (
    <section className="flex w-72 shrink-0 flex-col rounded-lg bg-surface-sunken/60 p-2">
      <header className="flex items-center justify-between px-1 py-1.5">
        <h2 className="text-sm font-semibold">{column.name}</h2>
        <span
          className={`text-xs tabular-nums ${overLimit ? 'font-semibold text-red-600' : 'text-ink-faint'}`}
        >
          {column.cards.length}
          {column.wipLimit !== undefined ? ` / ${column.wipLimit}` : ''}
        </span>
      </header>

      <SortableContext
        items={column.cards.map((card) => card.id)}
        strategy={verticalListSortingStrategy}
      >
        <ul
          ref={setNodeRef}
          className={`flex min-h-24 flex-1 flex-col gap-2 rounded-md p-1 transition-colors ${
            isOver ? 'bg-accent-soft' : ''
          }`}
        >
          {column.cards.map((card) => (
            <KanbanCard key={card.id} card={card} />
          ))}
          {column.cards.length === 0 ? (
            <li className="px-2 py-3 text-xs text-ink-faint">Drop cards here</li>
          ) : null}
        </ul>
      </SortableContext>
    </section>
  )
}
