import { type FormEvent, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '../../components/Button'
import { Dialog } from '../../components/Dialog'
import { TextField } from '../../components/TextField'
import { useCreateWorkspace } from './useCreateWorkspace'

// A hyphen inside a character class — [a-z0-9-], [-a-z0-9], even the
// backslash-escaped [a-z0-9\-] — is rejected outright by at least one
// current browser's `pattern`-attribute regex validation (confirmed by
// hand against the same Chrome build this was developed against: every
// character-class form throws "Invalid character in character class"/
// "Invalid character class" under the newer Unicode-set regex semantics
// the `pattern` attribute is now validated with; only an alternation
// avoids a character class entirely). Same matched language as the
// backend's Java @Pattern either way.
const SLUG_PATTERN = '^[a-z0-9](-|[a-z0-9])*$'

/** Auto-fills the slug from the name (editable after) — most people won't want to think up a separate URL segment. */
function slugify(name: string): string {
  return name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
}

export function CreateWorkspaceDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const [name, setName] = useState('')
  const [slug, setSlug] = useState('')
  const [slugEdited, setSlugEdited] = useState(false)
  const createWorkspace = useCreateWorkspace()
  const navigate = useNavigate()

  function handleNameChange(value: string) {
    setName(value)
    if (!slugEdited) {
      setSlug(slugify(value))
    }
  }

  function handleClose() {
    setName('')
    setSlug('')
    setSlugEdited(false)
    onClose()
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    createWorkspace.mutate(
      { name, slug },
      {
        onSuccess: (workspace) => {
          handleClose()
          navigate(`/w/${workspace.id}/boards`)
        },
      },
    )
  }

  return (
    <Dialog open={open} onClose={handleClose} title="Create a workspace">
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <TextField
          label="Name"
          required
          autoFocus
          value={name}
          onChange={(event) => handleNameChange(event.target.value)}
        />
        <TextField
          label="URL slug"
          required
          pattern={SLUG_PATTERN}
          title="Lowercase letters, numbers, and hyphens, starting alphanumeric"
          value={slug}
          onChange={(event) => {
            setSlug(event.target.value)
            setSlugEdited(true)
          }}
        />
        <Button type="submit" isLoading={createWorkspace.isPending} className="mt-2">
          Create workspace
        </Button>
      </form>
    </Dialog>
  )
}
