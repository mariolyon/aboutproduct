package backend

import munit.FunSuite

class NuExtractResponseBodyParserTest extends FunSuite:

  test("parseJobId extracts id successfully") {
    val json = """{"id": "job-123"}"""
    assertEquals(NuExtractResponseBodyParser.parseJobId(json), "job-123")
  }

  test("parseJobId extracts job_id successfully") {
    val json = """{"job_id": "job-456"}"""
    assertEquals(NuExtractResponseBodyParser.parseJobId(json), "job-456")
  }

  test("parseJobId extracts jobId successfully") {
    val json = """{"jobId": "job-789"}"""
    assertEquals(NuExtractResponseBodyParser.parseJobId(json), "job-789")
  }

  test("parseJobId extracts nested job id successfully") {
    val json = """{"job": {"id": "nested-job-123"}}"""
    assertEquals(NuExtractResponseBodyParser.parseJobId(json), "nested-job-123")
  }

  test("parseJobId throws exception when id is missing") {
    val json = """{"status": "success"}"""
    interceptMessage[RuntimeException]("NuExtract create-job response did not include a job id: {\"status\": \"success\"}") {
      NuExtractResponseBodyParser.parseJobId(json)
    }
  }

  test("parseJobId throws exception for invalid JSON") {
    val json = """invalid-json"""
    intercept[ujson.ParseException] {
      NuExtractResponseBodyParser.parseJobId(json)
    }
  }

  test("isJobNotCompleted returns true when code is JobNotCompleted") {
    val json = """{"code": "JobNotCompleted", "message": "Still processing"}"""
    assert(NuExtractResponseBodyParser.isJobNotCompleted(json))
  }

  test("isJobNotCompleted returns false when code is different") {
    val json = """{"code": "Success", "message": "Finished"}"""
    assert(!NuExtractResponseBodyParser.isJobNotCompleted(json))
  }

  test("isJobNotCompleted returns false when code is missing") {
    val json = """{"status": "pending"}"""
    assert(!NuExtractResponseBodyParser.isJobNotCompleted(json))
  }

  test("isJobNotCompleted returns false for invalid JSON") {
    val json = """invalid-json"""
    assert(!NuExtractResponseBodyParser.isJobNotCompleted(json))
  }

