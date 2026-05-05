# AI Technical Guide: AboutProduct

**System Instruction:** You are an expert AI software engineer assisting with the "AboutProduct" project. This document is your foundational context. Treat the instructions and architectural constraints below as strict invariants unless explicitly overridden by the user.

## General Guidelines
- Prefer **direct style** Scala using Ox for concurrency.
- Adhere to Scala 3 syntax (significant indentation, `enum`, `extension`, etc.).
- Ensure types are explicit for public APIs.
- Follow functional programming principles where appropriate, but favor readability and the "Direct Style" ecosystem.
- Use Mill for all build and task management.
- **Testing Protocol:** After making changes, test the backend, and then test the flow from the frontend.

## 1. Project Overview

**AboutProduct** is a full-stack Scala web application that extracts information (like nutrition facts) from a photo of a product label. It proxies requests to the NuExtract API for data extraction, handles asynchronous job polling, and renders a visual nutrition facts label from the extracted JSON.

## 2. Architecture & Modules

The project is built using the **Mill** build tool and consists of three modules:

### `shared` (Cross-compiled: JVM + Scala.js)
- **Role:** Defines the exact Tapir API contracts shared by the client and server.
- **Rules:** Must contain zero platform-specific logic. Only use Tapir Core and cross-platform Scala 3.

### `backend` (JVM)
- **Role:** The API server, NuExtract API proxy, and static file server for the frontend.
- **Tech Stack:** Scala 3.3.3, Tapir (Netty Sync), Ox.
- **Concurrency Model:** Uses **direct-style** concurrency via Ox. *Do not use Cats-Effect, ZIO, Monix, or Future-based async programming.*
- **Key Behaviors:**
  - Exposes port 8080.
  - Required Env Vars: `PROJECT_ID`, `API_KEY`.
  - HTTP errors map explicitly using `Either[(StatusCode, String), String]`.
  - During the build (`mill backend.run`), it triggers the frontend compilation (`fastLinkJS`), copies the output JS and HTML into its resources, and serves them via Tapir's `tapir-files`.

### `frontend` (Scala.js)
- **Role:** Single-page application (SPA) allowing users to upload images, poll the backend, and view the rendered result.
- **Tech Stack:** Scala.js 1.20.2, Laminar 17.
- **Key Behaviors:**
  - **Reactivity:** Uses Laminar's `Var`, `Signal`, and `EventStream`. *Do not suggest React or generic JS frameworks.*
  - **State Machine:**
    1. **Upload:** Supports single or multiple image selection.
    2. **Primary Item:** The first selected image is previewed immediately and its job is tracked as the "main" operation.
    3. **Background Processing:** Additional images are uploaded and processed in parallel.
    4. **Polling:** Each `job_id` is polled independently every 10s.
    5. **History Integration:** Background jobs are immediately added to History with a "Processing" status.
    6. **Completion:** Upon success, History items are updated with extracted data; for the primary item, the main UI also updates to show the result.
    7. **Timeout:** Individual jobs stop polling after 5 mins and show "Failed" in History.

## 3. API Contract (`/api`)

- **`POST /jobs`**
  - Accepts raw image bytes.
  - Returns `200` with the plain-text `job_id`.
  - Returns `500` for backend failures or `502` for upstream API failures.
- **`GET /jobs/{id}`**
  - Polls NuExtract.
  - `200`: Success (returns extracted JSON).
  - `204`: Still processing (NuExtract returned `400` with code `JobNotCompleted`).
  - `400/500/502`: Varied upstream or backend errors.

## 4. AI Coding Guidelines

*When writing or modifying code in this repository, strictly adhere to the following:*

1. **Scala 3 Standards:** Use significant indentation (braceless syntax). Replace `implicit` with `using`/`given`/`extension`. Use `enum` for ADTs. Do not use `_` for wildcards, use `?`. See `docs/rules/scala.mdc` for details.
2. **Backend Concurrency:** Stick exclusively to Ox's `scoped`, `fork`, and direct-style blocking IO. See `docs/rules/backend.mdc`.
3. **Frontend Reactivity:** Build Laminar components using `HtmlElement`. Mutate state explicitly via `.amend` and reactive bindings. Avoid raw DOM mutation outside Laminar abstractions. See `docs/rules/frontend.mdc`.
4. **Data Handling:** Use `ujson` for lightweight JSON manipulation when strict structural types aren't necessary.
5. **No Redundancy:** Always maintain a single source of truth for the API contract within the `shared` module.

## 5. Development Workflow

- **Build/Run command:** `mill backend.run`
- To run the application successfully, you must ensure `.env` is populated with `PROJECT_ID` and `API_KEY` (use `docs/.env.template` as a reference).
- **Specification Maintenance:** You MUST update this document (or relevant `.md` files in `docs/`) after every code change that alters system behavior or architecture to ensure the "source of truth" remains accurate.

## 6. Current System Behavior

### History Drawer & Comparison Mode
- **State Tracking:** The system tracks the `currentResultId` (primary slot), `comparisonResultId` (comparison slot), and `activeJobIds` (set of ongoing background jobs).
- **History Items:** Each item in history has a `status` (`completed`, `processing`, or `failed`).
- **View Button Logic:** 
    - Disabled if the item is not `completed`.
    - Disabled if the item is already in the primary slot AND no comparison is active.
    - Enabled during comparison mode to allow users to "exit" comparison by selecting a single item to view.
- **Compare Button Logic:**
    - Disabled if the item is not `completed`.
    - Disabled if no item is currently viewed.
    - Disabled if the item is already in the primary slot.
    - Disabled if the item is already in the comparison slot.
- **Transition Logic:** Clicking "View" on any item while in comparison mode clears the comparison state and resets the UI to single-view mode for that item.
