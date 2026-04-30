package backend

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.util.logging.Logger
import sttp.model.StatusCode
import scala.util.control.NonFatal

class NuExtractClient(httpClient: HttpClient, projectId: String, apiKey: String):
  private val logger = Logger.getLogger("backend.NuExtractClient")
  private def authHeaderValue: String = s"Bearer $apiKey"

  def createJob(imageBytes: Array[Byte], contentType: String): Either[(StatusCode, String), String] =
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
          val jobId = NuExtractParser.parseJobId(response.body())
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

  def fetchJobStatus(jobId: String): Either[(StatusCode, String), String] =
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
        case 400 if NuExtractParser.isJobNotCompleted(response.body()) =>
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
