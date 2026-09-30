/** Mirrors com.flowdeck.web.dto.BoardDtos on the backend. */

export type CardPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT'

export interface CardResponse {
  id: string
  title: string
  description?: string
  position: number
  priority: CardPriority
  assignee?: string
  dueAt?: string
}

export interface ColumnResponse {
  id: string
  name: string
  position: number
  wipLimit?: number
  cards: CardResponse[]
}

export interface BoardSummary {
  id: string
  boardKey: string
  name: string
  description?: string
  archived: boolean
  createdAt: string
  updatedAt: string
}

export interface BoardDetail extends BoardSummary {
  columns: ColumnResponse[]
}

export interface CreateBoardRequest {
  boardKey: string
  name: string
  description?: string
}

/** RFC 7807 body returned by GlobalExceptionHandler. */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
}
