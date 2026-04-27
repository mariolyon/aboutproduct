package backend

import ox.supervised
import sttp.tapir.*
import sttp.tapir.files.*
import sttp.tapir.server.netty.sync.NettySyncServer
import shared.Endpoints

@main def run(): Unit =
  val greetServerEndpoint =
    Endpoints.greetEndpoint.handleSuccess(name => s"Hello, $name!")

  val classLoader = Thread.currentThread().getContextClassLoader
  val staticEndpoints = staticResourcesGetServerEndpoint[sttp.shared.Identity](emptyInput)(classLoader, "webapp")

  supervised:
    NettySyncServer()
      .port(8080)
      .addEndpoint(greetServerEndpoint)
      .addEndpoint(staticEndpoints)
      .startAndWait()
