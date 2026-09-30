import { Link, Outlet, useParams } from 'react-router-dom'
import { Skeleton } from '../components/Skeleton'
import { ThemeToggle } from '../components/ThemeToggle'
import { useLogout } from '../features/auth/useAuthMutations'
import { useCurrentUser } from '../features/auth/useCurrentUser'
import { WorkspaceSwitcher } from '../features/workspaces/WorkspaceSwitcher'

/** Layout route for every authenticated page: top bar + `<Outlet />`. `workspaceId` is absent on `/` (the pre-workspace-selection home), so the switcher only renders once there's one to show. */
export function AppShell() {
  const { workspaceId } = useParams<{ workspaceId: string }>()
  const currentUser = useCurrentUser()
  const logout = useLogout()

  return (
    <div className="flex h-screen flex-col">
      <header className="flex items-center justify-between border-b border-border bg-surface px-gutter py-3">
        <div className="flex items-center gap-4">
          <Link to="/" className="text-base font-semibold tracking-tight text-ink">
            Flowdeck
          </Link>
          {workspaceId && <WorkspaceSwitcher currentWorkspaceId={workspaceId} />}
        </div>

        <div className="flex items-center gap-3">
          <ThemeToggle />
          {currentUser.data ? (
            <span className="text-sm text-ink-muted">{currentUser.data.displayName}</span>
          ) : (
            <Skeleton className="h-4 w-20" />
          )}
          <button
            type="button"
            onClick={() => logout.mutate()}
            disabled={logout.isPending}
            className="text-sm font-medium text-ink-muted hover:text-ink disabled:opacity-50"
          >
            Log out
          </button>
        </div>
      </header>

      <main className="min-h-0 flex-1 overflow-auto">
        <Outlet />
      </main>
    </div>
  )
}
