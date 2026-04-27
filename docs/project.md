# About Product

Full-stack Scala application with a shared API contract, a backend server, and a browser frontend.

## Architecture

Three Mill modules:

- **shared** -- Cross-compiled (JVM + Scala.js). Defines Tapir endpoint descriptors used by both backend and frontend. Currently exposes a single `GET /api/greet?name=<string>` endpoint that accepts a name and returns a greeting string.
- **backend** -- JVM. Implements server logic for the shared endpoints and serves the compiled frontend as static files. Uses Tapir Netty Sync (direct-style) with Ox structured concurrency. Listens on port 8080.
- **frontend** -- Scala.js. Laminar-based UI that initially displays "Hello World", provides a text input for a name, and on submit calls the backend greet endpoint. The greeting concatenation happens entirely on the backend; the frontend displays the response as-is.

## Tech Stack

| Layer     | Library / Tool                          |
|-----------|-----------------------------------------|
| Build     | Mill                                    |
| Language  | Scala 3.3.3                             |
| Backend   | Tapir (Netty Sync) + Ox                 |
| Frontend  | Laminar 17 + Scala.js 1.16              |
| Shared    | Tapir Core (cross-compiled JVM + JS)    |

## How to Run

```bash
mill backend.run
```

Open http://localhost:8080 in a browser. Type a name, click Submit (or press Enter), and the heading updates with the greeting returned by the backend.
