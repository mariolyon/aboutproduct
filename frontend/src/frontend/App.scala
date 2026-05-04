package frontend

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom

import scala.scalajs.js.Thenable.Implicits.*
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.timers.setTimeout

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

  case class Nutrient(name: String, quantity: String, percentage: String)
  case class NutritionFactsData(
    title: String,
    servingsPerContainer: String,
    servingSize: String,
    calories: String,
    totalFat: String,
    saturatedFat: String,
    transFat: String,
    cholesterol: String,
    sodium: String,
    totalCarbs: String,
    dietaryFiber: String,
    totalSugars: String,
    addedSugars: String,
    protein: String,
    nutrients: Seq[Nutrient],
    smallPrint: String
  )

  def extractNutritionFacts(result: js.Dynamic): NutritionFactsData =
    val nutrition = findNutritionFacts(result).getOrElse(result)
    val servingSizeParts = asArray(dynamicField(nutrition, "serving_size")).map(quantityWithUnit).mkString(" / ")
    val carbs = dynamicField(nutrition, "carbs")
    val sugars = carbs.flatMap(c => dynamicField(c, "sugars"))
    val nutrients = asArray(dynamicField(nutrition, "nutrients")).map { n =>
      Nutrient(
        stringField(n, "name"),
        quantityWithUnit(n),
        stringField(n, "percentage_daily_value")
      )
    }

    NutritionFactsData(
      title = stringField(nutrition, "title"),
      servingsPerContainer = stringField(nutrition, "servings_per_container"),
      servingSize = if servingSizeParts.nonEmpty then servingSizeParts else "n/a",
      calories = stringField(nutrition, "calories"),
      totalFat = dynamicField(nutrition, "total_fat").map(quantityWithUnit).getOrElse("n/a"),
      saturatedFat = dynamicField(nutrition, "saturated_fat").map(quantityWithUnit).getOrElse("n/a"),
      transFat = dynamicField(nutrition, "trans_fat").map(quantityWithUnit).getOrElse("n/a"),
      cholesterol = dynamicField(nutrition, "cholesterol").map(quantityWithUnit).getOrElse("n/a"),
      sodium = dynamicField(nutrition, "sodium").map(quantityWithUnit).getOrElse("n/a"),
      totalCarbs = dynamicField(carbs.getOrElse(js.Dynamic.literal()), "total").map(quantityWithUnit).getOrElse("n/a"),
      dietaryFiber = dynamicField(carbs.getOrElse(js.Dynamic.literal()), "fiber").map(quantityWithUnit).getOrElse("n/a"),
      totalSugars = dynamicField(sugars.getOrElse(js.Dynamic.literal()), "total").map(quantityWithUnit).getOrElse("n/a"),
      addedSugars = dynamicField(sugars.getOrElse(js.Dynamic.literal()), "added").map(quantityWithUnit).getOrElse("n/a"),
      protein = dynamicField(nutrition, "protein").map(quantityWithUnit).getOrElse("n/a"),
      nutrients = nutrients,
      smallPrint = stringField(nutrition, "small_print")
    )

  case class HistoryItem(id: String, timestamp: Double, title: String, dataStr: String) {
    def parsedData: js.Dynamic = js.JSON.parse(dataStr).asInstanceOf[js.Dynamic]
  }

  object StorageUtils:
    val HistoryKey = "aboutproduct_history"

    def loadHistory(): Seq[HistoryItem] =
      val stored = dom.window.localStorage.getItem(HistoryKey)
      if stored != null && stored.nonEmpty then
        try
          val arr = js.JSON.parse(stored).asInstanceOf[js.Array[js.Dynamic]]
          arr.toSeq.map { item =>
            HistoryItem(
              id = item.selectDynamic("id").asInstanceOf[String],
              timestamp = item.selectDynamic("timestamp").asInstanceOf[Double],
              title = item.selectDynamic("title").asInstanceOf[String],
              dataStr = item.selectDynamic("dataStr").asInstanceOf[String]
            )
          }
        catch case _ => Seq.empty
      else Seq.empty

    def saveHistory(items: Seq[HistoryItem]): Unit =
      val jsArray = js.Array(items.map { item =>
        js.Dynamic.literal(
          id = item.id,
          timestamp = item.timestamp,
          title = item.title,
          dataStr = item.dataStr
        )
      }: _*)
      dom.window.localStorage.setItem(HistoryKey, js.JSON.stringify(jsArray))

object Components:
  import JsonUtils.*

  def row(labelText: String, valueText: String, rowClass: String = ""): HtmlElement =
    div(
      className := s"nf-row $rowClass".trim,
      span(cls := "nf-label", labelText),
      span(cls := "nf-value", valueText)
    )

  def renderNutritionFacts(result: js.Dynamic): HtmlElement =
    val data = extractNutritionFacts(result)

    div(
      cls := "nutrition-card",
      h2(data.title),
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
        span(cls := "nf-calories-value", data.calories)
      ),
      div(cls := "nf-thick-divider"),
      div(
        cls := "nf-dv-header",
        "% Daily Value*"
      ),
      row("Total Fat", data.totalFat, "major"),
      row("Saturated Fat", data.saturatedFat, "indent"),
      row("Trans Fat", data.transFat, "indent"),
      row("Cholesterol", data.cholesterol, "major"),
      row("Sodium", data.sodium, "major"),
      row("Total Carbohydrate", data.totalCarbs, "major"),
      row("Dietary Fiber", data.dietaryFiber, "indent"),
      row("Total Sugars", data.totalSugars, "indent"),
      row("Includes Added Sugars", data.addedSugars, "indent-2"),
      row("Protein", data.protein, "major"),
      div(cls := "nf-thick-divider"),
      if data.nutrients.nonEmpty then
        data.nutrients.map(nutrient =>
          row(
            nutrient.name,
            s"${nutrient.quantity} (${nutrient.percentage}% DV)"
          )
        )
      else
        emptyNode,
      p(cls := "nf-small-print", data.smallPrint)
    )

