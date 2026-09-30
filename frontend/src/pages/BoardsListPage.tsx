import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { boardsApi, ApiError } from '../lib/api'
import { queryKeys } from '../lib/queryClient'

export default function BoardsListPage() {
  const queryClient = useQueryClient()
  const [boardKey, setBoardKey] = useState('')
  const [name, setName] = useState('')

  const boardsQuery = useQuery({
    queryKey: queryKeys.boards,
    queryFn: boardsApi.list,
  })

  const createBoard = useMutation({
    mutationFn: boardsApi.create,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.boards })
      setBoardKey('')
      setName('')
    },
  })

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    createBoard.mutate({ boardKey: boardKey.toUpperCase(), name })
  }

  return (
    <div className="mx-auto max-w-4xl px-6 py-8">
      <h1 className="text-xl font-semibold tracking-tight">Boards</h1>

      <form
        onSubmit={handleSubmit}
        className="mt-6 flex flex-wrap items-end gap-3 rounded-lg border border-surface-sunken bg-surface p-4 shadow-card"
      >
        <label className="flex flex-col gap-1">
          <span className="text-xs font-medium text-ink-muted">Key</span>
          <input
            value={boardKey}
            onChange={(e) => setBoardKey(e.target.value)}
            placeholder="FLOW"
            maxLength={16}
            required
            className="w-28 rounded-md border border-surface-sunken px-2 py-1.5 text-sm uppercase"
          />
        </label>

        <label className="flex flex-1 flex-col gap-1">
          <span className="text-xs font-medium text-ink-muted">Name</span>
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Flowdeck roadmap"
            required
            className="min-w-48 rounded-md border border-surface-sunken px-2 py-1.5 text-sm"
          />
        </label>

        <button
          type="submit"
          disabled={createBoard.isPending}
          className="rounded-md bg-accent px-3 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {createBoard.isPending ? 'Creating…' : 'Create board'}
        </button>
      </form>

      {createBoard.error ? (
        <p className="mt-3 text-sm text-red-600">
          {createBoard.error instanceof ApiError
            ? createBoard.error.message
            : 'Could not create the board.'}
        </p>
      ) : null}

      <div className="mt-8">
        {boardsQuery.isPending ? (
          <p className="text-sm text-ink-muted">Loading boards…</p>
        ) : boardsQuery.isError ? (
          <p className="text-sm text-red-600">
            Could not reach the API. Is the backend running on :8080?
          </p>
        ) : boardsQuery.data.length === 0 ? (
          <p className="text-sm text-ink-muted">
            No boards yet — create the first one above.
          </p>
        ) : (
          <ul className="grid gap-3 sm:grid-cols-2">
            {boardsQuery.data.map((board) => (
              <li key={board.id}>
                <Link
                  to={`/boards/${board.boardKey}`}
                  className="block rounded-lg border border-surface-sunken bg-surface p-4 shadow-card transition hover:shadow-lift"
                >
                  <span className="text-xs font-semibold text-accent">
                    {board.boardKey}
                  </span>
                  <p className="mt-1 text-sm font-medium">{board.name}</p>
                  {board.description ? (
                    <p className="mt-1 text-xs text-ink-muted">
                      {board.description}
                    </p>
                  ) : null}
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
