import { create } from 'zustand'

/**
 * Ephemeral board UI state.
 *
 * Server data lives in TanStack Query — this store holds only things the
 * server does not own: what is being dragged, which card is open, filters.
 * Keeping the two separate avoids a second, stale copy of the board.
 */
interface BoardUiState {
  activeCardId: string | null
  selectedCardId: string | null
  assigneeFilter: string | null

  setActiveCardId: (cardId: string | null) => void
  setSelectedCardId: (cardId: string | null) => void
  setAssigneeFilter: (assignee: string | null) => void
  reset: () => void
}

export const useBoardUiStore = create<BoardUiState>((set) => ({
  activeCardId: null,
  selectedCardId: null,
  assigneeFilter: null,

  setActiveCardId: (activeCardId) => set({ activeCardId }),
  setSelectedCardId: (selectedCardId) => set({ selectedCardId }),
  setAssigneeFilter: (assigneeFilter) => set({ assigneeFilter }),
  reset: () =>
    set({ activeCardId: null, selectedCardId: null, assigneeFilter: null }),
}))
