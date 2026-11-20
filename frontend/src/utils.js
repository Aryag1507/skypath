// Formatting helpers. Times from the API are ISO strings carrying each airport's
// local offset (e.g. "2024-03-15T19:30:00-04:00"); we deliberately display the
// wall-clock time as given rather than converting to the viewer's time zone.

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
  'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

export function timeOf(isoString) {
  return isoString.slice(11, 16) // "HH:mm"
}

export function dateOf(isoString) {
  const [year, month, day] = isoString.slice(0, 10).split('-').map(Number)
  return `${MONTHS[month - 1]} ${day}, ${year}`
}

// Whether two ISO strings fall on different calendar days (to flag overnight legs).
export function crossesDay(startIso, endIso) {
  return startIso.slice(0, 10) !== endIso.slice(0, 10)
}

export function formatPrice(amount) {
  return `$${Number(amount).toFixed(2)}`
}
