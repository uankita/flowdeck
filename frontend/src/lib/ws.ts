import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'

/**
 * Resolves the STOMP endpoint.
 *
 * Falls back to the page's own origin so the Vite dev proxy (`ws: true`)
 * carries the socket, which keeps dev on a single origin like production.
 * `VITE_WS_URL` overrides it and accepts an http(s) URL for convenience.
 */
function resolveBrokerUrl(): string {
  const configured = import.meta.env.VITE_WS_URL
  if (configured) {
    return configured.replace(/^http/, 'ws')
  }
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws`
}

export function createStompClient(): Client {
  return new Client({
    brokerURL: resolveBrokerUrl(),
    reconnectDelay: 4000,
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 10_000,
    // Flip to console.log when debugging a handshake.
    debug: () => {},
  })
}

export type BoardEventHandler = (message: IMessage) => void

/**
 * Connects and subscribes to one board's topic.
 *
 * Returns a teardown function. Subscribing happens in `onConnect` because a
 * reconnect drops server-side subscriptions — this re-establishes them.
 */
export function subscribeToBoard(
  boardKey: string,
  onEvent: BoardEventHandler,
): () => void {
  const client = createStompClient()
  let subscription: StompSubscription | undefined

  client.onConnect = () => {
    subscription = client.subscribe(`/topic/boards/${boardKey}`, onEvent)
  }

  client.onStompError = (frame) => {
    console.error('STOMP error', frame.headers['message'], frame.body)
  }

  client.activate()

  return () => {
    subscription?.unsubscribe()
    void client.deactivate()
  }
}
