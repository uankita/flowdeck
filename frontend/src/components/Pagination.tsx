import type { PageResponse } from '../api/pagination'

export function Pagination<T>({
  page,
  onPageChange,
}: {
  page: PageResponse<T>
  onPageChange: (page: number) => void
}) {
  if (page.totalPages <= 1) {
    return null
  }

  return (
    <div className="flex items-center justify-between text-sm text-ink-muted">
      <span>
        Page {page.page + 1} of {page.totalPages} · {page.totalElements} total
      </span>
      <div className="flex gap-2">
        <button
          type="button"
          disabled={page.page === 0}
          onClick={() => onPageChange(page.page - 1)}
          className="rounded-md border border-border px-2 py-1 disabled:cursor-not-allowed disabled:opacity-50"
        >
          Previous
        </button>
        <button
          type="button"
          disabled={page.page + 1 >= page.totalPages}
          onClick={() => onPageChange(page.page + 1)}
          className="rounded-md border border-border px-2 py-1 disabled:cursor-not-allowed disabled:opacity-50"
        >
          Next
        </button>
      </div>
    </div>
  )
}
