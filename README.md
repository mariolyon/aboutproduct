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
- Java (JDK 17 or later recommended)
- [Mill Build Tool](https://mill-build.com/)
- NuExtract API Credentials (set as environment variables or in a `.env` file)

## How to Run

1. **Set up Environment Variables:**
   You will need to provide the credentials for the upstream NuExtract API.
   Copy the provided template and fill in your keys:
   ```bash
   cp docs/.env.template docs/.env
   # Edit docs/.env with your PROJECT_ID and API_KEY
   ```

2. **Start the Application:**
   You can use the provided run script which automatically sources the `.env` file and starts the server:
   ```bash
   ./run.sh
   ```
   *Alternatively, you can run `export $(cat docs/.env | xargs) && mill backend.run` manually.*

3. **Open the Application:**
   Navigate to [http://localhost:8080](http://localhost:8080) in your web browser. 

4. **Usage:**
   - Upload an image of a product label.
   - The UI will show "uploading..." and then "processing..." while it polls for results.
   - Once complete, it displays the extracted data as a formatted nutrition label.

## Project Architecture
- **`shared/`**: Contains Tapir endpoint descriptors. Cross-compiled for both JVM and Scala.js to ensure the backend and frontend agree on the API contract.
- **`backend/`**: JVM module. Serves the static compiled frontend files, proxies extraction requests to the NuExtract API, and manages job statuses.
- **`frontend/`**: Scala.js module. A Laminar-based single-page application handling image uploads, preview, polling, and data rendering.
