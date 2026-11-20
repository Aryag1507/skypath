import { timeOf, dateOf, crossesDay, formatPrice } from '../utils'

// Human label for the stop count shown in the card header badge.
function stopsLabel(stops) {
  if (stops === 0) return 'Nonstop'
  return stops === 1 ? '1 stop' : `${stops} stops`
}

// Renders one itinerary: a summary header (total time, stops, price) followed by
// each flight leg, with a layover row inserted between consecutive legs. There
// is always one fewer layover than there are segments, so layover i sits after
// segment i.
export default function ItineraryCard({ itinerary }) {
  const { segments, layovers, stops, totalDurationLabel, totalPrice } = itinerary

  return (
    <article className="itinerary">
      <header className="itinerary-header">
        <span className="duration">{totalDurationLabel}</span>
        <span className="stops-badge">{stopsLabel(stops)}</span>
        <span className="price">{formatPrice(totalPrice)}</span>
      </header>

      <div className="legs">
        {segments.map((seg, i) => (
          <div key={`${seg.flightNumber}-${i}`}>
            <div className="leg">
              <div className="leg-endpoint">
                <span className="leg-time">{timeOf(seg.departureTime)}</span>
                <span className="leg-airport">{seg.origin}</span>
              </div>

              <div className="leg-middle">
                <span className="flight-number">{seg.flightNumber} · {seg.airline}</span>
                <span className="leg-line" aria-hidden="true">→</span>
                <span className="leg-aircraft">{seg.aircraft}</span>
              </div>

              <div className="leg-endpoint leg-endpoint--arrive">
                <span className="leg-time">
                  {timeOf(seg.arrivalTime)}
                  {/* Flag legs that land on a later calendar day (overnight / date-line). */}
                  {crossesDay(seg.departureTime, seg.arrivalTime) && (
                    <sup className="overnight" title={`Arrives ${dateOf(seg.arrivalTime)}`}>+1</sup>
                  )}
                </span>
                <span className="leg-airport">{seg.destination}</span>
              </div>
            </div>

            {/* Layover shown after every leg except the last. */}
            {i < layovers.length && (
              <div className="layover">
                Layover at {layovers[i].airportCode} · {layovers[i].durationLabel}
                {layovers[i].international && <span className="intl-tag"> · International</span>}
              </div>
            )}
          </div>
        ))}
      </div>
    </article>
  )
}
