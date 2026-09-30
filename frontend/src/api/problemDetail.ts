import { isAxiosError } from 'axios'

/**
 * The RFC 7807 shape every error response from the backend uses (see its
 * `GlobalExceptionHandler`) — not generated from the OpenAPI spec, because
 * `ProblemDetail` isn't declared as a component schema there (springdoc
 * doesn't infer one from a plain `ProblemDetail` return type). `[key:
 * string]: unknown` covers extension members like `currentState` on a 409
 * (see the version-conflict handling in the backend's card/list update
 * endpoints) without needing every possible one named here.
 */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  [key: string]: unknown
}

/** True if the conflict response carries the shape a version-conflict 409 attaches — see `ProblemDetail`'s Javadoc. */
export function getConflictState<T>(error: unknown): T | undefined {
  if (isAxiosError(error) && error.response?.status === 409) {
    return (error.response.data as ProblemDetail | undefined)?.currentState as T | undefined
  }
  return undefined
}

/** Best-effort human-readable message for a toast — falls back gracefully for network errors, timeouts, etc. */
export function getErrorMessage(error: unknown): string {
  if (isAxiosError<ProblemDetail>(error)) {
    const problem = error.response?.data
    if (problem?.detail) {
      return problem.detail
    }
    if (problem?.title) {
      return problem.title
    }
    if (error.code === 'ERR_NETWORK') {
      return 'Could not reach the server. Check your connection and try again.'
    }
  }
  if (error instanceof Error) {
    return error.message
  }
  return 'Something went wrong. Please try again.'
}