@main def app(): Unit =
  import JsonUtils.*
  val status = Var("Select image to begin")
  val selectedImage = Var(Option.empty[dom.File])
  val imagePreviewUrl = Var(Option.empty[String])
  val activeJobId = Var(Option.empty[String])
  val extractionResult = Var(Option.empty[js.Dynamic])
  val scanHistory = Var(StorageUtils.loadHistory())
  val showHistory = Var(false)
  val cameraActive = Var(false)
  val videoStreamRef = Var(Option.empty[dom.MediaStream])
  val cameraVideoElement = Var(Option.empty[dom.HTMLVideoElement])

  import JsonUtils.*
  import Components.*

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
                    status.set("Analysis complete")

                    // Save to history
                    val nutritionFacts = extractNutritionFacts(parsed)
                    val title = nutritionFacts.title
                    val newItem = HistoryItem(
                      id = jobId,
                      timestamp = js.Date.now(),
                      title = if title.nonEmpty && title != "n/a" then title else s"Scan ${new js.Date().toLocaleTimeString()}",
                      dataStr = js.JSON.stringify(parsed)
                    )
                    scanHistory.update { history =>
                      val updated = newItem +: history.take(19) // Keep last 20
                      StorageUtils.saveHistory(updated)
                      updated
                    }
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

  val appElement = div(
    cls := "max-w-[1200px] w-[96vw] mx-auto text-center",
    div(
      cls := "app-banner mb-6 p-4 bg-gray-900 text-gray-50 rounded-b-xl shadow-md",
      h1(cls := "text-3xl font-extrabold mb-1 text-white tracking-tight", "AboutProduct"),
      p(cls := "app-blurb text-gray-400 max-w-2xl mx-auto leading-relaxed", "AI-powered nutrition insights from any food label.")
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
      ),
      // History Button
      button(
        cls := "action-btn",
        onClick --> (_ => showHistory.update(!_)),
        svg.svg(
          svg.viewBox := "0 0 24 24",
          svg.fill := "none",
          svg.stroke := "currentColor",
          svg.strokeWidth := "2",
          svg.path(svg.d := "M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z")
        ),
        "History"
      )
    ),
    child.maybe <-- showHistory.signal.combineWith(scanHistory.signal).map { case (show, history) =>
      Option.when(show) {
        div(
          cls := "bg-white border border-gray-200 rounded-xl shadow-lg p-6 mb-6 max-w-2xl mx-auto text-left",
          h3(cls := "text-lg font-bold mb-4", "Scan History"),
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
                    span(cls := "text-xs text-gray-500", new js.Date(item.timestamp).toLocaleString())
                  ),
                  div(
                    cls := "flex gap-2",
                    button(
                      cls := "action-btn px-3 py-1 text-xs min-w-0",
                      "View",
                      onClick --> { _ =>
                        extractionResult.set(Some(item.parsedData))
                        showHistory.set(false)
                      }
                    )
                  )
                )
              }
            )
        )
      }
    },
    child.maybe <-- status.signal.combineWith(selectedImage.signal).map { case (s, imgOpt) =>
      Option.when(s.nonEmpty && imgOpt.isEmpty)(h2(cls := "status-text", s))
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
    div(
      cls := "flex flex-col lg:flex-row flex-wrap justify-center items-start gap-8 my-6 w-full",
      child.maybe <-- imagePreviewUrl.signal.combineWith(selectedImage.signal).map {
        case (Some(url), Some(file)) =>
          Some(
            div(
              cls := "flex-1 min-w-[380px] lg:max-w-[calc(50%-1rem)] w-full flex flex-col items-center p-6 border border-gray-200 rounded-xl bg-white shadow-sm mx-auto lg:mx-0",
              child.maybe <-- status.signal.map { s =>
                Option.when(s.nonEmpty)(h2(cls := "status-text", s))
              },
              div(cls := "image-filename mb-3 text-sm text-gray-500 break-all font-medium text-left w-full", file.name),
              img(cls := "image-preview w-full max-h-[70vh] block border border-gray-300 rounded-lg object-contain bg-white p-1 shadow-md", src := url, alt := "Selected image preview")
            )
          )
        case _ => None
      },
      child.maybe <-- extractionResult.signal.map(_.map { result =>
        div(
          cls := "flex-1 min-w-[380px] lg:max-w-[calc(50%-1rem)] w-full flex flex-col items-center p-6 border border-gray-200 rounded-xl bg-white shadow-sm mx-auto lg:mx-0",
          tabIndex := -1,
          onMountCallback { ctx =>
            val node = ctx.thisNode.ref.asInstanceOf[js.Dynamic]
            node.scrollIntoView(js.Dynamic.literal(behavior = "smooth", block = "start"))
            node.focus()
          },
          div(
            cls := "flex justify-between items-center w-full mb-5",
            h2(cls := "status-text mb-0", "Result"),
            button(
              cls := "action-btn px-3 py-1.5 min-w-0 flex-row text-xs",
              "Copy JSON",
              onClick --> { _ =>
                val jsonString = js.JSON.stringify(findNutritionFacts(result).getOrElse(js.Dynamic.literal()), null.asInstanceOf[js.Array[js.Any]], 2)
                dom.window.navigator.clipboard.writeText(jsonString)
              }
            )
          ),
          renderNutritionFacts(result)
        )
      })
    )
  )

  renderOnDomContentLoaded(
    dom.document.getElementById("app"),
    appElement
  )
