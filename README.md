# SkyPath — Flight Connection Search

A prototype flight search engine. Given a dataset of flight schedules, it finds
valid itineraries between two airports — direct, 1-stop, and 2-stop — honoring
real-world connection rules (minimum/maximum layovers, domestic vs.
international, no airport changes) and correct timezone math, then ranks results
by total travel time.

**Stack:** Java 21 · Spring Boot 3.3 (backend) · React 19 · Vite (frontend) ·
Docker / docker-compose.

---

## Quick start

Prerequisite: Docker running (Docker Desktop, Colima, etc.).

```bash
git clone https://github.com/Aryag1507/skypath
cd skypath
docker-compose up --build
```

Then open **http://localhost:5173**.

- The frontend (nginx) serves the UI and proxies `/api` to the backend, so the
  whole app is reachable from that one URL.
- The first build takes a few minutes (it downloads Maven/npm dependencies);
  subsequent runs start in seconds.
- `--build` is included so a stale image is never reused. Plain
  `docker-compose up` also works on a fresh clone.
- If ports `5173` or `8080` are already in use on your machine, change the
  host-side port in [`docker-compose.yml`](docker-compose.yml) (e.g.
  `"5174:80"`) and open that port instead. `5173` is the default used
  throughout this README.

Stop it with `Ctrl+C`, or `docker-compose down` from another terminal.

### Things to try

The dataset covers **2024-03-15** (pre-filled in the form).

| Search | What it shows |
|--------|---------------|
| `JFK → LAX` | Direct flights and multi-stop connections |
| `BOS → SEA` | No direct flight — connections only |
| `SFO → NRT` | International route (90-min minimum layover applies) |
| `SYD → LAX` | Date-line crossing — arrival looks "before" departure in local time |
| `JFK → SIN` | Valid airports but no route — empty state |
| `JFK → JFK` / `ZZZ → LAX` | Validation / graceful error handling |

---

## Running without Docker

**Backend** (requires JDK 21 + Maven):

```bash
cd backend
mvn spring-boot:run        # serves http://localhost:8080
```

It loads `../flights.json` by default (override with the
`SKYPATH_DATA_LOCATION` env var, which accepts `file:` or `classpath:` paths).

**Frontend** (requires Node 20):

```bash
cd frontend
npm install
npm run dev                # serves http://localhost:5173, proxies /api -> :8080
```

**Tests** (backend):

```bash
cd backend
mvn test
```

---

## API

A single endpoint:

```
GET /api/search?origin=JFK&destination=LAX&date=2024-03-15
```

- `origin`, `destination` — 3-letter IATA codes (case-insensitive)
- `date` — ISO `YYYY-MM-DD`; constrains the **first leg's** departure date

**Success (`200`)** — results sorted by total travel time, shortest first:

```json
{
  "origin": "JFK",
  "destination": "LAX",
  "date": "2024-03-15",
  "resultCount": 27,
  "itineraries": [
    {
      "stops": 1,
      "totalDurationMinutes": 495,
      "totalDurationLabel": "8h 15m",
      "totalPrice": 428.00,
      "segments": [
        {
          "flightNumber": "SP141",
          "airline": "SkyPath Airways",
          "origin": "JFK",
          "destination": "DFW",
          "departureTime": "2024-03-15T15:00:00-04:00",
          "arrivalTime": "2024-03-15T17:45:00-05:00",
          "durationMinutes": 225,
          "price": 259.00,
          "aircraft": "B737"
        },
        {
          "flightNumber": "SP152",
          "airline": "SkyPath Airways",
          "origin": "DFW",
          "destination": "LAX",
          "departureTime": "2024-03-15T19:00:00-05:00",
          "arrivalTime": "2024-03-15T20:15:00-07:00",
          "durationMinutes": 195,
          "price": 169.00,
          "aircraft": "A320"
        }
      ],
      "layovers": [
        { "airportCode": "DFW", "durationMinutes": 75, "durationLabel": "1h 15m", "international": false }
      ]
    }
  ]
}
```

**Error (`400`)** — invalid input (unknown/malformed code, same origin and
destination, bad date, missing parameter):

```json
{ "status": 400, "error": "Bad Request", "message": "Unknown origin airport: ZZZ" }
```

A valid search with no routes returns `200` with an empty `itineraries` list
(not an error).

---

## Architecture & key decisions

### The search: a bounded depth-first search

The schedule is modeled as a **directed graph** — airports are nodes, flights
are time-stamped edges — and a trip is a path through it. Because the spec caps
connections at **two stops (three legs)**, the search is a **depth-limited DFS**
from the origin that collects every complete path to the destination, skipping
any branch that breaks a connection rule or would revisit an airport.

I deliberately did **not** use a weighted shortest-path algorithm like
Dijkstra:

- We need **all** valid itineraries, not the single best one.
- Edge validity is **time-dependent** (whether you can take the next flight
  depends on when the previous one landed and the layover window) rather than a
  simple additive weight.
- The two-stop cap makes the search space tiny and bounded, so the cleverness
  of Dijkstra buys nothing here.

The result: a simpler, obviously-correct algorithm that's easy to reason about.
Results are sorted by total travel time after the search
([`ItinerarySearchService`](backend/src/main/java/com/skypath/search/ItinerarySearchService.java)),
keeping "what's valid" (search + rules) separate from "what's best" (ranking).

### Timezones: convert once, when data is loaded

