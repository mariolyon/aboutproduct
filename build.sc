import mill._, scalalib._, scalajslib._

trait AppScalaModule extends ScalaModule {
  def scalaVersion = "3.3.3"
}

trait AppScalaJSModule extends AppScalaModule with ScalaJSModule {
  def scalaJSVersion = "1.20.2"
}

object shared extends Module {
  trait SharedModule extends AppScalaModule with PlatformScalaModule {
    def mvnDeps = Seq(
      mvn"com.softwaremill.sttp.tapir::tapir-core::1.13.17"
    )
  }
  object jvm extends SharedModule
  object js extends SharedModule with AppScalaJSModule
}

object backend extends AppScalaModule {
  def moduleDeps = Seq(shared.jvm)
  def mvnDeps = Seq(
    mvn"com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.13.17",
    mvn"com.softwaremill.sttp.tapir::tapir-files:1.13.17"
  )
  def resources = Task {
    os.makeDir.all(Task.dest / "frontend")
    val jsPath = frontend.fastLinkJS().dest.path
    os.copy.over(jsPath / "main.js", Task.dest / "frontend" / "main.js")
    os.copy.over(jsPath / "main.js.map", Task.dest / "frontend" / "main.js.map")
    val frontendResources = os.Path(frontend.resources().head.path.toString)
    if (os.exists(frontendResources / "index.html"))
      os.copy.over(frontendResources / "index.html", Task.dest / "frontend" / "index.html")
    super.resources() ++ Seq(PathRef(Task.dest))
  }
}

object frontend extends AppScalaJSModule {
  def moduleDeps = Seq(shared.js)
  def mvnDeps = Seq(
    mvn"com.raquo::laminar::17.0.0"
  )
}
