import { useState } from 'react'

const CODE_PATTERN = /^[A-Za-z]{3}$/

// Mirrors the backend's validation so users get instant, inline feedback before
// a request is ever sent. The backend remains the source of truth.
function validate({ origin, destination, date }) {
  const errors = {}
  if (!CODE_PATTERN.test(origin)) {
    errors.origin = 'Enter a 3-letter airport code'
  }
  if (!CODE_PATTERN.test(destination)) {
    errors.destination = 'Enter a 3-letter airport code'
  }
  if (!errors.origin && !errors.destination &&
      origin.toUpperCase() === destination.toUpperCase()) {
    errors.destination = 'Origin and destination must differ'
  }
  if (!date) {
    errors.date = 'Pick a date'
  }
  return errors
}

// Controlled search form. Owns its own field values and validation errors, and
// only calls onSearch (provided by App) once the input passes validation.
// `date` defaults to the single day the dataset covers. `loading` disables the
// button while a request is in flight.
export default function SearchForm({ onSearch, loading }) {
  const [origin, setOrigin] = useState('')
  const [destination, setDestination] = useState('')
  const [date, setDate] = useState('2024-03-15')
  const [errors, setErrors] = useState({})

  function handleSubmit(event) {
    event.preventDefault() // keep the form from reloading the page
    const found = validate({ origin, destination, date })
    setErrors(found)
    if (Object.keys(found).length > 0) return // stop on invalid input
    // Normalize codes to uppercase to match the dataset before searching.
    onSearch({
      origin: origin.toUpperCase(),
      destination: destination.toUpperCase(),
      date,
    })
  }

  return (
    <form className="search-form" onSubmit={handleSubmit} noValidate>
      <div className="field">
        <label htmlFor="origin">From</label>
        <input
          id="origin"
          placeholder="JFK"
          maxLength={3}
          autoComplete="off"
          value={origin}
          onChange={(e) => setOrigin(e.target.value)}
          aria-invalid={Boolean(errors.origin)}
        />
        {errors.origin && <span className="field-error">{errors.origin}</span>}
      </div>

      <div className="field">
        <label htmlFor="destination">To</label>
        <input
          id="destination"
          placeholder="LAX"
          maxLength={3}
          autoComplete="off"
          value={destination}
          onChange={(e) => setDestination(e.target.value)}
          aria-invalid={Boolean(errors.destination)}
        />
        {errors.destination && <span className="field-error">{errors.destination}</span>}
      </div>

      <div className="field">
        <label htmlFor="date">Date</label>
        <input
          id="date"
          type="date"
          value={date}
          onChange={(e) => setDate(e.target.value)}
          aria-invalid={Boolean(errors.date)}
        />
        {errors.date && <span className="field-error">{errors.date}</span>}
      </div>

      <button type="submit" disabled={loading}>
        {loading ? 'Searching…' : 'Search flights'}
      </button>
    </form>
  )
}
