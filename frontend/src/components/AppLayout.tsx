import { Link, Outlet } from 'react-router-dom'

export default function AppLayout() {
  return (
    <div className="flex h-full flex-col">
      <header className="flex items-center gap-4 border-b border-surface-sunken bg-surface px-6 py-3">
        <Link to="/boards" className="text-base font-semibold tracking-tight">
          Flowdeck
        </Link>
        <span className="text-xs text-ink-faint">collaborative boards</span>
      </header>

      <main className="min-h-0 flex-1 overflow-auto">
        <Outlet />
      </main>
    </div>
  )
}
