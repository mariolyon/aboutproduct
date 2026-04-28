package frontend

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom

import scala.scalajs.js.Thenable.Implicits.*
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.timers.setTimeout

@main def app(): Unit =
  val status = Var("Read")
  val selectedImage = Var(Option.empty[dom.File])
  val activeJobId = Var(Option.empty[String])
  val extractionResult = Var(Option.empty[js.Dynamic])

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

  def quantityWithUnit(obj: js.Dynamic): String =
    val quantity = stringField(obj, "quantity")
    val unit = dynamicField(obj, "quantity_unit").map(stringify).getOrElse("")
    if unit.nonEmpty && unit != "n/a" then s"$quantity $unit" else quantity

  def findNutritionFacts(result: js.Dynamic): Option[js.Dynamic] =
    dynamicField(result, "nutrition_facts_label").orElse {
      dynamicField(result, "result").flatMap(res => dynamicField(res, "nutrition_facts_label"))
    }

  def isProcessingStatus(result: js.Dynamic): Boolean =
    val statusString = dynamicField(result, "status").map(stringify).getOrElse("").toLowerCase
    Set("queued", "pending", "processing", "running").contains(statusString)

  def hasCompletedResult(result: js.Dynamic): Boolean =
    findNutritionFacts(result).nonEmpty

  def row(labelText: String, valueText: String): HtmlElement =
    div(
      cls := "nf-row",
      span(cls := "nf-label", labelText),
      span(cls := "nf-value", valueText)
    )

  def renderNutritionFacts(result: js.Dynamic): HtmlElement =
    val nutrition = findNutritionFacts(result).getOrElse(result)
    val servingSizeParts = asArray(dynamicField(nutrition, "serving_size")).map(quantityWithUnit).mkString(" / ")
    val carbs = dynamicField(nutrition, "carbs")
    val sugars = carbs.flatMap(c => dynamicField(c, "sugars"))
    val totalCarbs = carbs.flatMap(c => dynamicField(c, "total"))
    val fiber = carbs.flatMap(c => dynamicField(c, "fiber"))
    val totalSugars = sugars.flatMap(s => dynamicField(s, "total"))
    val addedSugars = sugars.flatMap(s => dynamicField(s, "added"))
    val nutrients = asArray(dynamicField(nutrition, "nutrients"))

    div(
      cls := "nutrition-card",
      h2(stringField(nutrition, "title")),
      div(cls := "nf-subtitle", s"Servings per container: ${stringField(nutrition, "servings_per_container")}"),
      row("Serving size", if servingSizeParts.nonEmpty then servingSizeParts else "n/a"),
      row("Calories", stringField(nutrition, "calories")),
      row("Total Fat", dynamicField(nutrition, "total_fat").map(quantityWithUnit).getOrElse("n/a")),
      row("Saturated Fat", dynamicField(nutrition, "saturated_fat").map(quantityWithUnit).getOrElse("n/a")),
      row("Trans Fat", dynamicField(nutrition, "trans_fat").map(quantityWithUnit).getOrElse("n/a")),
      row("Cholesterol", dynamicField(nutrition, "cholesterol").map(quantityWithUnit).getOrElse("n/a")),
      row("Sodium", dynamicField(nutrition, "sodium").map(quantityWithUnit).getOrElse("n/a")),
      row("Total Carbohydrate", totalCarbs.map(quantityWithUnit).getOrElse("n/a")),
      row("Dietary Fiber", fiber.map(quantityWithUnit).getOrElse("n/a")),
      row("Total Sugars", totalSugars.map(quantityWithUnit).getOrElse("n/a")),
      row("Includes Added Sugars", addedSugars.map(quantityWithUnit).getOrElse("n/a")),
      row("Protein", dynamicField(nutrition, "protein").map(quantityWithUnit).getOrElse("n/a")),
      if nutrients.nonEmpty then
        div(
          cls := "nf-section-title",
          "Vitamins and Minerals"
        )
      else
        emptyNode,
      nutrients.map(nutrient =>
        row(
          stringField(nutrient, "name"),
          s"${quantityWithUnit(nutrient)} (${stringField(nutrient, "percentage_daily_value")}% DV)"
        )
      ),
      p(cls := "nf-small-print", stringField(nutrition, "small_print"))
    )

  def pollJobStatus(jobId: String): Unit =
    val pollDelayMs = 2000

    def continuePolling(): Unit =
      if activeJobId.now().contains(jobId) then
        setTimeout(pollDelayMs):
          pollOnce()

    def pollOnce(): Unit =
      if !activeJobId.now().contains(jobId) then ()
      else
        val future = for
          response <- dom.fetch(s"/api/jobs/$jobId").toFuture
          result <- if response.ok then
            response.text().toFuture.map(Right(_))
          else
            Future.successful(Left(response.status.toInt))
        yield result

        future.foreach {
          case Right(payload) =>
            try
              val parsed = js.JSON.parse(payload).asInstanceOf[js.Dynamic]
              if hasCompletedResult(parsed) then
                extractionResult.set(Some(parsed))
                activeJobId.set(None)
                status.set("Extraction complete")
              else if isProcessingStatus(parsed) then
                status.set("Processing extraction...")
                continuePolling()
              else
                status.set("Waiting for extraction result...")
                continuePolling()
            catch
              case _: Throwable =>
                activeJobId.set(None)
                status.set("Failed to parse extraction status response")
          case Left(statusCode) =>
            activeJobId.set(None)
            status.set(s"Status check failed (HTTP $statusCode)")
        }

        future.failed.foreach { _ =>
          activeJobId.set(None)
          status.set("Status check failed")
        }

    pollOnce()

  def readImage(): Unit =
    selectedImage.now() match
      case Some(image) =>
        extractionResult.set(None)
        activeJobId.set(None)
        status.set("Uploading image...")
        setTimeout(500):
          if status.now() == "Uploading image..." then
            status.set("Creating extraction job...")

        val contentType = image.`type`
        val requestInit = js.Dynamic
          .literal(
            method = "POST",
            headers = js.Dynamic.literal(
              "Content-Type" -> (if contentType.nonEmpty then contentType else "application/octet-stream")
            ),
            body = image
          )
          .asInstanceOf[dom.RequestInit]

        val future = for
          response <- dom.fetch("/api/jobs", requestInit).toFuture
          result <- if response.ok then
            response.text().toFuture.map(Right(_))
          else
            Future.successful(Left(response.status.toInt))
        yield result

        future.foreach {
          case Right(jobId) =>
            val normalizedJobId = jobId.trim
            if normalizedJobId.isEmpty then
              status.set("Extraction job creation returned an empty job id")
            else
              activeJobId.set(Some(normalizedJobId))
              status.set(s"Job created: $normalizedJobId")
              pollJobStatus(normalizedJobId)
          case Left(statusCode) => status.set(s"Read failed (HTTP $statusCode)")
        }
        future.failed.foreach(_ => status.set("Read failed"))
      case None =>
        status.set("Select an image first")

  val selectedImageNameSignal = selectedImage.signal.map(
    _.map(_.name).getOrElse("No image selected")
  )

  val appElement = div(
    h1(child.text <-- status.signal),
    div(
      cls := "upload-row",
      input(
        typ := "file",
        accept := "image/*",
        onChange --> { event =>
          val files = event.target.asInstanceOf[dom.HTMLInputElement].files
          selectedImage.set(Option(files).flatMap(fileList => Option(fileList.item(0))))
        }
      ),
      button(
        "Read",
        onClick --> (_ => readImage())
      )
    ),
    p(child.text <-- selectedImageNameSignal),
    child.maybe <-- extractionResult.signal.map(_.map(renderNutritionFacts))
  )

  renderOnDomContentLoaded(dom.document.getElementById("app"), appElement)
