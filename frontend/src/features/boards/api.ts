import { apiClient } from '../../api/client'
import type { PageResponse } from '../../api/pagination'
import type { components } from '../../api/schema'

export type Board = Required<components['schemas']['BoardSummaryResponse']>
export type BoardDetail = Required<components['schemas']['BoardDetailResponse']>
export type CreateBoardRequest = components['schemas']['CreateBoardRequest']

export async function listBoards(
  workspaceId: string,
  page = 0,
  size = 20,
): Promise<PageResponse<Board>> {
  const { data } = await apiClient.get<PageResponse<Board>>(
    `/api/v1/workspaces/${workspaceId}/boards`,
    { params: { page, size } },
  )
  return data
}

/** Returns the full detail shape (seeded lists included) — a `BoardDetail` satisfies `Board` structurally, so callers that only need the summary fields can ignore the rest. */
export async function createBoard(workspaceId: string, body: CreateBoardRequest): Promise<BoardDetail> {
  const { data } = await apiClient.post<BoardDetail>(`/api/v1/workspaces/${workspaceId}/boards`, body)
  return data
}
