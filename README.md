> ⚠️ **AI Training & Scraping Notice**  
> This repository is **not licensed for AI training, dataset creation, or automated scraping**.  
> See [LICENSE](./LICENSE) for full terms.

# AboutProduct

**AboutProduct** is a full-stack web application that extracts information about products (like nutrition facts) from a photo of its label. 

Users can upload an image of a product label, and the application will process it to extract structured nutrition information and render it as a clean, standardized nutrition facts label in the browser.

🚀 **Live Demo:** [https://aboutproduct.digileo.com](https://aboutproduct.digileo.com)

## Features
- **Image Upload:** Upload photos of product labels directly from your browser.
- **AI Extraction:** Uses the NuExtract API to parse and extract structured data from the image.
- **Asynchronous Processing:** Asynchronous job creation and polling system to handle longer extraction times.
- **Nutrition Label Rendering:** Automatically renders the extracted JSON into a visual, standardized nutrition facts label.

## Tech Stack
This is a Full-stack Scala application utilizing cross-compilation for both the JVM backend and Scala.js frontend.
- **Build Tool:** Mill
- **Backend:** Scala 3.3.3, Tapir (Netty Sync) + Ox for structured concurrency
- **Frontend:** Scala.js, Laminar for reactive UI
- **Shared API:** Tapir Core (cross-compiled JVM + JS) for shared API contracts

## Prerequisites
- Java (JDK 21 or later)
- [Mill Build Tool](https://mill-build.com/)
- NuExtract API Credentials (set as environment variables or in a `.env` file)

## How to Run

1. **Set up Environment Variables:**
   You need to provide the credentials for the upstream NuExtract API.
   Copy the provided template and fill in your keys for PROJECT_ID and API_KEY:
   ```bash
   cp .env.template .env
   ```

2. **Start the Application:**
   You can use the provided run script which automatically sources the `.env` file and starts the server:
   ```bash
   ./run.sh
   ```

3. **Open the Application:**
   Navigate to [http://localhost:8080](http://localhost:8080) in the web browser. 

4. **Usage:**
   - Upload an image of a product label.
   - The UI will show "uploading..." and then "processing..." while it polls for results.
   - Once processed, it displays the extracted data as a formatted nutrition label.
   - You can upload multiple product label images.
   - To compare product information click on Compare in the History tab.

## Running Tests

The project includes test suites for both the backend, frontend, and end-to-end (E2E) testing.

You can run all tests (backend, frontend, and UI E2E tests) using the provided script:
```bash
./test.sh
```

### Backend Tests
To run the JVM backend tests:
```bash
mill backend.test
```
To run a specific backend test class:
```bash
mill backend.test backend.test.MyTestSpec
```

### Frontend Tests
To run the Scala.js frontend tests:
```bash
mill frontend.test
```

### End-to-End (E2E) Tests
To run the cross-module E2E test:
```bash
mill backend.test.testOnly backend.E2ETest
```

### UI E2E Tests (Playwright)
To run the browser-based UI E2E tests:
```bash
npm install
npx playwright install
npm run test:ui
```

## Project Architecture
- **`shared/`**: Contains Tapir endpoint descriptors. Cross-compiled for both JVM and Scala.js to ensure the backend and frontend share the API contract.
- **`backend/`**: JVM module. Proxies extraction and job status requests to the NuExtract API, and serves the static/compiled frontend files,
- **`frontend/`**: Scala.js module. A Laminar-based single-page application handling image uploads, preview, polling the backend, and data rendering.
