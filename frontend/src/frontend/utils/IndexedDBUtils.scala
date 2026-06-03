package frontend.utils

import scala.scalajs.js
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalajs.dom
import scala.scalajs.js.Thenable.Implicits.*
import frontend.models.*

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
