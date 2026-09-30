import { useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { getErrorMessage } from '../../api/problemDetail'
import { createWorkspace } from './api'
import { workspacesQueryKey } from './useWorkspaces'

export function useCreateWorkspace() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createWorkspace,
    onSuccess: (workspace) => {
      void queryClient.invalidateQueries({ queryKey: workspacesQueryKey })
      toast.success(`Created workspace "${workspace.name}"`)
    },
    onError: (error) => {
      toast.error(getErrorMessage(error))
    },
  })
}
