package frontend.utils

import scala.scalajs.js
import frontend.models.*

object JsonUtils:
  def isDefined(value: js.Dynamic): Boolean =
    !js.isUndefined(value) && value != null

  def dynamicField(obj: js.Dynamic, field: String): Option[js.Dynamic] =
    val value = obj.selectDynamic(field).asInstanceOf[js.Dynamic]
    Option.when(isDefined(value))(value)

  def asArray(value: Option[js.Dynamic]): Seq[js.Dynamic] =
    value match
      case Some(arrayValue) if js.Array.isArray(arrayValue.asInstanceOf[js.Any]) =>
        arrayValue.asInstanceOf[js.Array[js.Dynamic]].toSeq
      case _ => Seq.empty

  def stringify(value: js.Dynamic): String =
    if !isDefined(value) then "n/a"
    else
      js.typeOf(value.asInstanceOf[js.Any]) match
        case "string" => value.asInstanceOf[String]
        case "number" | "boolean" => value.toString
        case _ => js.JSON.stringify(value)

  def stringField(obj: js.Dynamic, field: String): String =
    dynamicField(obj, field).map(stringify).getOrElse("n/a")

  def formatValueWithUnit(value: String, unit: String): String =
    if value == "n/a" || value.isEmpty then "n/a"
    else if unit.toLowerCase == "oz" then
      value.toDoubleOption match
        case Some(q) =>
          val grams = q * 28.3495
          val rounded = Math.round(grams * 100.0) / 100.0
          if rounded == rounded.toInt then s"${rounded.toInt} g" else s"$rounded g"
        case None => s"$value $unit"
    else if unit.nonEmpty && unit != "n/a" then
      s"$value $unit"
    else
      value

  def extractServingWeight(nutrition: js.Dynamic): Option[Double] =
    asArray(dynamicField(nutrition, "serving_size")).flatMap { obj =>
      val q = stringField(obj, "quantity").toDoubleOption
      val u = stringField(obj, "quantity_unit").toLowerCase
      q.map { value =>
        if u == "oz" then value * 28.3495
        else if u == "g" || u == "ml" then value
        else 0.0
      }
    }.find(_ > 0)

  def quantityWithUnit(obj: js.Dynamic): String =
    val rawQuantity = stringField(obj, "quantity")
    val unit = dynamicField(obj, "quantity_unit").map(stringify).getOrElse("")
    formatValueWithUnit(rawQuantity, unit)

  def quantityAndPer100WithUnit(obj: js.Dynamic, servingWeightG: Option[Double] = None): NutriValue =
    val rawQuantity = stringField(obj, "quantity")
    val rawPer100 = stringField(obj, "quantity_per_100")
    val unit = dynamicField(obj, "quantity_unit").map(stringify).getOrElse("")

    val servingFormatted = formatValueWithUnit(rawQuantity, unit)

    val per100Formatted =
      if rawPer100 != "n/a" && rawPer100.nonEmpty then
        formatValueWithUnit(rawPer100, unit)
      else
        servingWeightG.flatMap { weight =>
          rawQuantity.toDoubleOption.map { q =>
            val calculated = (q / weight) * 100.0
            val rounded = Math.round(calculated * 100.0) / 100.0
            formatValueWithUnit(rounded.toString, unit)
          }
        }.getOrElse("n/a")

    NutriValue(servingFormatted, per100Formatted)

  def findNutritionFacts(result: js.Dynamic): Option[js.Dynamic] =
    dynamicField(result, "nutrition_facts_label").orElse {
      dynamicField(result, "result").flatMap(res => dynamicField(res, "nutrition_facts_label"))
    }

  def isProcessingStatus(result: js.Dynamic): Boolean =
    val statusString = dynamicField(result, "status").map(stringify).getOrElse("").toLowerCase
    Set("queued", "pending", "processing", "running").contains(statusString)

  def hasCompletedResult(result: js.Dynamic): Boolean =
    findNutritionFacts(result).nonEmpty

  def extractNutritionFacts(result: js.Dynamic): NutritionFactsData =
    val nutrition = findNutritionFacts(result).getOrElse(result)
    val servingWeightG = extractServingWeight(nutrition)
    val servingSizeParts = asArray(dynamicField(nutrition, "serving_size")).map(quantityWithUnit).mkString(" / ")
    val carbs = dynamicField(nutrition, "carbs")
    val sugars = carbs.flatMap(c => dynamicField(c, "sugars"))
    val nutrients = asArray(dynamicField(nutrition, "nutrients")).map { n =>
      Nutrient(
        stringField(n, "name"),
        quantityAndPer100WithUnit(n, servingWeightG),
        stringField(n, "percentage_daily_value")
      )
    }

    val ingredientsList = asArray(dynamicField(result, "ingredients").orElse {
      dynamicField(result, "result").flatMap(res => dynamicField(res, "ingredients"))
    }).map(stringify).filter(_.nonEmpty)

    val rawCalories = stringField(nutrition, "calories")
    val rawCaloriesPer100 = stringField(nutrition, "calories_per_100")
    val caloriesPer100Formatted =
      if rawCaloriesPer100 != "n/a" && rawCaloriesPer100.nonEmpty then
        rawCaloriesPer100
      else
        servingWeightG.flatMap { weight =>
          rawCalories.toDoubleOption.map { cal =>
            val calculated = (cal / weight) * 100.0
            val rounded = Math.round(calculated * 10.0) / 10.0
            if rounded == rounded.toInt then rounded.toInt.toString else rounded.toString
          }
        }.getOrElse("n/a")

    NutritionFactsData(
      title = stringField(nutrition, "title"),
      servingsPerContainer = stringField(nutrition, "servings_per_container"),
      servingSize = if servingSizeParts.nonEmpty then servingSizeParts else "n/a",
      calories = NutriValue(rawCalories, caloriesPer100Formatted),
      totalFat = dynamicField(nutrition, "total_fat").map(quantityAndPer100WithUnit(_, servingWeightG)).getOrElse(NutriValue("n/a", "n/a")),
      saturatedFat = dynamicField(nutrition, "saturated_fat").map(quantityAndPer100WithUnit(_, servingWeightG)).getOrElse(NutriValue("n/a", "n/a")),
      transFat = dynamicField(nutrition, "trans_fat").map(quantityAndPer100WithUnit(_, servingWeightG)).getOrElse(NutriValue("n/a", "n/a")),
      cholesterol = dynamicField(nutrition, "cholesterol").map(quantityAndPer100WithUnit(_, servingWeightG)).getOrElse(NutriValue("n/a", "n/a")),
      sodium = dynamicField(nutrition, "sodium").map(quantityAndPer100WithUnit(_, servingWeightG)).getOrElse(NutriValue("n/a", "n/a")),
      totalCarbs = dynamicField(carbs.getOrElse(js.Dynamic.literal()), "total").map(quantityAndPer100WithUnit(_, servingWeightG)).getOrElse(NutriValue("n/a", "n/a")),
      dietaryFiber = dynamicField(carbs.getOrElse(js.Dynamic.literal()), "fiber").map(quantityAndPer100WithUnit(_, servingWeightG)).getOrElse(NutriValue("n/a", "n/a")),
      totalSugars = dynamicField(sugars.getOrElse(js.Dynamic.literal()), "total").map(quantityAndPer100WithUnit(_, servingWeightG)).getOrElse(NutriValue("n/a", "n/a")),
      addedSugars = dynamicField(sugars.getOrElse(js.Dynamic.literal()), "added").map(quantityAndPer100WithUnit(_, servingWeightG)).getOrElse(NutriValue("n/a", "n/a")),
      protein = dynamicField(nutrition, "protein").map(quantityAndPer100WithUnit(_, servingWeightG)).getOrElse(NutriValue("n/a", "n/a")),
      nutrients = nutrients,
      ingredients = if ingredientsList.nonEmpty then ingredientsList.mkString(", ") else "unknown",
      smallPrint = stringField(nutrition, "small_print")
    )
