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
    ingredients: String,
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

    val ingredientsList = asArray(dynamicField(result, "ingredients").orElse {
      dynamicField(result, "result").flatMap(res => dynamicField(res, "ingredients"))
    }).map(stringify).filter(_.nonEmpty)

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
      ingredients = if ingredientsList.nonEmpty then ingredientsList.mkString(", ") else "unknown",
      smallPrint = stringField(nutrition, "small_print")
    )

  case class HistoryItem(id: String, timestamp: Double, title: String, dataStr: String, imageBlob: Option[dom.Blob], status: String = "completed") {
    def parsedData: js.Dynamic = js.JSON.parse(dataStr).asInstanceOf[js.Dynamic]
  }

  object IndexedDBUtils:
    val DBName = "AboutProductDB"
    val StoreName = "scans"
    val Version = 1

    def openDB(): Future[dom.IDBDatabase] =
      val promise = scala.concurrent.Promise[dom.IDBDatabase]()
      val maybeIndexedDB = dom.window.indexedDB.toOption

      maybeIndexedDB match
        case Some(idb) =>
          val request = idb.open(DBName, Version)

          request.onupgradeneeded = { (event: dom.IDBVersionChangeEvent) =>
            val db = request.result.asInstanceOf[dom.IDBDatabase]
            if !db.objectStoreNames.contains(StoreName) then
              db.createObjectStore(StoreName, js.Dynamic.literal(keyPath = "id").asInstanceOf[dom.IDBCreateObjectStoreOptions])
          }

          request.onsuccess = { (_: dom.Event) =>
            promise.success(request.result.asInstanceOf[dom.IDBDatabase])
          }

          request.onerror = { (event: dom.ErrorEvent) =>
            promise.failure(new Exception(s"IndexedDB error: ${event.message}"))
          }
        case None =>
          promise.failure(new Exception("IndexedDB not supported in this browser"))

      promise.future

    def loadHistory(): Future[Seq[HistoryItem]] =
      openDB().flatMap { db =>
        val promise = scala.concurrent.Promise[Seq[HistoryItem]]()
        val transaction = db.transaction(StoreName, dom.IDBTransactionMode.readonly)
        val store = transaction.objectStore(StoreName)
        val request = store.getAll()

        request.onsuccess = { (_: dom.Event) =>
          val results = request.result.asInstanceOf[js.Array[js.Dynamic]]
          val items = results.toSeq.map { item =>
            val blobValue = item.selectDynamic("imageBlob")
            val statusValue = item.selectDynamic("status")
            HistoryItem(
              id = item.selectDynamic("id").asInstanceOf[String],
              timestamp = item.selectDynamic("timestamp").asInstanceOf[Double],
              title = item.selectDynamic("title").asInstanceOf[String],
              dataStr = item.selectDynamic("dataStr").asInstanceOf[String],
              imageBlob = if js.isUndefined(blobValue) || blobValue == null then None else Some(blobValue.asInstanceOf[dom.Blob]),
              status = if js.isUndefined(statusValue) || statusValue == null then "completed" else statusValue.asInstanceOf[String]
            )
          }.sortBy(-_.timestamp)
          promise.success(items)
        }

        request.onerror = { (event: dom.ErrorEvent) =>
          promise.failure(new Exception(s"Load error: ${event.message}"))
        }

        promise.future
      }

    def saveItem(item: HistoryItem): Future[Unit] =
      openDB().flatMap { db =>
        val promise = scala.concurrent.Promise[Unit]()
        val transaction = db.transaction(StoreName, dom.IDBTransactionMode.readwrite)
        val store = transaction.objectStore(StoreName)
        val jsItem = js.Dynamic.literal(
          id = item.id,
          timestamp = item.timestamp,
          title = item.title,
          dataStr = item.dataStr,
          imageBlob = item.imageBlob.orNull,
          status = item.status
        )
        val request = store.put(jsItem)

        request.onsuccess = { (_: dom.Event) => promise.success(()) }
        request.onerror = { (event: dom.ErrorEvent) =>
          promise.failure(new Exception(s"Save error: ${event.message}"))
        }

        promise.future
      }

    def deleteItem(id: String): Future[Unit] =
      openDB().flatMap { db =>
        val promise = scala.concurrent.Promise[Unit]()
        val transaction = db.transaction(StoreName, dom.IDBTransactionMode.readwrite)
        val store = transaction.objectStore(StoreName)
        val request = store.delete(id)

        request.onsuccess = { (_: dom.Event) => promise.success(()) }
        request.onerror = { (event: dom.ErrorEvent) =>
          promise.failure(new Exception(s"Delete error: ${event.message}"))
        }

        promise.future
      }

    def clearHistory(): Future[Unit] =
      openDB().flatMap { db =>
        val promise = scala.concurrent.Promise[Unit]()
        val transaction = db.transaction(StoreName, dom.IDBTransactionMode.readwrite)
        val store = transaction.objectStore(StoreName)
        val request = store.clear()

        request.onsuccess = { (_: dom.Event) => promise.success(()) }
        request.onerror = { (event: dom.ErrorEvent) =>
          promise.failure(new Exception(s"Clear error: ${event.message}"))
        }

        promise.future
      }

    def migrateFromLocalStorage(): Future[Unit] =
      val HistoryKey = "aboutproduct_history"
      val stored = dom.window.localStorage.getItem(HistoryKey)
      if stored != null && stored.nonEmpty then
        try
          val arr = js.JSON.parse(stored).asInstanceOf[js.Array[js.Dynamic]]
          val items = arr.toSeq.map { item =>
            HistoryItem(
              id = item.selectDynamic("id").asInstanceOf[String],
              timestamp = item.selectDynamic("timestamp").asInstanceOf[Double],
              title = item.selectDynamic("title").asInstanceOf[String],
              dataStr = item.selectDynamic("dataStr").asInstanceOf[String],
              imageBlob = None,
              status = "completed"
            )
          }
          // Save all to IndexedDB
          Future.sequence(items.map(saveItem)).map { _ =>
            dom.window.localStorage.removeItem(HistoryKey)
          }
        catch case _ => Future.successful(())
      else Future.successful(())

