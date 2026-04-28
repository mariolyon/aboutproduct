package shared

import sttp.tapir.*

object Endpoints:
  val jobsEndpoint: PublicEndpoint[(Array[Byte], String), Unit, String, Any] =
    endpoint.post
      .in("api" / "jobs")
      .in(byteArrayBody)
      .in(header[String]("Content-Type"))
      .out(stringBody)

  val jobStatusEndpoint: PublicEndpoint[String, Unit, String, Any] =
    endpoint.get.in("api" / "jobs" / path[String]("jobId")).out(stringBody)
