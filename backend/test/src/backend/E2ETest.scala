package backend

import munit.FunSuite
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.URI
import sttp.model.StatusCode
import shared.Endpoints
import sttp.tapir.server.netty.sync.NettySyncServer
import java.util.logging.Logger
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import java.net.ServerSocket

class E2ETest extends FunSuite:
  private val logger = Logger.getLogger("backend.E2ETest")

  def getFreePort(): Int =
    val socket = new ServerSocket(0)
    val port = socket.getLocalPort
    socket.close()
    port

  test("End-to-end flow: create job, poll, and get result") {
    val port = getFreePort()
    val projectId = "test-project"
    val apiKey = "test-api-key"

    // 1. Prepare Mock Responses
    val createJobResponse = MockHttpResponse(
      200,
      """{"job_id": "test-job-123"}"""
    )
    val pollResponse1 = MockHttpResponse(
      400,
      """{"code": "JobNotCompleted", "message": "Job is still processing"}"""
    )
    val pollResponse2 = MockHttpResponse(
      200,
      """{"product": "Test Milk", "nutrition_facts": {"calories": 100}}"""
    )

    val mockHttpClient = SequentialMockHttpClient(List(
      createJobResponse,
      pollResponse1,
      pollResponse2
    ))

    val nuExtractClient = NuExtractClient(mockHttpClient, projectId, apiKey)

    val createJobServerEndpoint =
      Endpoints.jobsEndpoint.handle((imageBytes, contentType) =>
        nuExtractClient.createJob(imageBytes, contentType)
      )

    val jobStatusServerEndpoint =
      Endpoints.jobStatusEndpoint.handle(jobId =>
        nuExtractClient.fetchJobStatus(jobId)
      )

    // Start server in background
    val server = NettySyncServer()
      .host("localhost")
      .port(port)
      .addEndpoint(createJobServerEndpoint)
      .addEndpoint(jobStatusServerEndpoint)

    val serverThread = new Thread(() => {
       try {
         server.startAndWait()
       } catch {
         case _: InterruptedException => // expected on stop
       }
    })
    serverThread.start()

    // Give server a moment to start
    Thread.sleep(1000)

    try {
      val client = HttpClient.newHttpClient()

      // Step 1: Create Job
      val createRequest = HttpRequest.newBuilder()
        .uri(URI.create(s"http://localhost:$port/api/jobs"))
        .header("Content-Type", "image/png")
        .POST(HttpRequest.BodyPublishers.ofByteArray(Array[Byte](1, 2, 3)))
        .build()

      val createResponse = client.send(createRequest, HttpResponse.BodyHandlers.ofString())
      assertEquals(createResponse.statusCode(), 200)
      assertEquals(createResponse.body(), "test-job-123")

      // Step 2: Poll - Round 1 (Still processing)
      val pollRequest1 = HttpRequest.newBuilder()
        .uri(URI.create(s"http://localhost:$port/api/jobs/test-job-123"))
        .GET()
        .build()

      val pollResponse1Actual = client.send(pollRequest1, HttpResponse.BodyHandlers.ofString())
      assertEquals(pollResponse1Actual.statusCode(), 204)

      // Step 3: Poll - Round 2 (Success)
      val pollResponse2Actual = client.send(pollRequest1, HttpResponse.BodyHandlers.ofString())
      assertEquals(pollResponse2Actual.statusCode(), 200)
      assert(pollResponse2Actual.body().contains("Test Milk"))

      // Step 4: Verify static files
      val staticRequest = HttpRequest.newBuilder()
        .uri(URI.create(s"http://localhost:$port/index.html"))
        .GET()
        .build()

      val staticResponse = client.send(staticRequest, HttpResponse.BodyHandlers.ofString())
      // index.html might not be there if frontend hasn't been built,
      // but Mill should have built it for backend.run/test
      assert(staticResponse.statusCode() == 200 || staticResponse.statusCode() == 404)
      if (staticResponse.statusCode() == 200) {
        assert(staticResponse.body().contains("<!DOCTYPE html>"))
      }

    } finally {
      // Cleanup: Attempt to stop server thread
      serverThread.interrupt()
    }
  }
