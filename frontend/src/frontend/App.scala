package frontend

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.Thenable.Implicits.*
import scala.concurrent.ExecutionContext.Implicits.global

@main def app(): Unit =
  val greeting = Var("Hello World")
  val nameInput = Var("")

  def submitName(): Unit =
    val name = nameInput.now()
    if name.nonEmpty then
      val encoded = js.URIUtils.encodeURIComponent(name)
      val future = for
        response <- dom.fetch(s"/api/greet?name=$encoded").toFuture
        text     <- response.text().toFuture
      yield text
      future.foreach(result => greeting.set(result))

  val appElement = div(
    h1(child.text <-- greeting.signal),
    div(
      input(
        typ         := "text",
        placeholder := "Enter your name",
        controlled(
          value <-- nameInput.signal,
          onInput.mapToValue --> nameInput.writer
        ),
        onKeyPress.filter(_.key == "Enter") --> (_ => submitName())
      ),
      button(
        "Submit",
        onClick --> (_ => submitName())
      )
    )
  )

  renderOnDomContentLoaded(dom.document.getElementById("app"), appElement)
