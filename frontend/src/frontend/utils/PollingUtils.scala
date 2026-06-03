package frontend.utils

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import scala.scalajs.js
import scala.scalajs.js.timers.setTimeout
import scala.scalajs.js.Thenable.Implicits.*
import scala.concurrent.ExecutionContext.Implicits.global

import frontend.models.*
import frontend.utils.JsonUtils.*
import frontend.utils.IndexedDBUtils

object PollingUtils:
  def pollJobStatus(
    jobId: String,
    isMain: Boolean,
    activeJobIds: Var[Set[String]],
    scanHistory: Var[Seq[HistoryItem]],
    status: Var[String],
    extractionResult: Var[Option[js.Dynamic]],
    currentResultId: Var[Option[String]]
  ): Unit =
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
