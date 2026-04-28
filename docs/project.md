# About Product

Full-stack Scala application with a shared API contract, a backend server, and a browser frontend.

## Architecture

Three Mill modules:

- **shared** -- Cross-compiled (JVM + Scala.js). Defines Tapir endpoint descriptors used by both backend and frontend. Exposes a `POST /api/read` endpoint that accepts uploaded image bytes and returns a checksum string.
- **backend** -- JVM. Implements server logic for the shared endpoint, waits 10 seconds, computes a SHA-256 checksum for the uploaded image, and returns it. Also serves the compiled frontend as static files. Uses Tapir Netty Sync (direct-style) with Ox structured concurrency. Listens on port 8080.
- **frontend** -- Scala.js. Laminar-based UI with an image file input and a `Read` button. On read, it posts the selected image to the backend and shows status transitions: `Uploading Image`, `Waiting For Response`, then `Read: <checksum>`.

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

Open http://localhost:8080 in a browser. Select an image, click `Read`, and the heading updates through `Uploading Image`, `Waiting For Response`, and finally `Read: <sha256-checksum>` after the backend response.
