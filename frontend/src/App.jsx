import { useState } from 'react'
import SearchForm from './components/SearchForm'
import Results from './components/Results'
import { searchFlights } from './api'
import './App.css'

// Root component and the single owner of request state. SearchForm raises a
// query, App runs it and tracks the lifecycle, and Results renders the outcome.
// Keeping the state here (rather than in the form or results) means both child
// components stay presentational and easy to reason about.
//
// status: 'idle' | 'loading' | 'success' | 'error'
export default function App() {
  const [status, setStatus] = useState('idle')
  const [data, setData] = useState(null)   // last successful SearchResponse
  const [error, setError] = useState(null) // last error message, if any

  // Runs a search and moves the UI through loading -> success/error.
  async function handleSearch(query) {
    setStatus('loading')
    setError(null)
    try {
      const result = await searchFlights(query)
      setData(result)
      setStatus('success')
    } catch (err) {
      setError(err.message)
      setStatus('error')
    }
  }

  return (
    <div className="app">
      <header className="masthead">
        <h1>SkyPath</h1>
        <p>Flight connection search</p>
      </header>

      <SearchForm onSearch={handleSearch} loading={status === 'loading'} />
      <Results status={status} data={data} error={error} />
    </div>
  )
}
