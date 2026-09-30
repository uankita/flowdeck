import { type FormEvent, useState } from 'react'
import { Button } from '../../components/Button'
import { Dialog } from '../../components/Dialog'
import { TextField } from '../../components/TextField'
import { useCreateBoard } from './useCreateBoard'

const BOARD_KEY_PATTERN = '^[A-Z][A-Z0-9]*$'

/** Auto-fills the key from the name (editable after) — mirrors CreateWorkspaceDialog's slug behaviour. */
function keyify(name: string): string {
  return name
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, '')
    .slice(0, 16)
}

export function CreateBoardDialog({
  workspaceId,
  open,
  onClose,
}: {
  workspaceId: string
  open: boolean
  onClose: () => void
}) {
  const [name, setName] = useState('')
  const [boardKey, setBoardKey] = useState('')
  const [keyEdited, setKeyEdited] = useState(false)
  const createBoard = useCreateBoard(workspaceId)

  function handleNameChange(value: string) {
    setName(value)
    if (!keyEdited) {
      setBoardKey(keyify(value))
    }
  }

  function handleClose() {
    setName('')
    setBoardKey('')
    setKeyEdited(false)
    onClose()
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault()
    createBoard.mutate(
      { name, boardKey },
      { onSuccess: handleClose },
    )
  }

  return (
    <Dialog open={open} onClose={handleClose} title="Create a board">
      <form onSubmit={handleSubmit} className="flex flex-col gap-4">
        <TextField
          label="Name"
          required
          autoFocus
          value={name}
          onChange={(event) => handleNameChange(event.target.value)}
        />
        <TextField
          label="Key"
          required
          maxLength={16}
          pattern={BOARD_KEY_PATTERN}
          title="Uppercase letters and digits, starting with a letter"
          value={boardKey}
          onChange={(event) => {
            setBoardKey(event.target.value.toUpperCase())
            setKeyEdited(true)
          }}
        />
        <p className="-mt-2 text-xs text-ink-faint">
          Seeded with three lists: Backlog, In progress, Done.
        </p>
        <Button type="submit" isLoading={createBoard.isPending} className="mt-2">
          Create board
        </Button>
      </form>
    </Dialog>
  )
}
