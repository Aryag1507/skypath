import ItineraryCard from './ItineraryCard'

// Presentational switch over the request lifecycle. Given the current status it
// renders exactly one view: the initial hint, a loading spinner, the backend's
// message on error, an empty state when a valid search has no routes, or the
// ranked itinerary list on success. It holds no state of its own.
export default function Results({ status, data, error }) {
  // Before the first search: a short prompt.
  if (status === 'idle') {
    return (
      <p className="hint">
        Enter an origin, destination and date to find itineraries.
      </p>
    )
  }

  // Request in flight: spinner. aria-live announces it to screen readers.
  if (status === 'loading') {
    return (
      <div className="state" role="status" aria-live="polite">
        <span className="spinner" aria-hidden="true" />
        <span>Searching for flights…</span>
      </div>
    )
  }

  // Request failed: show the backend's message directly — it is already specific
  // (e.g. "Unknown destination airport: SSS"), so no generic alarm line is added.
  // role="alert" announces it to screen readers.
  if (status === 'error') {
    return (
      <div className="state state--error" role="alert">
        <span>{error}</span>
      </div>
    )
  }

  // Valid search, but no itineraries within the rules: empty state.
  if (status === 'success' && data.resultCount === 0) {
    return (
      <div className="state">
        <strong>No itineraries found.</strong>
        <span>
          No routes from {data.origin} to {data.destination} on {data.date}.
          Try a different date or airports.
        </span>
      </div>
    )
  }

  // Success with results: a summary line plus the ranked itinerary cards.
  return (
    <section className="results">
      <p className="results-count">
        {data.resultCount} {data.resultCount === 1 ? 'itinerary' : 'itineraries'} ·{' '}
        {data.origin} → {data.destination} · {data.date} · sorted by travel time
      </p>
      {data.itineraries.map((itinerary, i) => (
        <ItineraryCard key={i} itinerary={itinerary} />
      ))}
    </section>
  )
}