object Components:
  import JsonUtils.*

  def row(labelText: String, valueText: String, rowClass: String = ""): HtmlElement =
    div(
      className := s"nf-row $rowClass".trim,
      span(cls := "nf-label", labelText),
      span(cls := "nf-value", valueText)
    )

  def renderNutritionFacts(result: js.Dynamic, onTitleUpdate: Option[String => Unit] = None): HtmlElement =
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
      p(
        cls := "nf-ingredients",
        span(cls := "nf-ingredients-label", "Ingredients: "),
        data.ingredients
      ),
      p(cls := "nf-small-print", data.smallPrint)
    )

@main def app(): Unit =
  import JsonUtils.*
  val status = Var("Select image to begin")
  val selectedImage = Var(Option.empty[dom.File])
  val imagePreviewUrl = Var(Option.empty[String])
  val activeJobIds = Var(Set.empty[String])
  val extractionResult = Var(Option.empty[js.Dynamic])
  val currentResultId = Var(Option.empty[String])
  val scanHistory = Var(Seq.empty[HistoryItem])
  val showHistory = Var(false)

  // Initialize History from IndexedDB
  IndexedDBUtils.migrateFromLocalStorage().flatMap(_ => IndexedDBUtils.loadHistory()).foreach { history =>
    scanHistory.set(history)
  }
  val comparisonResult = Var(Option.empty[js.Dynamic])
  val comparisonResultId = Var(Option.empty[String])
  val cameraActive = Var(false)
  val videoStreamRef = Var(Option.empty[dom.MediaStream])
  val cameraVideoElement = Var(Option.empty[dom.HTMLVideoElement])

  import JsonUtils.*
  import Components.*

  def updateTitle(jobId: String, resultVar: Var[Option[js.Dynamic]], newTitle: String): Unit =
    resultVar.now().foreach { result =>
      // Update parsed JSON in-place
      val target = findNutritionFacts(result).getOrElse(result)
      target.updateDynamic("title")(newTitle)

      // Serialize and parse to force a new JS object reference.
      // This ensures `resultVar.set` successfully emits the change.
      val updatedDataStr = js.JSON.stringify(result)
      val updatedResult = js.JSON.parse(updatedDataStr).asInstanceOf[js.Dynamic]

      resultVar.set(Some(updatedResult))

      // Update history state & IndexedDB
      scanHistory.update { history =>
        history.map { item =>
          if item.id == jobId then
            val updated = item.copy(title = newTitle, dataStr = updatedDataStr)
            IndexedDBUtils.saveItem(updated)
            updated
          else item
        }
      }
    }

  def pollJobStatus(jobId: String, isMain: Boolean): Unit =
    val pollDelayMs = 10000
    val maxPollingDurationMs = 5 * 60 * 1000
    val startedAtMs = js.Date.now()

    def markAsFailed(): Unit =
      activeJobIds.update(_ - jobId)
      scanHistory.update { history =>
        history.map { item =>
          if item.id == jobId then
            val updated = item.copy(status = "failed")
            IndexedDBUtils.saveItem(updated)
            updated
          else item
        }
      }
      if isMain then status.set("Failed")

    def continuePolling(): Unit =
      if activeJobIds.now().contains(jobId) then
        setTimeout(pollDelayMs):
          pollOnce()

    def pollOnce(): Unit =
      if !activeJobIds.now().contains(jobId) then ()
      else if js.Date.now() - startedAtMs > maxPollingDurationMs then
        markAsFailed()
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
                    val nutritionFacts = extractNutritionFacts(parsed)
                    val title = nutritionFacts.title
                    val finalTitle = if title.nonEmpty && title != "n/a" then title else s"Scan ${new js.Date().toLocaleTimeString()}"

                    activeJobIds.update(_ - jobId)
                    scanHistory.update { history =>
                      history.map { item =>
                        if item.id == jobId then
                          val updated = item.copy(
                            status = "completed",
                            title = finalTitle,
                            dataStr = payload
                          )
                          IndexedDBUtils.saveItem(updated)
                          updated
                        else item
                      }
                    }

                    if isMain then
                      extractionResult.set(Some(parsed))
                      currentResultId.set(Some(jobId))
                      status.set("Analysis complete")
                  else if isProcessingStatus(parsed) then
                    if isMain then status.set("processing ...")
                    continuePolling()
                  else
                    if isMain then status.set("processing ...")
                    continuePolling()
                catch
                  case _: Throwable =>
                    markAsFailed()
              }
              payloadFuture.failed.foreach { _ =>
                markAsFailed()
              }
            case 204 =>
              if isMain then status.set("processing ...")
              continuePolling()
            case _ =>
              markAsFailed()
        }

        future.failed.foreach { _ =>
          markAsFailed()
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
          uploadImage(file, isMain = true)
        }
        blobFuture.failed.foreach { _ =>
          status.set("Failed")
          stopCamera()
        }
      case None =>
        status.set("Failed")
        stopCamera()

  def uploadImage(image: dom.File, isMain: Boolean): Unit =
    if isMain then
      extractionResult.set(None)
      currentResultId.set(None)
      comparisonResult.set(None)
      comparisonResultId.set(None)
      activeJobIds.set(Set.empty)
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
          if isMain then status.set("Failed")
        else
          activeJobIds.update(_ + normalizedJobId)
          if isMain then status.set("processing ...")

          // Create placeholder in history
          val tempItem = HistoryItem(
            id = normalizedJobId,
            timestamp = js.Date.now(),
            title = image.name,
            dataStr = "{}",
            imageBlob = Some(image.asInstanceOf[dom.Blob]),
            status = "processing"
          )
          scanHistory.update { history =>
            val updated = (tempItem +: history).sortBy(-_.timestamp).take(20)
            IndexedDBUtils.saveItem(tempItem)
            updated
          }

          pollJobStatus(normalizedJobId, isMain)
      case Left(_) => if isMain then status.set("Failed")
    }
    future.failed.foreach(_ => if isMain then status.set("Failed"))

  def renderImagePreview(urlOpt: Option[String], fileOpt: Option[dom.File], s: String): HtmlElement =
    div(
      cls := "flex-1 min-w-[380px] md:max-w-[calc(50%-1rem)] w-full flex flex-col items-center p-6 border border-gray-200 rounded-xl bg-white shadow-sm mx-auto md:mx-0",
      Option.when(s.nonEmpty)(h2(cls := "status-text", s)).getOrElse(emptyNode),
      fileOpt.map(file => div(cls := "image-filename mb-3 text-sm text-gray-500 break-all font-medium text-left w-full", file.name)).getOrElse(emptyNode),
      urlOpt.map(url => img(cls := "image-preview w-full max-h-[70vh] block border border-gray-300 rounded-lg object-contain bg-white p-1 shadow-md", src := url, alt := "Selected image preview")).getOrElse(emptyNode)
    )

  def renderResultCard(result: js.Dynamic, headerTitle: String, onClear: Option[() => Unit] = None, onTitleUpdate: Option[String => Unit] = None): HtmlElement =
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
            "Copy JSON",
            onClick --> { _ =>
              val jsonString = js.JSON.stringify(findNutritionFacts(result).getOrElse(js.Dynamic.literal()), null.asInstanceOf[js.Array[js.Any]], 2)
              dom.window.navigator.clipboard.writeText(jsonString)
            }
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
          multiple := true,
          onChange --> { event =>
            val files = event.target.asInstanceOf[dom.HTMLInputElement].files
            stopCamera()
            val fileList = (0 until files.length).flatMap(i => Option(files.item(i)))
            if fileList.nonEmpty then
              val firstFile = fileList.head
              setSelectedImage(Some(firstFile))
              uploadImage(firstFile, isMain = true)

              fileList.tail.foreach { file =>
                uploadImage(file, isMain = false)
              }
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
    child.maybe <-- showHistory.signal.combineWith(scanHistory.signal, currentResultId.signal, comparisonResultId.signal).map { case (show, history, currentId, compId) =>
      Option.when(show) {
        div(
          cls := "bg-white border border-gray-200 rounded-xl shadow-lg p-6 mb-6 max-w-2xl mx-auto text-left",
          div(
            cls := "flex justify-between items-center mb-4",
            h3(cls := "text-lg font-bold", "Scan History"),
            button(
              cls := "text-xs text-red-500 hover:text-red-700 font-medium",
              "Clear All",
              onClick --> { _ =>
                if dom.window.confirm("Are you sure you want to clear all history?") then
                  IndexedDBUtils.clearHistory().foreach { _ =>
                    scanHistory.set(Seq.empty)
                  }
              }
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
                      disabled <-- currentResultId.signal.combineWith(comparisonResultId.signal).map { case (currId, compId) =>
                        item.status != "completed" || (currId.contains(item.id) && compId.isEmpty)
                      },
                      "View",
                      onClick --> { _ =>
                        extractionResult.set(Some(item.parsedData))
                        currentResultId.set(Some(item.id))
                        item.imageBlob.foreach { blob =>
                          imagePreviewUrl.now().foreach(dom.URL.revokeObjectURL)
                          imagePreviewUrl.set(Some(dom.URL.createObjectURL(blob)))
                          selectedImage.set(None) // Clear file ref since we're using stored blob
                        }
                        comparisonResult.set(None)
                        comparisonResultId.set(None)
                        showHistory.set(false)
                      }
                    ),
                    button(
                      cls := "action-btn px-3 py-1 text-xs min-w-0 bg-blue-50 text-blue-600 border-blue-200 hover:bg-blue-100 disabled:opacity-50 disabled:cursor-not-allowed disabled:bg-gray-50 disabled:text-gray-400 disabled:border-gray-200",
                      disabled <-- currentResultId.signal.combineWith(comparisonResultId.signal).map { case (currId, compId) =>
                        item.status != "completed" || currId.isEmpty || currId.contains(item.id) || compId.contains(item.id)
                      },
                      "Compare",
                      onClick --> { _ =>
                        comparisonResult.set(Some(item.parsedData))
                        comparisonResultId.set(Some(item.id))
                        item.imageBlob.foreach { blob =>
                          // If we are comparing, we might want to keep the current image or show the new one.
                          // Comparison mode currently hides the image, so setting it is mostly for when we exit comparison.
                          imagePreviewUrl.now().foreach(dom.URL.revokeObjectURL)
                          imagePreviewUrl.set(Some(dom.URL.createObjectURL(blob)))
                          selectedImage.set(None)
                        }
                        showHistory.set(false)
                      }
                    ),
                    button(
                      cls := "action-btn px-3 py-1 text-xs min-w-0 bg-red-50 text-red-600 border-red-200 hover:bg-red-100",
                      "Clear",
                      onClick --> { _ =>
                        if dom.window.confirm(s"Delete '${item.title}'?") then
                          IndexedDBUtils.deleteItem(item.id).foreach { _ =>
                            scanHistory.update(_.filterNot(_.id == item.id))
                          }
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
      cls := "flex flex-col md:flex-row flex-wrap justify-center items-start gap-8 my-6 w-full",
      child <-- extractionResult.signal.combineWith(comparisonResult.signal, currentResultId.signal, comparisonResultId.signal, imagePreviewUrl.signal, selectedImage.signal, status.signal).map {
        case (Some(res1), Some(res2), currIdOpt, compIdOpt, _, _, _) =>
          // COMPARISON MODE: Hide image, show two side-by-side cards
          div(
            cls := "w-full flex flex-col md:flex-row gap-8 justify-center items-start",
            renderResultCard(
              res1,
              "Current Item",
              onTitleUpdate = currIdOpt.map(id => (newTitle: String) => updateTitle(id, extractionResult, newTitle))
            ),
            renderResultCard(
              res2,
              "Comparing With",
              onClear = Some(() => {
                comparisonResult.set(None)
                comparisonResultId.set(None)
              }),
              onTitleUpdate = compIdOpt.map(id => (newTitle: String) => updateTitle(id, comparisonResult, newTitle))
            )
          )

        case (Some(res), None, currIdOpt, _, maybeUrl, maybeFile, s) =>
          // STANDARD MODE: Show Image + Current Result
          div(
            cls := "w-full flex flex-col md:flex-row gap-8 justify-center items-start",
            renderImagePreview(maybeUrl, maybeFile, s),
            renderResultCard(
              res,
              "Result",
              onTitleUpdate = currIdOpt.map(id => (newTitle: String) => updateTitle(id, extractionResult, newTitle))
            )
          )

        case (None, _, _, _, maybeUrl, maybeFile, s) =>
          // INITIAL MODE: Only show Image preview if exists
          maybeUrl.zip(maybeFile).map { case (url, file) =>
            renderImagePreview(Some(url), Some(file), s)
          }.getOrElse(emptyNode)
      }
    )
  )

  renderOnDomContentLoaded(
    dom.document.getElementById("app"),
    appElement
  )
