package frontend.components

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom

object ActionBar:
  def apply(
    onUploadFiles: Seq[dom.File] => Unit,
    onStartCamera: () => Unit,
    onToggleHistory: () => Unit
  ): HtmlElement =
    div(
      cls := "action-bar",
      // Upload Button
      div(
        cls := "file-input-wrapper",
        div(
          cls := "action-btn",
          svg.svg(
            svg.viewBox := "0 0 24 24",
            svg.fill := "none",
            svg.stroke := "currentColor",
            svg.strokeWidth := "2",
            svg.path(svg.d := "M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12")
          ),
          "Upload"
        ),
        input(
          typ := "file",
          accept := "image/*",
          multiple := true,
          onChange --> { event =>
            val files = event.target.asInstanceOf[dom.HTMLInputElement].files
            val fileList = (0 until files.length).flatMap(i => Option(files.item(i)))
            if fileList.nonEmpty then onUploadFiles(fileList)
          }
        )
      ),
      // Camera Button
      button(
        cls := "action-btn",
        onClick --> (_ => onStartCamera()),
        svg.svg(
          svg.viewBox := "0 0 24 24",
          svg.fill := "none",
          svg.stroke := "currentColor",
          svg.strokeWidth := "2",
          svg.path(svg.d := "M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z"),
          svg.circle(svg.cx := "12", svg.cy := "13", svg.r := "3")
        ),
        "Take Photo"
      ),
      // History Button
      button(
        cls := "action-btn",
        onClick --> (_ => onToggleHistory()),
        svg.svg(
          svg.viewBox := "0 0 24 24",
          svg.fill := "none",
          svg.stroke := "currentColor",
          svg.strokeWidth := "2",
          svg.path(svg.d := "M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z")
        ),
        "History"
      )
    )
