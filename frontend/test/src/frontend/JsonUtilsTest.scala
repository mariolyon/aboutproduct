package frontend

import scala.scalajs.js
import munit.FunSuite

class JsonUtilsTest extends FunSuite {
  import JsonUtils.*

  test("isDefined returns false for undefined and null") {
    assert(!isDefined(js.undefined.asInstanceOf[js.Dynamic]))
    assert(!isDefined(null))
  }

  test("isDefined returns true for defined values") {
    assert(isDefined(js.Dynamic.literal(a = 1)))
    assert(isDefined("hello".asInstanceOf[js.Dynamic]))
    assert(isDefined(123.asInstanceOf[js.Dynamic]))
  }

  test("dynamicField extracts existing field") {
    val obj = js.Dynamic.literal(name = "test")
    val res = dynamicField(obj, "name")
    assert(res.isDefined)
    assert(res.get.asInstanceOf[String] == "test")
  }

  test("dynamicField returns None for missing field") {
    val obj = js.Dynamic.literal(name = "test")
    val res = dynamicField(obj, "missing")
    assert(res.isEmpty)
  }

  test("asArray converts js.Array to Seq") {
    val arr = js.Array("a", "b", "c").asInstanceOf[js.Dynamic]
    val res = asArray(Some(arr))
    assert(res.length == 3)
    assert(res.head.asInstanceOf[String] == "a")
  }

  test("asArray returns empty Seq for non-array") {
    val notArr = js.Dynamic.literal(a = 1)
    assert(asArray(Some(notArr)).isEmpty)
    assert(asArray(None).isEmpty)
  }

  test("stringify converts various types correctly") {
    assert(stringify(js.undefined.asInstanceOf[js.Dynamic]) == "n/a")
    assert(stringify("hello".asInstanceOf[js.Dynamic]) == "hello")
    assert(stringify(123.asInstanceOf[js.Dynamic]) == "123")
    assert(stringify(true.asInstanceOf[js.Dynamic]) == "true")
    assert(stringify(js.Dynamic.literal(a = 1)) == """{"a":1}""")
  }

  test("stringField returns field value or n/a") {
    val obj = js.Dynamic.literal(name = "test", num = 42)
    assert(stringField(obj, "name") == "test")
    assert(stringField(obj, "num") == "42")
    assert(stringField(obj, "missing") == "n/a")
  }

  test("quantityWithUnit formats correctly") {
    val obj1 = js.Dynamic.literal(quantity = 10, quantity_unit = "g")
    assert(quantityWithUnit(obj1) == "10 g")

    val obj2 = js.Dynamic.literal(quantity = "10")
    assert(quantityWithUnit(obj2) == "10")

    val obj3 = js.Dynamic.literal(quantity = 10, quantity_unit = "n/a")
    assert(quantityWithUnit(obj3) == "10")
  }

  test("findNutritionFacts finds nutrition_facts_label") {
    val direct = js.Dynamic.literal(nutrition_facts_label = js.Dynamic.literal(calories = 100))
    val nested = js.Dynamic.literal(result = direct)

    assert(findNutritionFacts(direct).isDefined)
    assert(findNutritionFacts(direct).get.selectDynamic("calories").asInstanceOf[Int] == 100)

    assert(findNutritionFacts(nested).isDefined)
    assert(findNutritionFacts(nested).get.selectDynamic("calories").asInstanceOf[Int] == 100)

    val empty = js.Dynamic.literal()
    assert(findNutritionFacts(empty).isEmpty)
  }

  test("isProcessingStatus recognizes processing statuses") {
    assert(isProcessingStatus(js.Dynamic.literal(status = "queued")))
    assert(isProcessingStatus(js.Dynamic.literal(status = "Pending")))
    assert(isProcessingStatus(js.Dynamic.literal(status = "PROCESSING")))
    assert(isProcessingStatus(js.Dynamic.literal(status = "running")))
    assert(!isProcessingStatus(js.Dynamic.literal(status = "completed")))
    assert(!isProcessingStatus(js.Dynamic.literal()))
  }

  test("hasCompletedResult returns true when facts exist") {
    val res = js.Dynamic.literal(nutrition_facts_label = js.Dynamic.literal())
    assert(hasCompletedResult(res))

    val notCompleted = js.Dynamic.literal(status = "processing")
    assert(!hasCompletedResult(notCompleted))
  }

  test("extractServingWeight calculates weight correctly") {
    val oz = js.Dynamic.literal(serving_size = js.Array(js.Dynamic.literal(quantity = "1", quantity_unit = "oz")))
    assert(extractServingWeight(oz).get == 28.3495)

    val g = js.Dynamic.literal(serving_size = js.Array(js.Dynamic.literal(quantity = "100", quantity_unit = "g")))
    assert(extractServingWeight(g).get == 100.0)

    val ml = js.Dynamic.literal(serving_size = js.Array(js.Dynamic.literal(quantity = "250", quantity_unit = "ml")))
    assert(extractServingWeight(ml).get == 250.0)

    val unknown = js.Dynamic.literal(serving_size = js.Array(js.Dynamic.literal(quantity = "1", quantity_unit = "cup")))
    assert(extractServingWeight(unknown).isEmpty)

    val missing = js.Dynamic.literal()
    assert(extractServingWeight(missing).isEmpty)
  }

