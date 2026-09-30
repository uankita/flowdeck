import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { Button } from '../../components/Button'
import { EmptyState } from '../../components/EmptyState'
import { Pagination } from '../../components/Pagination'
import { Skeleton } from '../../components/Skeleton'
import { BoardCard } from './BoardCard'
import { CreateBoardDialog } from './CreateBoardDialog'
import { useBoards } from './useBoards'

export default function BoardListPage() {
  const { workspaceId = '' } = useParams<{ workspaceId: string }>()
  const [page, setPage] = useState(0)
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const boards = useBoards(workspaceId, page)

  return (
    <div className="mx-auto max-w-5xl px-gutter py-section">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold tracking-tight text-ink">Boards</h1>
        <Button onClick={() => setIsCreateOpen(true)}>New board</Button>
      </div>

      <div className="mt-6">
        {boards.isPending ? (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {Array.from({ length: 6 }, (_, i) => (
              <Skeleton key={i} className="h-24 w-full" />
            ))}
          </div>
        ) : boards.isError ? (
          <EmptyState
            title="Couldn't load boards"
            description="Something went wrong reaching the server. Try reloading the page."
          />
        ) : boards.data.content.length === 0 ? (
          <EmptyState
            title="No boards yet"
            description="Create your first board to start organizing work."
            action={<Button onClick={() => setIsCreateOpen(true)}>New board</Button>}
          />
        ) : (
          <>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {boards.data.content.map((board) => (
                <BoardCard key={board.id} workspaceId={workspaceId} board={board} />
              ))}
            </div>
            <div className="mt-6">
              <Pagination page={boards.data} onPageChange={setPage} />
            </div>
          </>
        )}
      </div>

      <CreateBoardDialog
        workspaceId={workspaceId}
        open={isCreateOpen}
        onClose={() => setIsCreateOpen(false)}
      />
    </div>
  )
}
