import { apiClient } from '../../api/client'
import type { PageResponse } from '../../api/pagination'
import type { components } from '../../api/schema'

export type Workspace = Required<components['schemas']['WorkspaceResponse']>
export type CreateWorkspaceRequest = components['schemas']['CreateWorkspaceRequest']

export async function listWorkspaces(page = 0, size = 20): Promise<PageResponse<Workspace>> {
  const { data } = await apiClient.get<PageResponse<Workspace>>('/api/v1/workspaces', {
    params: { page, size },
  })
  return data
}

export async function createWorkspace(body: CreateWorkspaceRequest): Promise<Workspace> {
  const { data } = await apiClient.post<Workspace>('/api/v1/workspaces', body)
  return data
}

export async function getWorkspace(workspaceId: string): Promise<Workspace> {
  const { data } = await apiClient.get<Workspace>(`/api/v1/workspaces/${workspaceId}`)
  return data
}