Every time in the dataset is local wall-clock time, so durations can't be
computed from the raw strings. The loader resolves each flight's departure
against its **origin** airport's zone and its arrival against its
**destination** airport's zone, producing absolute `ZonedDateTime` instants
([`FlightDataLoader`](backend/src/main/java/com/skypath/data/FlightDataLoader.java)).

This happens exactly once, at load time. Nothing downstream ever sees a naive
timestamp, so layover and total-duration math is correct for free — including
date-line cases like `SYD → LAX`, where the arrival's local time appears earlier
than the departure's but the UTC duration is a sensible ~15 hours.

### Connection rules: a pure, isolated component

The rules live in one dependency-light class
([`ConnectionRules`](backend/src/main/java/com/skypath/search/ConnectionRules.java))
with the thresholds as named constants mirroring the spec (45 min domestic /
90 min international minimum, both inclusive; 6 hour maximum).

### Layering

```
api     → REST controller, request validation, response DTOs, error handling
search  → DFS search + the connection-rules engine
data    → loads flights.json, builds the in-memory indexes, input DTOs
domain  → immutable records: Airport, Flight, Layover, Itinerary
```

- **Domain is modeled with immutable `record`s** using rich types (`ZoneId`,
  `ZonedDateTime`, `BigDecimal` for money) so timezone- and
  precision-correctness are structural, not conventions.
- **DTOs sit at both edges** — input DTOs absorb the JSON file's shape; response
  DTOs present UI-friendly values (ISO times, durations in minutes + labels) —
  so the domain model and the public API can evolve independently.
- **The dataset is loaded once into memory** and indexed as an adjacency list
  keyed by origin airport (the graph) plus an airport lookup by code. It's a
  small, read-only dataset, so this is both sufficient and simple.

### Frontend

A thin React app: a validated search form, a results list, and the four
required states (idle, loading, empty, error). It calls a **relative** `/api`,
which Vite proxies in development and nginx proxies in Docker — so the client
has no hard-coded backend host. Times are shown as each airport's local
wall-clock (as a traveler expects), with a `+1` marker on overnight legs.

---

## Assumptions

- **"date" constrains only the first leg's departure** (in the origin's local
  date). Onward connections may legitimately cross midnight within the 6-hour
  layover limit.
- **Domestic means every airport across both connecting flights shares one
  country** — the faithful reading of "both flights within the same country."
  This correctly treats e.g. `US → CA → US` as *international* (a naive check of
  just the first and last airport would not).
- **Unknown country → treated as international** (the stricter 90-minute rule),
  on a "when unsure, demand the safer layover" basis.
- **No result cap / pagination.** The dataset is small and capped at two stops,
  so every valid itinerary is returned; capping could hide a valid
  fastest/cheapest option. (See *What I'd improve*.)
- **Malformed records are skipped, not fatal.** The real `flights.json` contains
  one flight with a typo'd origin (`JKF` instead of `JFK`); it's logged and
  skipped, so 302 of 303 flights load and the service still starts cleanly.

---

## Testing

37 tests, focused on the correctness-critical areas:

- **Connection rules** — boundary tests for the layover thresholds (44 invalid /
  45 valid, 89 / 90, 6h00m valid / 6h01m invalid), domestic vs. international
  classification, same-airport and chronology checks.
- **Search** — unit tests over small hand-built graphs (direct, ranking,
  short/long-layover rejection, two-stop cap, no-revisit, date filter), plus
  integration tests against the real dataset.
- **API** — MockMvc tests for a valid search and each error case.

The six official scenarios are covered end to end across the search integration
tests (cases 1, 2, 3, 6) and the controller tests (cases 4, 5).

---

## Tradeoffs

- **In-memory data, no database.** Simple and fast for a read-only dataset of
  this size; wouldn't scale to a live, mutable schedule.
- **Tests skipped in the Docker image build.** They run locally and in CI; the
  image build stays fast and deterministic.
- **Dataset mounted, not baked into the image.** Keeps one source of truth at
  the repo root at the cost of a volume mount in compose.
- **Unpaginated results** (see Assumptions / below).
- **No auth, rate limiting, or caching** — out of scope for a prototype.

---

## What I'd improve with more time

- **Pagination / result limiting** with a `totalCount`, for production-scale
  result sets.
- **Caching** of hot origin/destination/date queries.
- **Richer filtering and sorting** in the UI (by price, stops, airline;
  departure-time windows).
- **More connection nuance** — per-airport minimum connection times, overnight
  airport-closure handling, airline/alliance constraints.
- **A database and an ingestion pipeline** for a real, updating schedule rather
  than a static file.
- **Observability and CI** — metrics, structured request logging, a test/build
  pipeline, and a deployment target.

---

## Project structure

```
skypath/
├── backend/                 # Spring Boot service (Java 21)
│   ├── src/main/java/com/skypath/
│   │   ├── domain/          # Airport, Flight, Layover, Itinerary (records)
│   │   ├── data/            # loader, in-memory repository/index, input DTOs
│   │   ├── search/          # ConnectionRules + ItinerarySearchService (DFS)
│   │   └── api/             # controller, response DTOs, error handling
│   ├── src/test/            # unit + integration tests
│   └── Dockerfile
├── frontend/                # React + Vite app
│   ├── src/
│   │   ├── components/      # SearchForm, Results, ItineraryCard
│   │   ├── api.js, utils.js
│   │   └── App.jsx
│   ├── nginx.conf           # serves the SPA, proxies /api -> backend
│   └── Dockerfile
├── docker-compose.yml       # one-command startup
└── flights.json             # the dataset (single source of truth)
```
