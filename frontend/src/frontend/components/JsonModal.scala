package frontend.components

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom

object JsonModal:
  def apply(
    showJsonModal: Signal[Boolean],
    modalJson: Signal[String],
    onClose: () => Unit,
    onCopyToClipboard: String => Unit
  ): Modifier[HtmlElement] =
    child.maybe <-- showJsonModal.combineWith(modalJson).map { case (show, jsonText) =>
      Option.when(show) {
        div(
          cls := "fixed inset-0 z-[60] flex items-center justify-center p-4",
          // Backdrop
          div(
            cls := "fixed inset-0 bg-black bg-opacity-60",
            onClick --> { _ => onClose() }
          ),
          // Modal Content
          div(
            cls := "relative bg-white rounded-xl shadow-2xl w-full max-w-3xl max-h-[90vh] flex flex-col z-[70]",
            div(
              cls := "flex justify-between items-center p-4 border-b border-gray-200",
              h3(cls := "text-lg font-bold", "Nutrition Facts JSON"),
              button(
                cls := "text-gray-400 hover:text-gray-600 p-1",
                onClick --> { _ => onClose() },
                svg.svg(
                  svg.cls := "w-6 h-6",
                  svg.fill := "none",
                  svg.stroke := "currentColor",
                  svg.viewBox := "0 0 24 24",
                  svg.path(svg.strokeLineCap := "round", svg.strokeLineJoin := "round", svg.strokeWidth := "2", svg.d := "M6 18L18 6M6 6l12 12")
                )
              )
            ),
            div(
              cls := "p-4 overflow-auto flex-1 text-left bg-gray-50",
              pre(
                cls := "text-xs font-mono text-gray-800 break-all whitespace-pre-wrap p-2",
                jsonText
              )
            ),
            div(
              cls := "p-4 border-t border-gray-200 flex justify-end gap-3",
              button(
                cls := "action-btn px-4 py-2 min-w-0 flex-row text-sm",
                "Copy to Clipboard",
                onClick --> { _ =>
                  onCopyToClipboard(jsonText)
                }
              ),
              button(
                cls := "action-btn px-4 py-2 min-w-0 flex-row text-sm bg-gray-100 hover:bg-gray-200 border-gray-300",
                "Close",
                onClick --> { _ => onClose() }
              )
            )
          )
        )
      }
    }
