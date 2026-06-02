package backend

import java.net.URI
import java.net.http.{HttpClient, HttpHeaders, HttpRequest, HttpResponse}
import java.util.Optional
import javax.net.ssl.SSLSession

class MockHttpResponse(statusCodeValue: Int, bodyValue: String) extends HttpResponse[String]:
  override val statusCode: Int = statusCodeValue
  override val request: HttpRequest = null
  override val previousResponse: Optional[HttpResponse[String]] = Optional.empty()
  override val headers: HttpHeaders = null
  override val body: String = bodyValue
  override val sslSession: Optional[SSLSession] = Optional.empty()
  override val uri: URI = null
  override val version: HttpClient.Version = HttpClient.Version.HTTP_1_1
