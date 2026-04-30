package backend

import munit.FunSuite
import sttp.model.StatusCode

class NuExtractClientTest extends FunSuite:
  val projectId = "test-project-id"
  val apiKey = "test-api-key"

  test("createJob sends correct request and returns Right with job ID on success") {
    val mockHttpClient = new MockHttpClient()
    mockHttpClient.responseToReturn = new MockHttpResponse(200, """{"id": "job-123"}""")
    
    val client = new NuExtractClient(mockHttpClient, projectId, apiKey)
    val result = client.createJob("image-data".getBytes, "image/png")
    
    assertEquals(result, Right("job-123"))
    
    val request = mockHttpClient.lastRequest
    assertEquals(request.uri().toString, s"https://nuextract.ai/api/structured-extraction/$projectId/jobs")
    assertEquals(request.headers().firstValue("Authorization").get(), s"Bearer $apiKey")
    assertEquals(request.headers().firstValue("Content-Type").get(), "image/png")
    assertEquals(request.method(), "POST")
  }

  test("createJob returns Left with BadGateway when status is not 2xx") {
    val mockHttpClient = new MockHttpClient()
    mockHttpClient.responseToReturn = new MockHttpResponse(500, "Internal Server Error")
    
    val client = new NuExtractClient(mockHttpClient, projectId, apiKey)
    val result = client.createJob("image-data".getBytes, "image/png")
    
    assert(result.isLeft)
    assertEquals(result.left.toOption.get._1, StatusCode.BadGateway)
    assert(result.left.toOption.get._2.contains("NuExtract create-job failed with HTTP 500: Internal Server Error"))
  }

  test("createJob returns Left with BadGateway when response json is invalid") {
    val mockHttpClient = new MockHttpClient()
    mockHttpClient.responseToReturn = new MockHttpResponse(200, "invalid-json")
    
    val client = new NuExtractClient(mockHttpClient, projectId, apiKey)
    val result = client.createJob("image-data".getBytes, "image/png")
    
    assert(result.isLeft)
    assertEquals(result.left.toOption.get._1, StatusCode.BadGateway)
  }

  test("createJob returns Left with InternalServerError when exception is thrown") {
    val mockHttpClient = new MockHttpClient()
    mockHttpClient.exceptionToThrow = new RuntimeException("Network error")
    
    val client = new NuExtractClient(mockHttpClient, projectId, apiKey)
    val result = client.createJob("image-data".getBytes, "image/png")
    
    assert(result.isLeft)
    assertEquals(result.left.toOption.get._1, StatusCode.InternalServerError)
    assert(result.left.toOption.get._2.contains("Network error"))
  }

  test("fetchJobStatus returns Right with response body when status is 200") {
    val mockHttpClient = new MockHttpClient()
    val responseJson = """{"status": "success", "result": "..."}"""
    mockHttpClient.responseToReturn = new MockHttpResponse(200, responseJson)
    
    val client = new NuExtractClient(mockHttpClient, projectId, apiKey)
    val result = client.fetchJobStatus("job-123")
    
    assertEquals(result, Right(responseJson))
    
    val request = mockHttpClient.lastRequest
    assertEquals(request.uri().toString, "https://nuextract.ai/api/structured-extraction/jobs/job-123")
    assertEquals(request.headers().firstValue("Authorization").get(), s"Bearer $apiKey")
    assertEquals(request.method(), "GET")
  }

  test("fetchJobStatus returns Left with NoContent when status is 400 and JobNotCompleted") {
    val mockHttpClient = new MockHttpClient()
    val responseJson = """{"code": "JobNotCompleted", "message": "Pending"}"""
    mockHttpClient.responseToReturn = new MockHttpResponse(400, responseJson)
    
    val client = new NuExtractClient(mockHttpClient, projectId, apiKey)
    val result = client.fetchJobStatus("job-123")
    
    assertEquals(result, Left((StatusCode.NoContent, "")))
  }

  test("fetchJobStatus returns Left with BadRequest when status is 400 but not JobNotCompleted") {
    val mockHttpClient = new MockHttpClient()
    val responseJson = """{"code": "OtherError", "message": "Invalid param"}"""
    mockHttpClient.responseToReturn = new MockHttpResponse(400, responseJson)
    
    val client = new NuExtractClient(mockHttpClient, projectId, apiKey)
    val result = client.fetchJobStatus("job-123")
    
    assertEquals(result, Left((StatusCode.BadRequest, responseJson)))
  }

  test("fetchJobStatus returns Left with BadGateway when status is 500") {
    val mockHttpClient = new MockHttpClient()
    mockHttpClient.responseToReturn = new MockHttpResponse(500, "Error")
    
    val client = new NuExtractClient(mockHttpClient, projectId, apiKey)
    val result = client.fetchJobStatus("job-123")
    
    assertEquals(result, Left((StatusCode.BadGateway, "NuExtract status lookup failed with HTTP 500: Error")))
  }

  test("fetchJobStatus returns Left with InternalServerError when exception is thrown") {
    val mockHttpClient = new MockHttpClient()
    mockHttpClient.exceptionToThrow = new RuntimeException("Network timeout")
    
    val client = new NuExtractClient(mockHttpClient, projectId, apiKey)
    val result = client.fetchJobStatus("job-123")
    
    assertEquals(result, Left((StatusCode.InternalServerError, "Internal error while fetching job status: Network timeout")))
  }

