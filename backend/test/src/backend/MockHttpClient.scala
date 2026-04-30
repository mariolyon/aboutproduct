package backend

import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.Authenticator
import java.net.CookieHandler
import java.net.ProxySelector
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.Optional
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import java.util.concurrent.Executor

class MockHttpClient extends HttpClient:
  var lastRequest: HttpRequest = null
  var responseToReturn: HttpResponse[String] = null
  var exceptionToThrow: Throwable = null

  override def cookieHandler(): Optional[CookieHandler] = Optional.empty()
  override def connectTimeout(): Optional[Duration] = Optional.empty()
  override def followRedirects(): HttpClient.Redirect = HttpClient.Redirect.NEVER
  override def proxy(): Optional[ProxySelector] = Optional.empty()
  override def sslContext(): SSLContext = null
  override def sslParameters(): SSLParameters = null
  override def authenticator(): Optional[Authenticator] = Optional.empty()
  override def version(): HttpClient.Version = HttpClient.Version.HTTP_1_1
  override def executor(): Optional[Executor] = Optional.empty()
  
  override def send[T](request: HttpRequest, responseBodyHandler: HttpResponse.BodyHandler[T]): HttpResponse[T] =
    lastRequest = request
    if exceptionToThrow != null then throw exceptionToThrow
    responseToReturn.asInstanceOf[HttpResponse[T]]

  override def sendAsync[T](request: HttpRequest, responseBodyHandler: HttpResponse.BodyHandler[T]): CompletableFuture[HttpResponse[T]] = ???
  override def sendAsync[T](request: HttpRequest, responseBodyHandler: HttpResponse.BodyHandler[T], pushPromiseHandler: HttpResponse.PushPromiseHandler[T]): CompletableFuture[HttpResponse[T]] = ???
