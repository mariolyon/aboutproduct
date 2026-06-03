package frontend.components

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom

object ImagePreview:
  def apply(urlOpt: Option[String], fileOpt: Option[dom.File], statusMsg: String): HtmlElement =
    div(
      cls := "flex-1 min-w-[380px] md:max-w-[calc(50%-1rem)] w-full flex flex-col items-center p-6 border border-gray-200 rounded-xl bg-white shadow-sm mx-auto md:mx-0",
      Option.when(statusMsg.nonEmpty)(h2(cls := "status-text", statusMsg)).getOrElse(emptyNode),
      fileOpt.map(file => div(cls := "image-filename mb-3 text-sm text-gray-500 break-all font-medium text-left w-full", file.name)).getOrElse(emptyNode),
      urlOpt.map(url => img(cls := "image-preview w-full max-h-[70vh] block border border-gray-300 rounded-lg object-contain bg-white p-1 shadow-md", src := url, alt := "Selected image preview")).getOrElse(emptyNode)
    )
