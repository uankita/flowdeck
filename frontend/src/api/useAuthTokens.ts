import { useSyncExternalStore } from 'react'
import { tokenStore } from './tokenStore'

/**
 * Reactive view of {@link tokenStore} for components. `useSyncExternalStore`
 * (rather than mirroring the store into `useState`) is the correct tool
 * here specifically because the store is mutated from *outside* React too —
 * by `client.ts`'s response interceptor — so a component needs to be told
 * about changes it didn't trigger itself, not just ones from its own event
 * handlers.
 */
export function useAuthTokens() {
  return useSyncExternalStore(tokenStore.subscribe, tokenStore.getSnapshot)
}
