# About Product

Full-stack Scala application with a shared API contract, a backend server, and a browser frontend.

## Architecture

Three Mill modules:

- **shared** -- Cross-compiled (JVM + Scala.js). Defines Tapir endpoint descriptors shared by backend and frontend for job creation and job status retrieval.
- **backend** -- JVM. Proxies requests to NuExtract, maps NuExtract responses to local HTTP statuses, and serves the compiled frontend as static files. Uses Tapir Netty Sync (direct-style) with Ox structured concurrency. Listens on port 8080.
- **frontend** -- Scala.js + Laminar UI with image upload, image preview, status updates, polling, and nutrition label rendering from extracted JSON.

## Tech Stack

| Layer     | Library / Tool                          |
|-----------|-----------------------------------------|
| Build     | Mill                                    |
| Language  | Scala 3.3.3                             |
| Backend   | Tapir (Netty Sync) + Ox                 |
| Frontend  | Laminar 17 + Scala.js 1.20.2            |
| Shared    | Tapir Core (cross-compiled JVM + JS)    |

## API Contract

Base path: `/api`

- `POST /jobs`
  - Accepts image bytes with `Content-Type` from the client.
  - Creates a NuExtract job.
  - Returns `200` with plain-text `job_id`.
  - Returns `500` for internal backend failures.
  - Returns `502` for upstream NuExtract failures.

- `GET /jobs/{id}`
  - Retrieves job status/result from NuExtract.
  - Returns `200` with JSON when extraction result is ready.
  - Returns `204` when NuExtract responds with:
    - `400` and body code `JobNotCompleted`.
  - Returns `400` for other NuExtract `400` responses.
  - Returns `500` for internal backend failures.
  - Returns `502` for other upstream NuExtract errors.

## Backend Behavior

- Requires environment variables:
  - `PROJECT_ID`
  - `API_KEY`
- Logs startup and NuExtract request/response outcomes.
- Uses `Either[(StatusCode, String), String]` endpoint handling to map response statuses explicitly.

## Frontend

- Lets the user upload an image and shows a small preview.
- On `Read`, uploads the image to `POST /api/jobs` and stores `job_id` in memory.
- Shows `"uploading ..."` while creating the job.
- Polls `GET /api/jobs/{id}` every 10 seconds.
- If `204` is returned, shows `"processing ..."` and continues polling.
- Stops polling after 5 minutes and shows `"Failed"` if no completed result arrived.
- On success, renders a nutrition label-style component from returned JSON.
  - Accepts payloads with `nutrition_facts_label` either at top-level or under `result`.
  - Reference example JSON: `docs/expected.json`.
  - Visual target: `docs/nutrilion_label.png`.

## How to Run

```bash
mill backend.run
```

Open http://localhost:8080 in a browser, upload an image, and click `Read`.
The UI flow is:

1. `uploading ...`
2. `processing ...` (while polling)
3. `Read complete` with a rendered nutrition label, or `Failed` on timeout/error.


