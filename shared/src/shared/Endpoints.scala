package shared

import sttp.model.StatusCode
import sttp.tapir.*

object Endpoints:
  val jobsEndpoint: PublicEndpoint[(Array[Byte], String), (StatusCode, String), String, Any] =
    endpoint.post
      .in("api" / "jobs")
      .in(byteArrayBody)
      .in(header[String]("Content-Type"))
      .out(stringBody)
      .errorOut(statusCode.and(stringBody))

  val jobStatusEndpoint: PublicEndpoint[String, (StatusCode, String), String, Any] =
    endpoint.get
      .in("api" / "jobs" / path[String]("jobId"))
      .out(stringBody)
      .errorOut(statusCode.and(stringBody))
