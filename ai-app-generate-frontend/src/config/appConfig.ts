const DEFAULT_API_BASE_URL = 'http://localhost:8123/api'
const DEFAULT_APP_PREVIEW_BASE_URL = 'http://localhost:8123'
const DEFAULT_APP_DEPLOY_BASE_URL = 'http://localhost:8123'

const trimTrailingSlash = (value: string) => value.replace(/\/+$/, '')

const trimLeadingSlash = (value: string) => value.replace(/^\/+/, '')

const isAbsoluteUrl = (value: string) => /^[a-z][a-z\d+.-]*:\/\//i.test(value)

const joinUrl = (baseUrl: string, path: string) => {
  const normalizedBaseUrl = trimTrailingSlash(baseUrl)
  const normalizedPath = trimLeadingSlash(path)
  if (!normalizedBaseUrl) {
    return normalizedPath
  }
  return normalizedPath ? `${normalizedBaseUrl}/${normalizedPath}` : normalizedBaseUrl
}

const normalizeUrl = (value: string, fallbackValue: string) => {
  const trimmedValue = value.trim()
  return trimTrailingSlash(trimmedValue || fallbackValue)
}

export const apiBaseUrl = normalizeUrl(import.meta.env.VITE_API_BASE_URL, DEFAULT_API_BASE_URL)

export const appPreviewBaseUrl = normalizeUrl(
  import.meta.env.VITE_APP_PREVIEW_BASE_URL,
  DEFAULT_APP_PREVIEW_BASE_URL,
)

export const appDeployBaseUrl = normalizeUrl(
  import.meta.env.VITE_APP_DEPLOY_BASE_URL,
  DEFAULT_APP_DEPLOY_BASE_URL,
)

export const buildApiUrl = (path: string) => joinUrl(apiBaseUrl, path)

export const buildPreviewAppUrl = (path: string) => joinUrl(appPreviewBaseUrl, path)

export const buildDeployAppUrl = (urlOrPath?: string) => {
  if (!urlOrPath) {
    return ''
  }
  const normalizedValue = urlOrPath.trim()
  if (!normalizedValue) {
    return ''
  }
  const deployUrl = isAbsoluteUrl(normalizedValue)
    ? normalizedValue
    : joinUrl(appDeployBaseUrl, normalizedValue)
  const trimmedDeployUrl = trimTrailingSlash(deployUrl)
  return trimmedDeployUrl.endsWith('/index.html') ? trimmedDeployUrl : `${trimmedDeployUrl}/index.html`
}
