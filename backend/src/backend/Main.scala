package backend

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.util.logging.Logger

import ox.supervised
import sttp.tapir.*
import sttp.tapir.files.*
import sttp.tapir.server.netty.sync.NettySyncServer
import shared.Endpoints
import ujson.Obj

@main def run(): Unit =
  val logger = Logger.getLogger("backend.Main")

  def requiredEnv(name: String): String =
    sys.env.get(name) match
      case Some(value) => value
      case None =>
        logger.severe(s"Missing required environment variable: $name")
        throw IllegalStateException(s"Missing required environment variable: $name")

  val apiKey = requiredEnv("API_KEY")
  val projectId = requiredEnv("PROJECT_ID")
  val httpClient = HttpClient.newHttpClient()
  logger.info(s"Backend starting with PROJECT_ID=$projectId")

  def authHeaderValue: String = s"Bearer $apiKey"

  def parseJobId(responseBody: String): String =
    val json = ujson.read(responseBody)
    val maybeJobId = json match
      case obj: Obj =>
        obj.value.get("id").flatMap(_.strOpt)
          .orElse(obj.value.get("job_id").flatMap(_.strOpt))
          .orElse(obj.value.get("jobId").flatMap(_.strOpt))
          .orElse(
            obj.value
              .get("job")
              .collect { case nestedObj: Obj => nestedObj }
              .flatMap(_.value.get("id").flatMap(_.strOpt))
          )
      case _ => None
    maybeJobId.getOrElse(
      throw RuntimeException(s"NuExtract create-job response did not include a job id: $responseBody")
    )

  def createNuExtractJob(imageBytes: Array[Byte], contentType: String): String =
    logger.info(s"Creating NuExtract job (payloadBytes=${imageBytes.length}, contentType=$contentType)")
    val request = HttpRequest
      .newBuilder()
      .uri(URI.create(s"https://nuextract.ai/api/structured-extraction/$projectId/jobs"))
      .header("Authorization", authHeaderValue)
      .header("Content-Type", contentType)
      .POST(HttpRequest.BodyPublishers.ofByteArray(imageBytes))
      .build()

    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    if response.statusCode() / 100 != 2 then
      logger.severe(
        s"NuExtract create-job failed (httpStatus=${response.statusCode()}, responseBody=${response.body()})"
      )
      throw RuntimeException(
        s"NuExtract create-job failed with HTTP ${response.statusCode()}: ${response.body()}"
      )

    val jobId = parseJobId(response.body())
    logger.info(s"NuExtract job created successfully (jobId=$jobId)")
    jobId

  def fetchNuExtractJobStatus(jobId: String): String =
    logger.info(s"Fetching NuExtract job status (jobId=$jobId)")
    val request = HttpRequest
      .newBuilder()
      .uri(URI.create(s"https://nuextract.ai/api/structured-extraction/jobs/$jobId"))
      .header("Authorization", authHeaderValue)
      .GET()
      .build()

    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    if response.statusCode() / 100 != 2 then
      logger.severe(
        s"NuExtract status lookup failed (jobId=$jobId, httpStatus=${response.statusCode()}, responseBody=${response.body()})"
      )
      throw RuntimeException(
        s"NuExtract status lookup failed for job $jobId with HTTP ${response.statusCode()}: ${response.body()}"
      )

    logger.info(s"NuExtract status fetched successfully (jobId=$jobId, httpStatus=${response.statusCode()})")
    response.body()

  val createJobServerEndpoint =
    Endpoints.jobsEndpoint.handleSuccess((imageBytes, contentType) =>
      createNuExtractJob(imageBytes, contentType)
    )

  val jobStatusServerEndpoint =
    Endpoints.jobStatusEndpoint.handleSuccess(jobId =>
      fetchNuExtractJobStatus(jobId)
    )

  val staticDir = Thread.currentThread().getContextClassLoader.getResource("frontend").getPath

  val staticEndpoints = staticFilesGetServerEndpoint[sttp.shared.Identity](emptyInput)(staticDir)

  supervised:
    logger.info("Starting backend server on port 8080")
    NettySyncServer()
      .port(8080)
      .addEndpoint(createJobServerEndpoint)
      .addEndpoint(jobStatusServerEndpoint)
      .addEndpoint(staticEndpoints)
      .startAndWait()
