/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
  readonly VITE_APP_PREVIEW_BASE_URL: string
  readonly VITE_APP_DEPLOY_BASE_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module 'event-source-polyfill' {
  export const EventSourcePolyfill: any
}