  test("quantityAndPer100WithUnit calculates per 100 correctly") {
    val obj = js.Dynamic.literal(quantity = "10", quantity_unit = "g")

    // With 50g serving weight, 10g serving -> 20g per 100g
    val res1 = quantityAndPer100WithUnit(obj, Some(50.0))
    assert(res1.serving == "10 g")
    assert(res1.per100 == "20 g")

    // Without serving weight, per100 is n/a
    val res2 = quantityAndPer100WithUnit(obj, None)
    assert(res2.serving == "10 g")
    assert(res2.per100 == "n/a")

    // With explicit per 100
    val objExplicit = js.Dynamic.literal(quantity = "10", quantity_per_100 = "15", quantity_unit = "g")
    val res3 = quantityAndPer100WithUnit(objExplicit, Some(50.0))
    assert(res3.serving == "10 g")
    assert(res3.per100 == "15 g")
  }

  test("extractNutritionFacts extracts all fields correctly") {
    val input = js.Dynamic.literal(
      nutrition_facts_label = js.Dynamic.literal(
        title = "Milk",
        servings_per_container = "1",
        calories = "150",
        serving_size = js.Array(js.Dynamic.literal(quantity = "1", quantity_unit = "cup")),
        total_fat = js.Dynamic.literal(quantity = "8", quantity_unit = "g"),
        carbs = js.Dynamic.literal(
          total = js.Dynamic.literal(quantity = "12", quantity_unit = "g"),
          sugars = js.Dynamic.literal(total = js.Dynamic.literal(quantity = "12", quantity_unit = "g"))
        ),
        nutrients = js.Array(
          js.Dynamic.literal(
            name = "Calcium",
            quantity = "300",
            quantity_unit = "mg",
            percentage_daily_value = "25"
          )
        )
      ),
      ingredients = js.Array("Milk", "Vitamin D3")
    )

    val res = extractNutritionFacts(input)
    assert(res.title == "Milk")
    assert(res.calories.serving == "150")
    assert(res.servingSize == "1 cup")
    assert(res.totalFat.serving == "8 g")
    assert(res.totalCarbs.serving == "12 g")
    assert(res.totalSugars.serving == "12 g")
    assert(res.ingredients == "Milk, Vitamin D3")
    assert(res.nutrients.length == 1)
    assert(res.nutrients.head.name == "Calcium")
    assert(res.nutrients.head.quantity.serving == "300 mg")
    assert(res.nutrients.head.percentage == "25")
  }

  test("extractNutritionFacts handles nested ingredients") {
    val input = js.Dynamic.literal(
      result = js.Dynamic.literal(
        nutrition_facts_label = js.Dynamic.literal(title = "Bread"),
        ingredients = js.Array("Flour", "Water")
      )
    )
    val res = extractNutritionFacts(input)
    assert(res.ingredients == "Flour, Water")
  }

  test("extractNutritionFacts extracts per100 fields correctly") {
    val input = js.Dynamic.literal(
      nutrition_facts_label = js.Dynamic.literal(
        title = "Milk",
        calories = "150",
        calories_per_100 = "60",
        total_fat = js.Dynamic.literal(
          quantity = "8",
          quantity_per_100 = "3.2",
          quantity_unit = "g"
        )
      )
    )

    val res = extractNutritionFacts(input)
    assert(res.calories.serving == "150")
    assert(res.calories.per100 == "60")
    assert(res.totalFat.serving == "8 g")
    assert(res.totalFat.per100 == "3.2 g")
  }

  test("extractNutritionFacts calculates missing per100 fields relative to serving weight") {
    val input = js.Dynamic.literal(
      nutrition_facts_label = js.Dynamic.literal(
        title = "Juice",
        calories = "50",
        serving_size = js.Array(js.Dynamic.literal(quantity = "200", quantity_unit = "ml")),
        total_fat = js.Dynamic.literal(
          quantity = "0.5",
          quantity_unit = "g"
        )
      )
    )

    val res = extractNutritionFacts(input)
    // calories per 100ml: (50 / 200) * 100 = 25
    assert(res.calories.serving == "50")
    assert(res.calories.per100 == "25")
    // fat per 100ml: (0.5 / 200) * 100 = 0.25
    assert(res.totalFat.serving == "0.5 g")
    assert(res.totalFat.per100 == "0.25 g")
  }

  test("extractNutritionFacts calculates missing per100 fields for oz serving size") {
    val input = js.Dynamic.literal(
      nutrition_facts_label = js.Dynamic.literal(
        title = "Snack",
        calories = "100",
        serving_size = js.Array(js.Dynamic.literal(quantity = "1", quantity_unit = "oz")),
        total_fat = js.Dynamic.literal(
          quantity = "5",
          quantity_unit = "g"
        )
      )
    )

    val res = extractNutritionFacts(input)
    // 1 oz approx 28.35g
    // calories per 100g: (100 / 28.3495) * 100 approx 352.7
    assert(res.calories.serving == "100")
    assert(res.calories.per100 == "352.7")
    // fat per 100g: (5 / 28.3495) * 100 approx 17.64
    assert(res.totalFat.serving == "5 g")
    assert(res.totalFat.per100 == "17.64 g")
  }

  test("extractNutritionFacts handles missing ingredients") {
    val input = js.Dynamic.literal(
      nutrition_facts_label = js.Dynamic.literal(title = "Water")
    )
    val res = extractNutritionFacts(input)
    assert(res.ingredients == "unknown")
  }
}
