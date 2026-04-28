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
  val imagePreviewUrl = Var(Option.empty[String])
  val activeJobId = Var(Option.empty[String])
  val extractionResult = Var(Option.empty[js.Dynamic])

  val appStyles =
    """
      |.image-preview {
      |  margin: 1rem auto 0;
      |  display: block;
      |  max-width: min(280px, 90vw);
      |  max-height: 220px;
      |  border: 2px solid #111827;
      |  border-radius: 8px;
      |  object-fit: contain;
      |  background: #fff;
      |  padding: 0.25rem;
      |}
      |.nutrition-card {
      |  margin: 1.5rem auto 0;
      |  width: min(520px, 100%);
      |  background: #ffffff;
      |  border: 2px solid #111;
      |  border-radius: 0;
      |  padding: 0.75rem;
      |  text-align: left;
      |  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.08);
      |}
      |.nutrition-card h2 {
      |  margin: 0 0 0.5rem;
      |  font-size: 3rem;
      |  font-weight: 800;
      |  line-height: 1.1;
      |  border-bottom: 2px solid #111;
      |  padding-bottom: 0.4rem;
      |}
      |.nf-subtitle {
      |  font-size: 0.9rem;
      |  margin-bottom: 0.1rem;
      |  font-weight: 500;
      |}
      |.nf-row {
      |  display: flex;
      |  justify-content: space-between;
      |  gap: 1rem;
      |  border-bottom: 1px solid #9ca3af;
      |  padding: 0.28rem 0;
      |  font-size: 1.05rem;
      |}
      |.nf-label {
      |  font-weight: 600;
      |}
      |.nf-row.major .nf-label,
      |.nf-row.serving-size .nf-label {
      |  font-weight: 800;
      |}
      |.nf-row.indent {
      |  padding-left: 1.8rem;
      |}
      |.nf-row.indent-2 {
      |  padding-left: 3.2rem;
      |}
      |.nf-value {
      |  text-align: right;
      |  white-space: nowrap;
      |}
      |.nf-thick-divider {
      |  border-top: 10px solid #111;
      |  margin: 0.2rem 0;
      |}
      |.nf-amount {
      |  margin-top: 0.25rem;
      |  font-size: 1rem;
      |  font-weight: 700;
      |}
      |.nf-calories-row {
      |  display: flex;
      |  justify-content: space-between;
      |  align-items: baseline;
      |  line-height: 1;
      |  margin: 0.15rem 0 0.25rem;
      |}
      |.nf-calories-label {
      |  font-size: 3rem;
      |  font-weight: 800;
      |}
      |.nf-calories-value {
      |  font-size: 4.4rem;
      |  font-weight: 800;
      |}
      |.nf-dv-header {
      |  text-align: right;
      |  font-size: 1.7rem;
      |  font-weight: 800;
      |  border-bottom: 2px solid #111;
      |  padding: 0.1rem 0 0.2rem;
      |  margin-bottom: 0.05rem;
      |}
      |.nf-small-print {
      |  margin-top: 0.75rem;
      |  font-size: 0.82rem;
      |  line-height: 1.3;
      |  color: #374151;
      |}
      |""".stripMargin

  def injectAppStyles(): Unit =
    val styleTag = dom.document.createElement("style")
    styleTag.textContent = appStyles
    dom.document.head.appendChild(styleTag)

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

  def row(labelText: String, valueText: String, rowClass: String = ""): HtmlElement =
    div(
      className := s"nf-row $rowClass".trim,
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
    val calories = stringField(nutrition, "calories")

    div(
      cls := "nutrition-card",
      h2(stringField(nutrition, "title")),
      div(cls := "nf-subtitle", s"${stringField(nutrition, "servings_per_container")} servings per container"),
      row("Serving size", if servingSizeParts.nonEmpty then servingSizeParts else "n/a", "serving-size"),
      div(cls := "nf-thick-divider"),
      div(
        cls := "nf-amount",
        "Amount per serving"
      ),
      div(
        cls := "nf-calories-row",
        span(cls := "nf-calories-label", "Calories"),
        span(cls := "nf-calories-value", calories)
      ),
      div(cls := "nf-thick-divider"),
      div(
        cls := "nf-dv-header",
        "% Daily Value*"
      ),
      row("Total Fat", dynamicField(nutrition, "total_fat").map(quantityWithUnit).getOrElse("n/a"), "major"),
      row("Saturated Fat", dynamicField(nutrition, "saturated_fat").map(quantityWithUnit).getOrElse("n/a"), "indent"),
      row("Trans Fat", dynamicField(nutrition, "trans_fat").map(quantityWithUnit).getOrElse("n/a"), "indent"),
      row("Cholesterol", dynamicField(nutrition, "cholesterol").map(quantityWithUnit).getOrElse("n/a"), "major"),
      row("Sodium", dynamicField(nutrition, "sodium").map(quantityWithUnit).getOrElse("n/a"), "major"),
      row("Total Carbohydrate", totalCarbs.map(quantityWithUnit).getOrElse("n/a"), "major"),
      row("Dietary Fiber", fiber.map(quantityWithUnit).getOrElse("n/a"), "indent"),
      row("Total Sugars", totalSugars.map(quantityWithUnit).getOrElse("n/a"), "indent"),
      row("Includes Added Sugars", addedSugars.map(quantityWithUnit).getOrElse("n/a"), "indent-2"),
      row("Protein", dynamicField(nutrition, "protein").map(quantityWithUnit).getOrElse("n/a"), "major"),
      div(cls := "nf-thick-divider"),
      if nutrients.nonEmpty then
        nutrients.map(nutrient =>
          row(
            stringField(nutrient, "name"),
            s"${quantityWithUnit(nutrient)} (${stringField(nutrient, "percentage_daily_value")}% DV)"
          )
        )
      else
        emptyNode,
      p(cls := "nf-small-print", stringField(nutrition, "small_print"))
    )

  def pollJobStatus(jobId: String): Unit =
    val pollDelayMs = 10000
    val maxPollingDurationMs = 5 * 60 * 1000
    val startedAtMs = js.Date.now()

    def continuePolling(): Unit =
      if activeJobId.now().contains(jobId) then
        setTimeout(pollDelayMs):
          pollOnce()

    def pollOnce(): Unit =
      if !activeJobId.now().contains(jobId) then ()
      else if js.Date.now() - startedAtMs > maxPollingDurationMs then
        activeJobId.set(None)
        status.set("Failed")
      else
        val future = dom.fetch(s"/api/jobs/$jobId").toFuture

        future.foreach { response =>
          response.status.toInt match
            case 200 =>
              val payloadFuture = response.text().toFuture
              payloadFuture.foreach { payload =>
                try
                  val parsed = js.JSON.parse(payload).asInstanceOf[js.Dynamic]
                  if hasCompletedResult(parsed) then
                    extractionResult.set(Some(parsed))
                    activeJobId.set(None)
                    status.set("Read complete")
                  else if isProcessingStatus(parsed) then
                    status.set("processing ...")
                    continuePolling()
                  else
                    status.set("processing ...")
                    continuePolling()
                catch
                  case _: Throwable =>
                    activeJobId.set(None)
                    status.set("Failed")
              }
              payloadFuture.failed.foreach { _ =>
                activeJobId.set(None)
                status.set("Failed")
              }
            case 204 =>
              status.set("processing ...")
              continuePolling()
            case _ =>
              activeJobId.set(None)
              status.set("Failed")
        }

        future.failed.foreach { _ =>
          activeJobId.set(None)
          status.set("Failed")
        }

    pollOnce()

  def setSelectedImage(maybeFile: Option[dom.File]): Unit =
    imagePreviewUrl.now().foreach(dom.URL.revokeObjectURL)
    selectedImage.set(maybeFile)
    imagePreviewUrl.set(maybeFile.map(file => dom.URL.createObjectURL(file)))

  def readImage(): Unit =
    selectedImage.now() match
      case Some(image) =>
        extractionResult.set(None)
        activeJobId.set(None)
        status.set("uploading ...")

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
              status.set("Failed")
            else
              activeJobId.set(Some(normalizedJobId))
              status.set("processing ...")
              pollJobStatus(normalizedJobId)
          case Left(_) => status.set("Failed")
        }
        future.failed.foreach(_ => status.set("Failed"))
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
          setSelectedImage(Option(files).flatMap(fileList => Option(fileList.item(0))))
        }
      ),
      button(
        "Read",
        onClick --> (_ => readImage())
      )
    ),
    p(child.text <-- selectedImageNameSignal),
    child.maybe <-- imagePreviewUrl.signal.map(
      _.map(url => img(cls := "image-preview", src := url, alt := "Selected image preview"))
    ),
    child.maybe <-- extractionResult.signal.map(_.map(renderNutritionFacts))
  )

  renderOnDomContentLoaded(
    dom.document.getElementById("app"),
    {
      injectAppStyles()
      appElement
    }
  )
