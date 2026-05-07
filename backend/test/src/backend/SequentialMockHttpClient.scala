package backend

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.net.Authenticator
import java.net.CookieHandler
import java.net.ProxySelector
import java.time.Duration
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters

class SequentialMockHttpClient(responses: List[HttpResponse[String]]) extends HttpClient:
  private var remainingResponses = responses
  var lastRequests: List[HttpRequest] = Nil

  override def cookieHandler(): Optional[CookieHandler] = Optional.empty()
  override def connectTimeout(): Optional[Duration] = Optional.empty()
  override def followRedirects(): HttpClient.Redirect = HttpClient.Redirect.NEVER
  override def proxy(): Optional[ProxySelector] = Optional.empty()
  override def sslContext(): SSLContext = null
  override def sslParameters(): SSLParameters = null
  override def authenticator(): Optional[Authenticator] = Optional.empty()
  override def version(): HttpClient.Version = HttpClient.Version.HTTP_1_1
  override def executor(): Optional[Executor] = Optional.empty()

  // This is a hack because HttpClient is hard to mock perfectly without a lot of boilerplate
  override def send[T](request: HttpRequest, responseBodyHandler: HttpResponse.BodyHandler[T]): HttpResponse[T] =
    lastRequests = lastRequests :+ request
    remainingResponses match
      case head :: tail =>
        remainingResponses = tail
        head.asInstanceOf[HttpResponse[T]]
      case Nil =>
        throw new RuntimeException("No more mocked responses")

  override def sendAsync[T](request: HttpRequest, responseBodyHandler: HttpResponse.BodyHandler[T]): CompletableFuture[HttpResponse[T]] = ???
  override def sendAsync[T](request: HttpRequest, responseBodyHandler: HttpResponse.BodyHandler[T], pushPromiseHandler: HttpResponse.PushPromiseHandler[T]): CompletableFuture[HttpResponse[T]] = ???
