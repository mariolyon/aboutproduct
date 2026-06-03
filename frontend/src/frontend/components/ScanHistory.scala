package frontend.components

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import scala.scalajs.js
import frontend.models.HistoryItem

object ScanHistory:
  def apply(
    showHistory: Signal[Boolean],
    scanHistory: Signal[Seq[HistoryItem]],
    currentResultId: Signal[Option[String]],
    comparisonResultId: Signal[Option[String]],
    onClearAll: () => Unit,
    onViewItem: HistoryItem => Unit,
    onCompareItem: HistoryItem => Unit,
    onClearItem: HistoryItem => Unit
  ): Modifier[HtmlElement] =
    child.maybe <-- showHistory.combineWith(scanHistory, currentResultId, comparisonResultId).map { case (show, history, currentId, compId) =>
      Option.when(show) {
        div(
          cls := "bg-white border border-gray-200 rounded-xl shadow-lg p-6 mb-6 max-w-2xl mx-auto text-left",
          div(
            cls := "flex justify-between items-center mb-4",
            h3(cls := "text-lg font-bold", "Scan History"),
            button(
              cls := "text-xs text-red-500 hover:text-red-700 font-medium",
              "Clear All",
              onClick --> { _ => onClearAll() }
            )
          ),
          if history.isEmpty then
            p(cls := "text-gray-500 italic", "No scans yet.")
          else
            div(
              cls := "flex flex-col gap-3",
              history.map { item =>
                div(
                  cls := "flex justify-between items-center p-3 border border-gray-100 rounded-lg hover:bg-gray-50",
                  div(
                    cls := "flex flex-col",
                    span(cls := "font-semibold text-gray-800", item.title),
                    span(cls := "text-xs text-gray-500",
                      new js.Date(item.timestamp).toLocaleString() + (if item.status != "completed" then s" - ${item.status.capitalize}" else "")
                    )
                  ),
                  div(
                    cls := "flex gap-2",
                    button(
                      cls := "action-btn px-3 py-1 text-xs min-w-0 disabled:opacity-50 disabled:cursor-not-allowed disabled:bg-gray-50 disabled:text-gray-400 disabled:border-gray-200",
                      disabled <-- currentResultId.combineWith(comparisonResultId).map { case (currId, compId) =>
                        item.status != "completed" || (currId.contains(item.id) && compId.isEmpty)
                      },
                      "View",
                      onClick --> { _ => onViewItem(item) }
                    ),
                    button(
                      cls := "action-btn px-3 py-1 text-xs min-w-0 bg-blue-50 text-blue-600 border-blue-200 hover:bg-blue-100 disabled:opacity-50 disabled:cursor-not-allowed disabled:bg-gray-50 disabled:text-gray-400 disabled:border-gray-200",
                      disabled <-- currentResultId.combineWith(comparisonResultId).map { case (currId, compId) =>
                        item.status != "completed" || currId.isEmpty || currId.contains(item.id) || compId.contains(item.id)
                      },
                      "Compare",
                      onClick --> { _ => onCompareItem(item) }
                    ),
                    button(
                      cls := "action-btn px-3 py-1 text-xs min-w-0 bg-red-50 text-red-600 border-red-200 hover:bg-red-100",
                      "Clear",
                      onClick --> { _ => onClearItem(item) }
                    )
                  )
                )
              }
            )
        )
      }
    }
