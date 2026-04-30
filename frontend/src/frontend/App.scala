package frontend

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom

import scala.scalajs.js.Thenable.Implicits.*
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.timers.setTimeout

@main def app(): Unit =
  val status = Var("")
  val selectedImage = Var(Option.empty[dom.File])
  val imagePreviewUrl = Var(Option.empty[String])
  val activeJobId = Var(Option.empty[String])
  val extractionResult = Var(Option.empty[js.Dynamic])
  val cameraActive = Var(false)
  val videoStreamRef = Var(Option.empty[dom.MediaStream])
  val cameraVideoElement = Var(Option.empty[dom.HTMLVideoElement])

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

  def stopCamera(): Unit =
    videoStreamRef.now().foreach { stream =>
      stream.getTracks().foreach(_.stop())
    }
    videoStreamRef.set(None)
    cameraActive.set(false)
    cameraVideoElement.set(None)

  def startCamera(): Unit =
    val mediaDevices = dom.window.navigator.mediaDevices
    if js.isUndefined(mediaDevices) || mediaDevices == null then
      status.set("Camera not available")
    else
      stopCamera()
      val constraints = js.Dynamic
        .literal(
          video = js.Dynamic.literal(
            facingMode = js.Dynamic.literal(ideal = "environment")
          ),
          audio = false
        )
        .asInstanceOf[dom.MediaStreamConstraints]

      val cameraFuture = mediaDevices.getUserMedia(constraints).toFuture

      cameraFuture.foreach { stream =>
          videoStreamRef.set(Some(stream))
          cameraActive.set(true)
          status.set("Camera ready")
      }

      cameraFuture.failed.foreach { _ =>
        status.set("Camera access denied")
      }

  def captureFrame(video: dom.HTMLVideoElement): Unit =
    val width = if video.videoWidth > 0 then video.videoWidth else 1280
    val height = if video.videoHeight > 0 then video.videoHeight else 720
    val canvas = dom.document.createElement("canvas").asInstanceOf[dom.HTMLCanvasElement]
    canvas.width = width
    canvas.height = height
    val maybeContext = Option(canvas.getContext("2d")).map(_.asInstanceOf[dom.CanvasRenderingContext2D])

    maybeContext match
      case Some(context) =>
        context.drawImage(video, 0, 0, width, height)
        val dataUrl = canvas.toDataURL("image/jpeg", 0.92)
        val blobFuture = for
          response <- dom.fetch(dataUrl).toFuture
          blob <- response.blob().toFuture
        yield blob

        blobFuture.foreach { imageBlob =>
          val file = new dom.File(
            js.Array(imageBlob),
            "camera-photo.jpg",
            js.Dynamic.literal(
              `type` = "image/jpeg"
            ).asInstanceOf[dom.FilePropertyBag]
          )
          setSelectedImage(Some(file))
          status.set("Photo captured")
          stopCamera()
          readImage()
        }
        blobFuture.failed.foreach { _ =>
          status.set("Failed")
          stopCamera()
        }
      case None =>
        status.set("Failed")
        stopCamera()

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
    _.map(_.name).getOrElse("Select image to begin")
  )

  val appElement = div(
    div(
      cls := "app-banner",
      h1("AboutProduct"),
      p(cls := "app-blurb", "AI-powered nutrition insights from any label.")
    ),
    div(
      cls := "action-bar",
      // Upload Button
      div(
        cls := "file-input-wrapper",
        div(
          cls := "action-btn",
          svg.svg(
            svg.viewBox := "0 0 24 24",
            svg.fill := "none",
            svg.stroke := "currentColor",
            svg.strokeWidth := "2",
            svg.path(svg.d := "M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12")
          ),
          "Upload"
        ),
        input(
          typ := "file",
          accept := "image/*",
          onChange --> { event =>
            val files = event.target.asInstanceOf[dom.HTMLInputElement].files
            stopCamera()
            val maybeFile = Option(files).flatMap(fileList => Option(fileList.item(0)))
            setSelectedImage(maybeFile)
            maybeFile.foreach(_ => readImage())
          }
        )
      ),
      // Camera Button
      button(
        cls := "action-btn",
        onClick --> (_ => startCamera()),
        svg.svg(
          svg.viewBox := "0 0 24 24",
          svg.fill := "none",
          svg.stroke := "currentColor",
          svg.strokeWidth := "2",
          svg.path(svg.d := "M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z"),
          svg.circle(svg.cx := "12", svg.cy := "13", svg.r := "3")
        ),
        "Take Photo"
      )
    ),
    child.maybe <-- status.signal.map { s =>
      Option.when(s.nonEmpty)(h2(cls := "status-text", s))
    },
    child.maybe <-- cameraActive.signal.map { active =>
      Option.when(active) {
        div(
          cls := "camera-viewfinder",
          htmlTag("video")(
            onMountCallback { ctx =>
              val videoElement = ctx.thisNode.ref.asInstanceOf[dom.HTMLVideoElement]
              videoElement.autoplay = true
              videoElement.muted = true
              videoElement.setAttribute("playsinline", "true")
              cameraVideoElement.set(Some(videoElement))
            },
            onUnmountCallback(_ => cameraVideoElement.set(None)),
            inContext { thisNode =>
              videoStreamRef.signal --> { maybeStream =>
                thisNode.ref.asInstanceOf[js.Dynamic].updateDynamic("srcObject")(maybeStream.orNull)
              }
            }
          ),
          div(
            cls := "camera-controls",
            button(
              "Capture",
              onClick --> (_ =>
                cameraVideoElement.now() match
                  case Some(videoElement) => captureFrame(videoElement)
                  case None               => status.set("Camera not ready")
              )
            ),
            button(
              "Cancel",
              onClick --> (_ => stopCamera())
            )
          )
        )
      }
    },
    p(child.text <-- selectedImageNameSignal),
    div(
      cls := "results-container",
      child.maybe <-- imagePreviewUrl.signal.map(
        _.map(url => img(cls := "image-preview", src := url, alt := "Selected image preview"))
      ),
      child.maybe <-- extractionResult.signal.map(_.map(renderNutritionFacts))
    )
  )

  renderOnDomContentLoaded(
    dom.document.getElementById("app"),
    appElement
  )
