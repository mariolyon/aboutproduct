package frontend.components

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import scala.scalajs.js

object CameraViewfinder:
  def apply(
    cameraActive: Signal[Boolean],
    videoStreamRef: Signal[Option[dom.MediaStream]],
    onCapture: dom.HTMLVideoElement => Unit,
    onCancel: () => Unit
  ): Modifier[HtmlElement] =
    val cameraVideoElement = Var(Option.empty[dom.HTMLVideoElement])

    child.maybe <-- cameraActive.map { active =>
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
              videoStreamRef --> { maybeStream =>
                thisNode.ref.asInstanceOf[js.Dynamic].updateDynamic("srcObject")(maybeStream.orNull)
              }
            }
          ),
          div(
            cls := "camera-controls",
            button(
              "Capture",
              onClick --> { _ =>
                cameraVideoElement.now() match
                  case Some(videoElement) => onCapture(videoElement)
                  case None               => ()
              }
            ),
            button(
              "Cancel",
              onClick --> (_ => onCancel())
            )
          )
        )
      }
    }
