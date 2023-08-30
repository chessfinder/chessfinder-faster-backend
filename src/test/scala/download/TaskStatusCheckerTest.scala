package chessfinder
package download

import BrokenComputation.*
import persistence.TaskRecord
import chessfinder.download.details.DownloadStatusResponse

import zio.ZIO
import zio.mock.{ Expectation, MockReporter }
import zio.test.*

import java.util.UUID

object TaskStatusCheckerTest extends ZIOSpecDefault with Mocks:

  val service = ZIO.service[DownloadStatusChecker]
  override def spec = suite("TaskStatusChecker")(
    test(
      "when status exists in database should retrieve and return back"
    ) {
      val taskId       = TaskId(UUID.randomUUID())
      val expectedTask = DownloadStatusResponse(TaskRecord(taskId, 15))

      val gettingTask = TaskRepoMock.GetTask(
        assertion = Assertion.equalTo(taskId),
        result = Expectation.value(expectedTask)
      )

      val mock = gettingTask.toLayer

      val testResult =
        for
          taskStatusChecker <- service
          actualResult      <- taskStatusChecker.check(taskId)
          check = assertTrue(actualResult == expectedTask)
        yield check
      testResult.provide(mock, DownloadStatusChecker.Impl.layer)
    },
    test("when either task is not found should pass through the error") {
      val taskId       = TaskId(UUID.randomUUID())
      val expectedTask = DownloadStatusResponse(TaskRecord(taskId, 15))

      val gettingTask = TaskRepoMock.GetTask(
        assertion = Assertion.equalTo(taskId),
        result = Expectation.failure(TaskNotFound(taskId))
      )

      val mock = gettingTask.toLayer

      val testResult =
        for
          taskStatusChecker <- service
          actualResult      <- taskStatusChecker.check(taskId).either
          check = assertTrue(actualResult == Left(TaskNotFound(taskId)))
        yield check
      testResult.provide(mock, DownloadStatusChecker.Impl.layer)
    },
    test("for any other broke logic should return ServiceOverloaded") {
      val taskId       = TaskId(UUID.randomUUID())
      val expectedTask = DownloadStatusResponse(TaskRecord(taskId, 15))

      val gettingTask = TaskRepoMock.GetTask(
        assertion = Assertion.equalTo(taskId),
        result = Expectation.failure(TaskProgressOverflown(taskId))
      )

      val mock = gettingTask.toLayer

      val testResult =
        for
          taskStatusChecker <- service
          actualResult      <- taskStatusChecker.check(taskId).either
          check = assertTrue(actualResult == Left(ServiceOverloaded))
        yield check
      testResult.provide(mock, DownloadStatusChecker.Impl.layer)
    }
  ) @@ TestAspect.sequential @@ MockReporter()
