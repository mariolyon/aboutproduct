package backend

import ujson.Obj
import scala.util.control.NonFatal

object NuExtractParser:
  def parseJobId(responseBody: String): String =
    val json = ujson.read(responseBody)
    val maybeJobId = json match
      case obj: Obj =>
        obj.value.get("id").flatMap(_.strOpt)
          .orElse(obj.value.get("job_id").flatMap(_.strOpt))
          .orElse(obj.value.get("jobId").flatMap(_.strOpt))
          .orElse(
            obj.value
              .get("job")
              .collect { case nestedObj: Obj => nestedObj }
              .flatMap(_.value.get("id").flatMap(_.strOpt))
          )
      case _ => None
    maybeJobId.getOrElse(
      throw RuntimeException(s"NuExtract create-job response did not include a job id: $responseBody")
    )

  def isJobNotCompleted(responseBody: String): Boolean =
    try
      val json = ujson.read(responseBody)
      json.obj.get("code").flatMap(_.strOpt).contains("JobNotCompleted")
    catch
      case NonFatal(_) => false
