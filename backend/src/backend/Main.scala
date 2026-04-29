package backend

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.util.logging.Logger

import ox.supervised
import sttp.model.StatusCode
import sttp.tapir.*
import sttp.tapir.files.*
import sttp.tapir.server.netty.sync.NettySyncServer
import shared.Endpoints
import ujson.Obj
import scala.util.control.NonFatal

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

  def isJobNotCompleted(responseBody: String): Boolean =
    try
      val json = ujson.read(responseBody)
      json.obj.get("code").flatMap(_.strOpt).contains("JobNotCompleted")
    catch
      case NonFatal(_) => false

  def createNuExtractJob(imageBytes: Array[Byte], contentType: String): Either[(StatusCode, String), String] =
    try
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
        Left(
          (
            StatusCode.BadGateway,
            s"NuExtract create-job failed with HTTP ${response.statusCode()}: ${response.body()}"
          )
        )
      else
        try
          val jobId = parseJobId(response.body())
          logger.info(s"NuExtract job created successfully (jobId=$jobId)")
          Right(jobId)
        catch
          case NonFatal(error) =>
            logger.severe(s"Failed to parse NuExtract create-job response: ${error.getMessage}")
            Left((StatusCode.BadGateway, s"Failed to parse NuExtract create-job response: ${error.getMessage}"))
    catch
      case NonFatal(error) =>
        logger.severe(s"Internal error while creating NuExtract job: ${error.getMessage}")
        Left((StatusCode.InternalServerError, s"Internal error while creating job: ${error.getMessage}"))

  def fetchNuExtractJobStatus(jobId: String): Either[(StatusCode, String), String] =
    try
      logger.info(s"Fetching NuExtract job status (jobId=$jobId)")
      val request = HttpRequest
        .newBuilder()
        .uri(URI.create(s"https://nuextract.ai/api/structured-extraction/jobs/$jobId"))
        .header("Authorization", authHeaderValue)
        .GET()
        .build()

      val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      response.statusCode() match
        case 200 =>
          logger.info(s"NuExtract status fetched successfully (jobId=$jobId, httpStatus=200)")
          Right(response.body())
        case 400 if isJobNotCompleted(response.body()) =>
          logger.info(s"NuExtract job is not completed yet (jobId=$jobId)")
          Left((StatusCode.NoContent, ""))
        case 400 =>
          logger.warning(
            s"NuExtract returned invalid job request (jobId=$jobId, responseBody=${response.body()})"
          )
          Left((StatusCode.BadRequest, response.body()))
        case statusCode =>
          logger.severe(
            s"NuExtract status lookup failed (jobId=$jobId, httpStatus=$statusCode, responseBody=${response.body()})"
          )
          Left(
            (
              StatusCode.BadGateway,
              s"NuExtract status lookup failed with HTTP $statusCode: ${response.body()}"
            )
          )
    catch
      case NonFatal(error) =>
        logger.severe(s"Internal error while fetching job status for $jobId: ${error.getMessage}")
        Left((StatusCode.InternalServerError, s"Internal error while fetching job status: ${error.getMessage}"))

  val createJobServerEndpoint =
    Endpoints.jobsEndpoint.handle((imageBytes, contentType) =>
      createNuExtractJob(imageBytes, contentType)
    )

  val jobStatusServerEndpoint =
    Endpoints.jobStatusEndpoint.handle(jobId =>
      fetchNuExtractJobStatus(jobId)
    )

  val staticEndpoints = staticResourcesGetServerEndpoint[sttp.shared.Identity](emptyInput)(
    Thread.currentThread().getContextClassLoader,
    "frontend"
  )

  supervised:
    logger.info("Starting backend server on port 8080")
    NettySyncServer()
      .port(8080)
      .addEndpoint(createJobServerEndpoint)
      .addEndpoint(jobStatusServerEndpoint)
      .addEndpoint(staticEndpoints)
      .startAndWait()
