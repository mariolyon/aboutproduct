package backend

import munit.FunSuite

class NuExtractParserTest extends FunSuite:

  test("parseJobId extracts id successfully") {
    val json = """{"id": "job-123"}"""
    assertEquals(NuExtractParser.parseJobId(json), "job-123")
  }

  test("parseJobId extracts job_id successfully") {
    val json = """{"job_id": "job-456"}"""
    assertEquals(NuExtractParser.parseJobId(json), "job-456")
  }

  test("parseJobId extracts jobId successfully") {
    val json = """{"jobId": "job-789"}"""
    assertEquals(NuExtractParser.parseJobId(json), "job-789")
  }

  test("parseJobId extracts nested job id successfully") {
    val json = """{"job": {"id": "nested-job-123"}}"""
    assertEquals(NuExtractParser.parseJobId(json), "nested-job-123")
  }

  test("parseJobId throws exception when id is missing") {
    val json = """{"status": "success"}"""
    interceptMessage[RuntimeException]("NuExtract create-job response did not include a job id: {\"status\": \"success\"}") {
      NuExtractParser.parseJobId(json)
    }
  }

  test("parseJobId throws exception for invalid JSON") {
    val json = """invalid-json"""
    intercept[ujson.ParseException] {
      NuExtractParser.parseJobId(json)
    }
  }

  test("isJobNotCompleted returns true when code is JobNotCompleted") {
    val json = """{"code": "JobNotCompleted", "message": "Still processing"}"""
    assert(NuExtractParser.isJobNotCompleted(json))
  }

  test("isJobNotCompleted returns false when code is different") {
    val json = """{"code": "Success", "message": "Finished"}"""
    assert(!NuExtractParser.isJobNotCompleted(json))
  }

  test("isJobNotCompleted returns false when code is missing") {
    val json = """{"status": "pending"}"""
    assert(!NuExtractParser.isJobNotCompleted(json))
  }

  test("isJobNotCompleted returns false for invalid JSON") {
    val json = """invalid-json"""
    assert(!NuExtractParser.isJobNotCompleted(json))
  }

