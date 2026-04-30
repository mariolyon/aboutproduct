package backend

import java.net.URI
import java.net.http.{HttpClient, HttpHeaders, HttpRequest, HttpResponse}
import java.util.Optional
import javax.net.ssl.SSLSession

class MockHttpResponse(statusCodeValue: Int, bodyValue: String) extends HttpResponse[String]:
  override def statusCode(): Int = statusCodeValue
  override def request(): HttpRequest = null
  override def previousResponse(): Optional[HttpResponse[String]] = Optional.empty()
  override def headers(): HttpHeaders = null
  override def body(): String = bodyValue
  override def sslSession(): Optional[SSLSession] = Optional.empty()
  override def uri(): URI = null
  override def version(): HttpClient.Version = HttpClient.Version.HTTP_1_1
