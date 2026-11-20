// Thin client for the SkyPath search API. Returns the parsed payload on success
// and throws an Error carrying the backend's message on failure, so the UI can
// surface a clear reason.

export async function searchFlights({ origin, destination, date }) {
  const params = new URLSearchParams({ origin, destination, date })
  let response
  try {
    response = await fetch(`/api/search?${params.toString()}`)
  } catch {
    throw new Error('Could not reach the server. Is the backend running?')
  }

  let body = null
  try {
    body = await response.json()
  } catch {
    // non-JSON response; fall through to generic handling below
  }

  if (!response.ok) {
    const message = body?.message || `Request failed (${response.status})`
    throw new Error(message)
  }
  return body
}
