import { Link } from 'react-router-dom'

export default function NotFoundPage() {
  return (
    <div className="mx-auto max-w-lg px-6 py-20 text-center">
      <h1 className="text-lg font-semibold">Page not found</h1>
      <p className="mt-2 text-sm text-ink-muted">
        That route does not exist.
      </p>
      <Link
        to="/boards"
        className="mt-6 inline-block rounded-md bg-accent px-3 py-2 text-sm font-medium text-white"
      >
        Back to boards
      </Link>
    </div>
  )
}
