import { Link } from 'react-router-dom'
import { Button } from '../components/Button'
import { EmptyState } from '../components/EmptyState'

export default function NotFoundPage() {
  return (
    <div className="flex h-screen items-center justify-center px-gutter">
      <EmptyState
        title="Page not found"
        description="That route doesn't exist."
        action={
          <Link to="/">
            <Button variant="secondary">Back home</Button>
          </Link>
        }
      />
    </div>
  )
}
