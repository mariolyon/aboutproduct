package frontend.models

import org.scalajs.dom
import scala.scalajs.js

case class NutriValue(serving: String, per100: String)

case class Nutrient(name: String, quantity: NutriValue, percentage: String)

case class NutritionFactsData(
  title: String,
  servingsPerContainer: String,
  servingSize: String,
  calories: NutriValue,
  totalFat: NutriValue,
  saturatedFat: NutriValue,
  transFat: NutriValue,
  cholesterol: NutriValue,
  sodium: NutriValue,
  totalCarbs: NutriValue,
  dietaryFiber: NutriValue,
  totalSugars: NutriValue,
  addedSugars: NutriValue,
  protein: NutriValue,
  nutrients: Seq[Nutrient],
  ingredients: String,
  smallPrint: String
)

case class HistoryItem(
  id: String,
  timestamp: Double,
  title: String,
  dataStr: String,
  imageBlob: Option[dom.Blob],
  status: String = "completed"
) {
  def parsedData: js.Dynamic = js.JSON.parse(dataStr).asInstanceOf[js.Dynamic]
}
