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


## Shared
should contain the endpoints callable from the frontend and handled by the backend:
- POST /jobs: it should take any image, and should return a job_id
- GET /jobs/{id}: it should take an id and return status 200 with a json result, 
or status 204 if it is not ready, or 400 if the id is invalid, 
or 500 if there is an error on the backend, or 502 if there is an error on NuExtact API
- 
## Backend
- Should expect environment variables PROJECT_ID and API_KEY
- Should have logging
- When handling POST /jobs: it should call NuExtact API
it should return the job_id
- When handling GET /jobs/{id}: It should call NuExtact API, and if it receives
 httpStatus=400 with responseBody={"code":"JobNotCompleted","message":"Job has not completed yet"}
then it should return 204. 
If the httpStatus=200 then it should return the result in json format. 

## Frontend
- Should allow an image to be uploaded
- Should show a small preview of the image
- Should call POST /jobs on the backend, to upload the image, and should save the 
received job_id in memory.
- Whilst it is uploading the image to the backend, it should show message "uploading ..."
- Should poll the GET /jobs/{id} endpoint to get the status of the job. 
- If the job is not ready for example if it receives 204 status response,
it should show a message "processing ...", and should retry retrieving the status
after 10 seconds, up to a maximum of 5 minutes. 
- If the job has not been processed in the maximum time, it should show a message "Failed"
- If the job was successful, it should display the received json in a nicely formatted nutrition label component, 
- An example of the expected result is in @docs/expected.json
- The nutrition information should be shown in a similar way to @docs/nutrition_label.png

## How to Run

```bash
mill backend.run
```

Open http://localhost:8080 in a browser. Select an image, click `Read`, and the heading updates through `Uploading Image`, `Waiting For Response`, and finally `Read: <sha256-checksum>` after the backend response.


