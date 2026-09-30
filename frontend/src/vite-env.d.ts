/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Base URL of the Spring Boot API. Empty string routes through the Vite dev proxy. */
  readonly VITE_API_BASE_URL?: string
  /** STOMP endpoint, e.g. ws://localhost:8080/ws */
  readonly VITE_WS_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
