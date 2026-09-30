/**
 * The backend emits one concrete schema per instantiation — e.g.
 * `PageResponseWorkspaceResponse`, `PageResponseBoardSummaryResponse` (see
 * `components['schemas']` in schema.ts) — since springdoc can't express a
 * generic directly. This is the generic they're all structurally identical
 * to, so frontend code (generic hooks, the `<Pagination>` component) can
 * work with one shape instead of N near-duplicates; a
 * `PageResponseWorkspaceResponse` is already assignable to
 * `PageResponse<Workspace>` with no cast.
 */
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
