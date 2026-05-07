# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# AI Technical Guide: AboutProduct

**System Instruction:** You are an expert AI software engineer assisting with the "AboutProduct" project. This document is your foundational context. Treat the instructions and architectural constraints below as strict invariants unless explicitly overridden by the user.

After making changes:
- validate that the tests pass. 
- update the specs in @docs/ai.md, but do not change the instructions before "Project Overview".
- ensure that the application is left running, by execution of run.sh, to allow for human to verify the changes.

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
    7. **Ingredients Display:** An ingredients list is rendered below the nutritional breakdown. If the `ingredients` array is missing or empty in the source JSON, it displays "unknown".
    8. **Timeout:** Individual jobs stop polling after 5 mins and show "Failed" in History.

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

1. **General Style:** Prefer **direct style** Scala using Ox. Adhere to Scala 3 syntax (significant indentation). Use `enum` for ADTs. Replace `implicit` with `using`/`given`/`extension`. Do not use `_` for wildcards, use `?`.
2. **Backend Concurrency:** Stick exclusively to Ox's `scoped`, `fork`, and direct-style blocking IO. See `docs/rules/backend.mdc`.
3. **Frontend Reactivity:** Build Laminar components using `HtmlElement`. Mutate state explicitly via `.amend` and reactive bindings. Avoid raw DOM mutation outside Laminar abstractions. See `docs/rules/frontend.mdc`.
4. **Data Handling:** Use `ujson` for lightweight JSON manipulation when strict structural types aren't necessary.
5. **No Redundancy:** Always maintain a single source of truth for the API contract within the `shared` module.

## 5. Development Workflow

### Core Commands (Mill)
- **Compile all:** `mill _.compile`
- **Run Backend:** `mill backend.run` (or `./run.sh` to include `.env` variables)
- **Run Tests:**
  - Backend: `mill backend.test`
  - Frontend: `mill frontend.test`
  - Single test: `mill backend.test backend.test.MyTestSpec`
- **Clean build:** `mill clean`
- **Link Scala.js:** `mill frontend.fastLinkJS`

### Environment & Testing
- **Env Vars:** Requires `PROJECT_ID` and `API_KEY` (source from `docs/.env` via `./run.sh`).
- **Testing Protocol:** After making changes, test the backend, and then test the end-to-end flow from the frontend.
- **Specification Maintenance:** Update this document (or relevant `.md` files in `docs/`) after every code change that alters system behavior or architecture.

## 6. Current System Behavior

### History Drawer & Comparison Mode
- **State Tracking:** Tracks `currentResultId` (primary), `comparisonResultId` (comparison), and `activeJobIds` (background).
- **History Items:** Statuses are `completed`, `processing`, or `failed`.
- **View/Compare Logic:** 
    - "View" is disabled if not completed or already in primary slot (unless in comparison mode).
    - "Compare" is disabled if not completed, no item is currently viewed, or item is already in a slot.
    - Clicking "View" during comparison clears the comparison state.

### 7. Layout & Responsiveness
- **Breakpoints:** Uses Tailwind CSS breakpoints via CDN.
- **Side-by-Side View:** The image preview (or first comparison card) and the nutrition facts result card transition from a vertical stack (`flex-col`) to a side-by-side layout (`flex-row`) at the **medium (`md:`) breakpoint (768px)**.
- **Comparison Mode:** In comparison mode, the image is hidden, and two nutrition facts cards are shown side-by-side from the `md` breakpoint upwards.
