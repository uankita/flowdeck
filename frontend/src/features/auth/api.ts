import { apiClient } from '../../api/client'
import type { components } from '../../api/schema'

export type TokenPair = Required<components['schemas']['TokenPairResponse']>
export type UserSummary = Required<components['schemas']['UserSummaryResponse']>
export type RegisterRequest = components['schemas']['RegisterRequest']
export type LoginRequest = components['schemas']['LoginRequest']

export async function register(body: RegisterRequest): Promise<TokenPair> {
  const { data } = await apiClient.post<TokenPair>('/api/auth/register', body)
  return data
}

export async function login(body: LoginRequest): Promise<TokenPair> {
  const { data } = await apiClient.post<TokenPair>('/api/auth/login', body)
  return data
}

export async function logout(refreshToken: string): Promise<void> {
  await apiClient.post('/api/auth/logout', { refreshToken })
}

export async function getCurrentUser(): Promise<UserSummary> {
  const { data } = await apiClient.get<UserSummary>('/api/v1/me')
  return data
}
