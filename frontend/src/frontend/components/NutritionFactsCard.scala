package frontend.components

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import scala.scalajs.js
import frontend.utils.JsonUtils.*

object NutritionFactsCard:
  private def row(labelText: String, valueText: String, rowClass: String = ""): HtmlElement =
    div(
      className := s"nf-row $rowClass".trim,
      span(cls := "nf-label", labelText),
      span(cls := "nf-value", valueText)
    )

  def apply(
    result: js.Dynamic,
    headerTitle: String,
    onViewJson: () => Unit,
    onClear: Option[() => Unit] = None,
    onTitleUpdate: Option[String => Unit] = None
  ): HtmlElement =
    div(
      cls := "flex-1 min-w-[380px] md:max-w-[calc(50%-1rem)] w-full flex flex-col items-center p-6 border border-gray-200 rounded-xl bg-white shadow-sm mx-auto md:mx-0",
      tabIndex := -1,
      onMountCallback { ctx =>
        val node = ctx.thisNode.ref.asInstanceOf[js.Dynamic]
        node.scrollIntoView(js.Dynamic.literal(behavior = "smooth", block = "start"))
        node.focus()
      },
      div(
        cls := "flex justify-between items-center w-full mb-5",
        h2(cls := "status-text mb-0", headerTitle),
        div(
          cls := "flex gap-2",
          button(
            cls := "action-btn px-3 py-1.5 min-w-0 flex-row text-xs",
            "View JSON",
            onClick --> { _ => onViewJson() }
          ),
          onClear.map { cb =>
            button(
              cls := "action-btn px-3 py-1.5 min-w-0 flex-row text-xs bg-red-50 text-red-600 border-red-200 hover:bg-red-100",
              "Clear",
              onClick --> (_ => cb())
            )
          }.getOrElse(emptyNode)
        )
      ),
      renderNutritionFacts(result, onTitleUpdate)
    )

  private def renderNutritionFacts(result: js.Dynamic, onTitleUpdate: Option[String => Unit]): HtmlElement =
    val data = extractNutritionFacts(result)
    val isEditing = Var(false)
    val editTitle = Var(data.title)

    div(
      cls := "nutrition-card",
      child <-- isEditing.signal.map {
        case false =>
          h2(
            cls := "cursor-pointer hover:text-blue-600 transition-colors flex items-center justify-center gap-2 mb-2",
            title := "Click to edit title",
            onClick --> { _ =>
              editTitle.set(data.title)
              isEditing.set(true)
            },
            span(data.title),
            svg.svg(
              svg.cls := "w-4 h-4 text-gray-400 inline-block",
              svg.fill := "none",
              svg.stroke := "currentColor",
              svg.strokeWidth := "2",
              svg.viewBox := "0 0 24 24",
              svg.path(svg.d := "M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z")
            )
          )
        case true =>
          div(
            cls := "flex items-center gap-2 w-full mb-4",
            input(
              typ := "text",
              cls := "border border-gray-300 rounded px-2 py-1 flex-1 text-xl font-bold focus:outline-none focus:border-blue-500",
              value <-- editTitle,
              onInput.mapToValue --> editTitle,
              onKeyDown.filter(_.key == "Enter") --> { _ =>
                val newTitle = editTitle.now().trim
                if newTitle.nonEmpty && newTitle != data.title then
                  onTitleUpdate.foreach(_(newTitle))
                isEditing.set(false)
              },
              onKeyDown.filter(_.key == "Escape") --> { _ =>
                isEditing.set(false)
              },
              onMountCallback { ctx =>
                ctx.thisNode.ref.asInstanceOf[dom.HTMLInputElement].focus()
              }
            ),
            button(
              cls := "action-btn px-3 py-1 text-xs min-w-0 bg-blue-50 text-blue-600 border-blue-200 hover:bg-blue-100",
              "Save",
              onClick --> { _ =>
                val newTitle = editTitle.now().trim
                if newTitle.nonEmpty && newTitle != data.title then
                  onTitleUpdate.foreach(_(newTitle))
                isEditing.set(false)
              }
            ),
            button(
              cls := "action-btn px-3 py-1 text-xs min-w-0 bg-gray-50 text-gray-600 border-gray-200 hover:bg-gray-100",
              "Cancel",
              onClick --> { _ => isEditing.set(false) }
            )
          )
      },
      div(cls := "nf-subtitle", s"${data.servingsPerContainer} servings per container"),
      row("Serving size", data.servingSize, "serving-size"),
      div(cls := "nf-thick-divider"),
      div(
        cls := "nf-amount",
        "Amount per serving"
      ),
      div(
        cls := "nf-calories-row",
        span(cls := "nf-calories-label", "Calories"),
        span(cls := "nf-calories-value", data.calories.serving)
      ),
      div(cls := "nf-thick-divider"),
      div(
        cls := "nf-dv-header",
        "% Daily Value*"
      ),
      row("Total Fat", data.totalFat.serving, "major"),
      row("Saturated Fat", data.saturatedFat.serving, "indent"),
      row("Trans Fat", data.transFat.serving, "indent"),
      row("Cholesterol", data.cholesterol.serving, "major"),
      row("Sodium", data.sodium.serving, "major"),
      row("Total Carbohydrate", data.totalCarbs.serving, "major"),
      row("Dietary Fiber", data.dietaryFiber.serving, "indent"),
      row("Total Sugars", data.totalSugars.serving, "indent"),
      row("Includes Added Sugars", data.addedSugars.serving, "indent-2"),
      row("Protein", data.protein.serving, "major"),
      div(cls := "nf-thick-divider"),
      if data.nutrients.nonEmpty then
        data.nutrients.map(nutrient =>
          row(
            nutrient.name,
            s"${nutrient.quantity.serving} (${nutrient.percentage}% DV)"
          )
        )
      else
        emptyNode,
      p(
        cls := "nf-ingredients",
        span(cls := "nf-ingredients-label", "Ingredients: "),
        data.ingredients
      ),
      p(cls := "nf-small-print", data.smallPrint)
    )
