package frontend

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom

import scala.scalajs.js.Thenable.Implicits.*
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.timers.setTimeout

import frontend.models.*
import frontend.utils.JsonUtils.*
import frontend.utils.IndexedDBUtils
import frontend.components.*

@main def app(): Unit =
  val status = Var("Select image to begin")
  val selectedImage = Var(Option.empty[dom.File])
  val imagePreviewUrl = Var(Option.empty[String])
  val activeJobIds = Var(Set.empty[String])
  val extractionResult = Var(Option.empty[js.Dynamic])
  val currentResultId = Var(Option.empty[String])
  val scanHistory = Var(Seq.empty[HistoryItem])
  val showHistory = Var(false)
  val showJsonModal = Var(false)
  val modalJson = Var("")

  // Initialize History from IndexedDB
  IndexedDBUtils.migrateFromLocalStorage().flatMap(_ => IndexedDBUtils.loadHistory()).foreach { history =>
    scanHistory.set(history)
  }
  val comparisonResult = Var(Option.empty[js.Dynamic])
  val comparisonResultId = Var(Option.empty[String])
  val cameraActive = Var(false)
  val videoStreamRef = Var(Option.empty[dom.MediaStream])

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

  val appElement = div(
    cls := "max-w-[1200px] w-[96vw] mx-auto text-center",
    AppBanner(),
    ActionBar(
      onUploadFiles = { fileList =>
        stopCamera()
        val firstFile = fileList.head
        setSelectedImage(Some(firstFile))
        uploadImage(firstFile, isMain = true)

        fileList.tail.foreach { file =>
          uploadImage(file, isMain = false)
        }
      },
      onStartCamera = () => startCamera(),
      onToggleHistory = () => showHistory.update(!_)
    ),
    ScanHistory(
      showHistory = showHistory.signal,
      scanHistory = scanHistory.signal,
      currentResultId = currentResultId.signal,
      comparisonResultId = comparisonResultId.signal,
      onClearAll = () => {
        if dom.window.confirm("Are you sure you want to clear all history?") then
          IndexedDBUtils.clearHistory().foreach { _ =>
            scanHistory.set(Seq.empty)
          }
      },
      onViewItem = { item =>
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
      },
      onCompareItem = { item =>
        comparisonResult.set(Some(item.parsedData))
        comparisonResultId.set(Some(item.id))
        item.imageBlob.foreach { blob =>
          imagePreviewUrl.now().foreach(dom.URL.revokeObjectURL)
          imagePreviewUrl.set(Some(dom.URL.createObjectURL(blob)))
          selectedImage.set(None)
        }
        showHistory.set(false)
      },
      onClearItem = { item =>
        if dom.window.confirm(s"Delete '${item.title}'?") then
          IndexedDBUtils.deleteItem(item.id).foreach { _ =>
            scanHistory.update(_.filterNot(_.id == item.id))
          }
      }
    ),
    child.maybe <-- status.signal.combineWith(selectedImage.signal).map { case (s, imgOpt) =>
      Option.when(s.nonEmpty && imgOpt.isEmpty)(h2(cls := "status-text", s))
    },
    CameraViewfinder(
      cameraActive = cameraActive.signal,
      videoStreamRef = videoStreamRef.signal,
      onCapture = captureFrame,
      onCancel = () => stopCamera()
    ),
    div(
      cls := "flex flex-col md:flex-row flex-wrap justify-center items-start gap-8 my-6 w-full",
      child <-- extractionResult.signal.combineWith(comparisonResult.signal, currentResultId.signal, comparisonResultId.signal, imagePreviewUrl.signal, selectedImage.signal, status.signal).map {
        case (Some(res1), Some(res2), currIdOpt, compIdOpt, _, _, _) =>
          // COMPARISON MODE: Hide image, show a unified comparison card
          div(
            cls := "w-full flex justify-center items-start",
            ComparisonView(
              res1,
              res2,
              onClear = Some(() => {
                comparisonResult.set(None)
                comparisonResultId.set(None)
              })
            )
          )

        case (Some(res), None, currIdOpt, _, maybeUrl, maybeFile, s) =>
          // STANDARD MODE: Show Image + Current Result
          div(
            cls := "w-full flex flex-col md:flex-row gap-8 justify-center items-start",
            ImagePreview(maybeUrl, maybeFile, s),
            NutritionFactsCard(
              res,
              "Result",
              onViewJson = () => {
                val jsonString = js.JSON.stringify(findNutritionFacts(res).getOrElse(js.Dynamic.literal()), null.asInstanceOf[js.Array[js.Any]], 2)
                modalJson.set(jsonString)
                showJsonModal.set(true)
              },
              onTitleUpdate = currIdOpt.map(id => (newTitle: String) => updateTitle(id, extractionResult, newTitle))
            )
          )

        case (None, _, _, _, maybeUrl, maybeFile, s) =>
          // INITIAL MODE: Only show Image preview if exists
          maybeUrl.zip(maybeFile).map { case (url, file) =>
            ImagePreview(Some(url), Some(file), s)
          }.getOrElse(emptyNode)
      }
    ),
    JsonModal(
      showJsonModal = showJsonModal.signal,
      modalJson = modalJson.signal,
      onClose = () => showJsonModal.set(false),
      onCopyToClipboard = { jsonText =>
        dom.window.navigator.clipboard.writeText(jsonText)
      }
    )
  )

  renderOnDomContentLoaded(
    dom.document.getElementById("app"),
    appElement
  )
