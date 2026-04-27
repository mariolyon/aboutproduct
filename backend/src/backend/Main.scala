package backend

import ox.supervised
import sttp.tapir.*
import sttp.tapir.files.*
import sttp.tapir.server.netty.sync.NettySyncServer
import shared.Endpoints

@main def run(): Unit =
  val greetServerEndpoint =
    Endpoints.greetEndpoint.handleSuccess(name => s"Hello, $name!")

  val staticDir = Thread.currentThread().getContextClassLoader.getResource("frontend").getPath

  val staticEndpoints = staticFilesGetServerEndpoint[sttp.shared.Identity](emptyInput)(staticDir)

  supervised:
    NettySyncServer()
      .port(8080)
      .addEndpoint(greetServerEndpoint)
      .addEndpoint(staticEndpoints)
      .startAndWait()
