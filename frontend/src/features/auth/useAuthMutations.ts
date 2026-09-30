import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { useNavigate } from 'react-router-dom'
import { getErrorMessage } from '../../api/problemDetail'
import { tokenStore } from '../../api/tokenStore'
import * as authApi from './api'

export function useLogin() {
  const navigate = useNavigate()
  return useMutation({
    mutationFn: authApi.login,
    onSuccess: (data) => {
      tokenStore.setTokens(data.accessToken, data.refreshToken)
      navigate('/', { replace: true })
    },
    onError: (error) => {
      toast.error(getErrorMessage(error))
    },
  })
}

export function useRegister() {
  const navigate = useNavigate()
  return useMutation({
    mutationFn: authApi.register,
    onSuccess: (data) => {
      tokenStore.setTokens(data.accessToken, data.refreshToken)
      toast.success(`Welcome, ${data.user.displayName}!`)
      navigate('/', { replace: true })
    },
    onError: (error) => {
      toast.error(getErrorMessage(error))
    },
  })
}

export function useLogout() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async () => {
      const refreshToken = tokenStore.getRefreshToken()
      if (refreshToken) {
        // Best-effort: revokes the refresh family server-side, but the
        // client forgets its tokens regardless of whether this succeeds —
        // see onSettled below.
        await authApi.logout(refreshToken)
      }
    },
    onSettled: () => {
      tokenStore.clear()
      queryClient.clear()
      navigate('/login', { replace: true })
    },
  })
}
