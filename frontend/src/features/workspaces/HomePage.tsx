import { useState } from 'react'
import { Navigate } from 'react-router-dom'
import { Button } from '../../components/Button'
import { EmptyState } from '../../components/EmptyState'
import { FullPageSpinner } from '../../components/FullPageSpinner'
import { CreateWorkspaceDialog } from './CreateWorkspaceDialog'
import { useWorkspaces } from './useWorkspaces'

/**
 * `/` itself: not a page anyone lingers on. With a workspace, it redirects
 * straight into it — this route exists to land somewhere before a
 * workspace is known, not to be a second "your workspaces" listing
 * alongside {@link WorkspaceSwitcher}. With none yet, it's the natural
 * place to prompt creating the first one.
 */
export default function HomePage() {
  const [isCreateOpen, setIsCreateOpen] = useState(false)
  const workspaces = useWorkspaces()

  if (workspaces.isPending) {
    return <FullPageSpinner label="Loading your workspaces…" />
  }

  const first = workspaces.data?.content[0]
  if (first) {
    return <Navigate to={`/w/${first.id}/boards`} replace />
  }

  return (
    <div className="flex h-full items-center justify-center px-gutter">
      <EmptyState
        title="Welcome to Flowdeck"
        description="Create a workspace to get started."
        action={<Button onClick={() => setIsCreateOpen(true)}>Create workspace</Button>}
      />
      <CreateWorkspaceDialog open={isCreateOpen} onClose={() => setIsCreateOpen(false)} />
    </div>
  )
}
