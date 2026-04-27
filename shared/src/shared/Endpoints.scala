package shared

import sttp.tapir.*

object Endpoints:
  val greetEndpoint: PublicEndpoint[String, Unit, String, Any] =
    endpoint.get.in("api" / "greet").in(query[String]("name")).out(stringBody)
