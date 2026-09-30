import type {
  BoardDetail,
  BoardSummary,
  CreateBoardRequest,
  ProblemDetail,
} from './types'

/**
 * Empty in dev: requests go to a relative /api path and the Vite proxy
 * forwards them to Spring Boot, keeping the browser on one origin.
 * Set VITE_API_BASE_URL for builds served from a different host.
 */
const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

export class ApiError extends Error {
  readonly status: number
  readonly problem?: ProblemDetail

  constructor(status: number, message: string, problem?: ProblemDetail) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
  })

  if (!response.ok) {
    // The API returns application/problem+json for handled failures, but a
    // proxy or a 500 can still produce HTML — don't assume JSON parses.
    let problem: ProblemDetail | undefined
    try {
      problem = (await response.json()) as ProblemDetail
    } catch {
      problem = undefined
    }
    throw new ApiError(
      response.status,
      problem?.detail ?? problem?.title ?? `Request failed (${response.status})`,
      problem,
    )
  }

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export const boardsApi = {
  list: () => request<BoardSummary[]>('/api/v1/boards'),

  get: (boardKey: string) =>
    request<BoardDetail>(`/api/v1/boards/${encodeURIComponent(boardKey)}`),

  create: (body: CreateBoardRequest) =>
    request<BoardDetail>('/api/v1/boards', {
      method: 'POST',
      body: JSON.stringify(body),
    }),
}
