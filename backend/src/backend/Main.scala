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
  val nuExtractClient = NuExtractClient(httpClient, projectId, apiKey)
  logger.info(s"Backend starting with PROJECT_ID=$projectId")

  val createJobServerEndpoint =
    Endpoints.jobsEndpoint.handle((imageBytes, contentType) =>
      nuExtractClient.createJob(imageBytes, contentType)
    )

  val jobStatusServerEndpoint =
    Endpoints.jobStatusEndpoint.handle(jobId =>
      nuExtractClient.fetchJobStatus(jobId)
    )

  val staticEndpoints = staticResourcesGetServerEndpoint[sttp.shared.Identity](emptyInput)(
    Thread.currentThread().getContextClassLoader,
    "frontend"
  )

  supervised:
    logger.info("Starting backend server on port 8080")
    NettySyncServer()
      .host("0.0.0.0")
      .port(8080)
      .addEndpoint(createJobServerEndpoint)
      .addEndpoint(jobStatusServerEndpoint)
      .addEndpoint(staticEndpoints)
      .startAndWait()
