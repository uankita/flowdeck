import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  DndContext,
  DragOverlay,
  PointerSensor,
  KeyboardSensor,
  closestCorners,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragOverEvent,
  type DragStartEvent,
} from '@dnd-kit/core'
import { arrayMove, sortableKeyboardCoordinates } from '@dnd-kit/sortable'
import KanbanColumn from '../components/KanbanColumn'
import { CardBody } from '../components/KanbanCard'
import { boardsApi } from '../lib/api'
import { queryKeys } from '../lib/queryClient'
import { subscribeToBoard } from '../lib/ws'
import { useBoardUiStore } from '../store/boardUiStore'
import type { CardResponse, ColumnResponse } from '../lib/types'

function columnIndexContainingCard(
  columns: ColumnResponse[],
  cardId: string,
): number {
  return columns.findIndex((column) =>
    column.cards.some((card) => card.id === cardId),
  )
}

/** `over` may be a column (empty lane) or a card inside one. */
function resolveTargetColumnIndex(
  columns: ColumnResponse[],
  overId: string,
): number {
  const directIndex = columns.findIndex((column) => column.id === overId)
  if (directIndex !== -1) {
    return directIndex
  }
  return columnIndexContainingCard(columns, overId)
}

export default function BoardPage() {
  const { boardKey = '' } = useParams<{ boardKey: string }>()
  const queryClient = useQueryClient()

  const { activeCardId, setActiveCardId } = useBoardUiStore()

  const boardQuery = useQuery({
    queryKey: queryKeys.board(boardKey),
    queryFn: () => boardsApi.get(boardKey),
    enabled: boardKey.length > 0,
  })

  /**
   * Drag needs to mutate order on every pointer move, which is too fast to
   * round-trip. Columns are mirrored locally and re-seeded whenever the
   * server copy changes.
   */
  const [columns, setColumns] = useState<ColumnResponse[]>([])

  useEffect(() => {
    if (boardQuery.data) {
      setColumns(boardQuery.data.columns)
    }
  }, [boardQuery.data])

  // Live updates from collaborators. Any board event simply invalidates the
  // query — cheap, and avoids re-implementing merge logic on the client.
  useEffect(() => {
    if (!boardKey) {
      return
    }
    const unsubscribe = subscribeToBoard(boardKey, () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.board(boardKey) })
    })
    return unsubscribe
  }, [boardKey, queryClient])

  const sensors = useSensors(
    // A small distance threshold keeps clicks from registering as drags.
    useSensor(PointerSensor, { activationConstraint: { distance: 4 } }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    }),
  )

  const activeCard: CardResponse | undefined = activeCardId
    ? columns.flatMap((column) => column.cards).find((c) => c.id === activeCardId)
    : undefined

  function handleDragStart(event: DragStartEvent) {
    setActiveCardId(String(event.active.id))
  }

  function handleDragOver(event: DragOverEvent) {
    const { active, over } = event
    if (!over) {
      return
    }
    const activeId = String(active.id)
    const overId = String(over.id)

    setColumns((current) => {
      const fromIndex = columnIndexContainingCard(current, activeId)
      const toIndex = resolveTargetColumnIndex(current, overId)

      if (fromIndex === -1 || toIndex === -1 || fromIndex === toIndex) {
        return current
      }

      const from = current[fromIndex]
      const to = current[toIndex]
      if (!from || !to) {
        return current
      }

      const moving = from.cards.find((card) => card.id === activeId)
      if (!moving) {
        return current
      }

      // Insert at the hovered card's slot, or append when over the lane itself.
      const overCardIndex = to.cards.findIndex((card) => card.id === overId)
      const insertAt = overCardIndex === -1 ? to.cards.length : overCardIndex

      const next = [...current]
      next[fromIndex] = {
        ...from,
        cards: from.cards.filter((card) => card.id !== activeId),
      }
      next[toIndex] = {
        ...to,
        cards: [
          ...to.cards.slice(0, insertAt),
          moving,
          ...to.cards.slice(insertAt),
        ],
      }
      return next
    })
  }

  function handleDragEnd(event: DragEndEvent) {
    const { active, over } = event
    setActiveCardId(null)
    if (!over) {
      return
    }
    const activeId = String(active.id)
    const overId = String(over.id)

    setColumns((current) => {
      const columnIndex = columnIndexContainingCard(current, activeId)
      if (columnIndex === -1) {
        return current
      }
      const column = current[columnIndex]
      if (!column) {
        return current
      }

      const oldIndex = column.cards.findIndex((card) => card.id === activeId)
      const newIndex = column.cards.findIndex((card) => card.id === overId)
      if (oldIndex === -1 || newIndex === -1 || oldIndex === newIndex) {
        return current
      }

      const next = [...current]
      next[columnIndex] = {
        ...column,
        cards: arrayMove(column.cards, oldIndex, newIndex),
      }
      return next
    })

    // TODO: persist via PATCH /api/v1/cards/{id}/position once the endpoint
    // exists, then broadcast on /topic/boards/{boardKey}. Until then a reload
    // reverts the move.
  }

  if (boardQuery.isPending) {
    return <p className="px-6 py-8 text-sm text-ink-muted">Loading board…</p>
  }

  if (boardQuery.isError) {
    return (
      <p className="px-6 py-8 text-sm text-red-600">
        Could not load board “{boardKey}”.
      </p>
    )
  }

  return (
    <div className="flex h-full flex-col">
      <div className="px-6 pt-6">
        <span className="text-xs font-semibold text-accent">
          {boardQuery.data.boardKey}
        </span>
        <h1 className="text-xl font-semibold tracking-tight">
          {boardQuery.data.name}
        </h1>
      </div>

      <DndContext
        sensors={sensors}
        collisionDetection={closestCorners}
        onDragStart={handleDragStart}
        onDragOver={handleDragOver}
        onDragEnd={handleDragEnd}
        onDragCancel={() => setActiveCardId(null)}
      >
        <div className="flex flex-1 gap-4 overflow-x-auto px-6 py-6">
          {columns.map((column) => (
            <KanbanColumn key={column.id} column={column} />
          ))}
        </div>

        <DragOverlay>
          {activeCard ? (
            <div className="w-64 rotate-2">
              <CardBody card={activeCard} />
            </div>
          ) : null}
        </DragOverlay>
      </DndContext>
    </div>
  )
}
