import mill._, scalalib._, scalajslib._

trait AppScalaModule extends ScalaModule {
  def scalaVersion = "3.3.3"
}

trait AppScalaJSModule extends AppScalaModule with ScalaJSModule {
  def scalaJSVersion = "1.20.2"
}

object shared extends Module {
  trait SharedModule extends AppScalaModule with PlatformScalaModule {
    override def moduleDir = super.moduleDir / os.up / "modules" / "shared"
    def mvnDeps = Seq(
      mvn"com.softwaremill.sttp.tapir::tapir-core::1.13.6"
    )
  }
  object jvm extends SharedModule
  object js extends SharedModule with AppScalaJSModule
}

object backend extends AppScalaModule {
  override def moduleDir = super.moduleDir / os.up / "modules" / "backend"
  def moduleDeps = Seq(shared.jvm)
  def mvnDeps = Seq(
    mvn"com.softwaremill.sttp.tapir::tapir-netty-server-sync:1.13.6",
    mvn"com.softwaremill.sttp.tapir::tapir-files:1.13.6"
  )
  def resources = Task {
    os.makeDir.all(Task.dest / "webapp")
    val jsPath = webapp.fastLinkJS().dest.path
    os.copy.over(jsPath / "main.js", Task.dest / "webapp" / "main.js")
    os.copy.over(jsPath / "main.js.map", Task.dest / "webapp" / "main.js.map")
    val webappResources = os.Path(webapp.resources().head.path.toString)
    if (os.exists(webappResources / "index.html"))
      os.copy.over(webappResources / "index.html", Task.dest / "webapp" / "index.html")
    super.resources() ++ Seq(PathRef(Task.dest))
  }
}

object webapp extends AppScalaJSModule {
  def moduleDeps = Seq(shared.js)
  def mvnDeps = Seq(
    mvn"com.raquo::laminar::17.0.0"
  )
}
