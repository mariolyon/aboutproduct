package frontend.components

import com.raquo.laminar.api.L.{*, given}
import scala.scalajs.js
import frontend.models.NutriValue
import frontend.utils.JsonUtils.*

object ComparisonView:
  private def compRow(labelText: String, v1: NutriValue, v2: NutriValue, rowClass: String = ""): HtmlElement =
    div(
      className := s"nf-row no-gap $rowClass grid grid-cols-6".trim,
      span(cls := "nf-label col-span-2", labelText),
      span(cls := "nf-value col-span-1 text-center border-l border-gray-200", v1.serving),
      span(cls := "nf-value col-span-1 text-center border-l border-gray-200 text-gray-500", if v1.per100 != "n/a" && v1.per100.nonEmpty then v1.per100 else "-"),
      span(cls := "nf-value col-span-1 text-center border-l border-gray-200 border-l-2", v2.serving),
      span(cls := "nf-value col-span-1 text-center border-l border-gray-200 text-gray-500", if v2.per100 != "n/a" && v2.per100.nonEmpty then v2.per100 else "-")
    )

  private def compRowStr(labelText: String, value1: String, value2: String, rowClass: String = ""): HtmlElement =
    div(
      className := s"nf-row no-gap $rowClass grid grid-cols-6".trim,
      span(cls := "nf-label col-span-2", labelText),
      span(cls := "nf-value col-span-2 text-center border-l border-gray-200", value1),
      span(cls := "nf-value col-span-2 text-center border-l border-gray-200 border-l-2", value2)
    )

  def apply(result1: js.Dynamic, result2: js.Dynamic, onClear: Option[() => Unit] = None): HtmlElement =
    div(
      cls := "flex-1 w-full flex flex-col items-center p-6 border border-gray-200 rounded-xl bg-white shadow-sm mx-auto",
      tabIndex := -1,
      onMountCallback { ctx =>
        val node = ctx.thisNode.ref.asInstanceOf[js.Dynamic]
        node.scrollIntoView(js.Dynamic.literal(behavior = "smooth", block = "start"))
        node.focus()
      },
      div(
        cls := "flex justify-between items-center w-full mb-5",
        h2(cls := "status-text mb-0", "Product Comparison"),
        div(
          cls := "flex gap-2",
          onClear.map { cb =>
            button(
              cls := "action-btn px-3 py-1.5 min-w-0 flex-row text-xs bg-red-50 text-red-600 border-red-200 hover:bg-red-100",
              "Exit Comparison",
              onClick --> (_ => cb())
            )
          }.getOrElse(emptyNode)
        )
      ),
      renderComparisonNutritionFacts(result1, result2)
    )

  private def renderComparisonNutritionFacts(result1: js.Dynamic, result2: js.Dynamic): HtmlElement =
    val data1 = extractNutritionFacts(result1)
    val data2 = extractNutritionFacts(result2)

    div(
      cls := "nutrition-card max-w-2xl",
      h2(
        cls := "mb-2 text-center",
        "Comparison"
      ),
      div(
        cls := "grid grid-cols-6 no-gap border-b-2 border-black pb-1 mb-1 font-bold",
        span(cls := "col-span-2"),
        span(cls := "col-span-2 text-center border-l border-gray-200", data1.title),
        span(cls := "col-span-2 text-center border-l border-gray-200 border-l-2", data2.title)
      ),
      div(
        cls := "grid grid-cols-6 no-gap border-b border-black pb-1 mb-1 text-xs font-semibold",
        span(cls := "col-span-2"),
        span(cls := "col-span-1 text-center border-l border-gray-200", "Actual"),
        span(cls := "col-span-1 text-center border-l border-gray-200 text-gray-500", "/100g"),
        span(cls := "col-span-1 text-center border-l border-gray-200 border-l-2", "Actual"),
        span(cls := "col-span-1 text-center border-l border-gray-200 text-gray-500", "/100g")
      ),
      div(cls := "nf-subtitle", "Side-by-side view"),
      compRowStr("Serving size", data1.servingSize, data2.servingSize, "serving-size"),
      div(cls := "nf-thick-divider"),
      div(
        cls := "nf-amount",
        "Amount per serving and per 100g"
      ),
      div(
        cls := "nf-calories-row grid grid-cols-6 no-gap items-baseline",
        span(cls := "nf-calories-label col-span-2", "Calories"),
        span(cls := "nf-calories-value col-span-1 text-center text-3xl border-l border-gray-200", data1.calories.serving),
        span(cls := "nf-calories-value col-span-1 text-center text-xl border-l border-gray-200 text-gray-500", if data1.calories.per100 != "n/a" then data1.calories.per100 else "-"),
        span(cls := "nf-calories-value col-span-1 text-center text-3xl border-l border-gray-200 border-l-2", data2.calories.serving),
        span(cls := "nf-calories-value col-span-1 text-center text-xl border-l border-gray-200 text-gray-500", if data2.calories.per100 != "n/a" then data2.calories.per100 else "-")
      ),
      div(cls := "nf-thick-divider"),
      div(
        cls := "nf-dv-header grid grid-cols-6 no-gap",
        span(cls := "col-span-2"),
        span(cls := "col-span-2 text-right text-xs border-l border-gray-200", "% Daily Value* (1)"),
        span(cls := "col-span-2 text-right text-xs border-l border-gray-200 border-l-2", "% Daily Value* (2)")
      ),
      compRow("Total Fat", data1.totalFat, data2.totalFat, "major"),
      compRow("Saturated Fat", data1.saturatedFat, data2.saturatedFat, "indent"),
      compRow("Trans Fat", data1.transFat, data2.transFat, "indent"),
      compRow("Cholesterol", data1.cholesterol, data2.cholesterol, "major"),
      compRow("Sodium", data1.sodium, data2.sodium, "major"),
      compRow("Total Carbohydrate", data1.totalCarbs, data2.totalCarbs, "major"),
      compRow("Dietary Fiber", data1.dietaryFiber, data2.dietaryFiber, "indent"),
      compRow("Total Sugars", data1.totalSugars, data2.totalSugars, "indent"),
      compRow("Includes Added Sugars", data1.addedSugars, data2.addedSugars, "indent-2"),
      compRow("Protein", data1.protein, data2.protein, "major"),
      div(cls := "nf-thick-divider"),
      div(
        cls := "grid grid-cols-2 gap-4 mt-4 text-xs",
        div(
          span(cls := "nf-ingredients-label", "Ingredients (1): "),
          data1.ingredients
        ),
        div(
          span(cls := "nf-ingredients-label", "Ingredients (2): "),
          data2.ingredients
        )
      ),
      p(cls := "nf-small-print", s"1: ${data1.smallPrint}"),
      p(cls := "nf-small-print", s"2: ${data2.smallPrint}")
    )
