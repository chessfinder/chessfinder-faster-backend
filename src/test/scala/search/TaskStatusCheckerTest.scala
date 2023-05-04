package chessfinder
package search

import zio.test.*
import chessfinder.core.SearchFen
import chessfinder.core.ProbabilisticBoard
import search.BrokenLogic.*
import search.TaskStatusChecker
import search.entity.*
import sttp.model.Uri.UriContext
import client.chess_com.dto.*
import chess.format.pgn.PgnStr
import zio.mock.Expectation
import api.ApiVersion
import core.SearchFen
import chessfinder.api.TaskResponse
import chessfinder.util.UriParser
import java.util.UUID
import zio.mock.MockRandom
import client.ClientError
import sttp.model.Uri
import chess.format.pgn.PgnStr
import zio.mock.MockReporter
import api.TaskStatusResponse
import search.entity.TaskId
import persistence.TaskRecord

object TaskStatusCheckerTest extends ZIOSpecDefault with Mocks:

  override def spec = suite("TaskStatusChecker")(
    test(
      "when status exists in database should retrieve and return back"
    ) {
      val taskId       = TaskId(UUID.randomUUID())
      val expectedTask = TaskStatusResponse(TaskRecord(taskId, 15))

      val gettingTask = TaskRepoMock.GetTask(
        assertion = Assertion.equalTo(taskId),
        result = Expectation.value(expectedTask)
      )

      val mock = gettingTask.toLayer

      val statusChecking = TaskStatusChecker.check(taskId)

      (for
        actualResult <- statusChecking
        check = assertTrue(actualResult == expectedTask)
      yield check).provide(mock, TaskStatusChecker.Impl.layer)
    },
    test("when either task is not found should pass through the error") {
      val taskId       = TaskId(UUID.randomUUID())
      val expectedTask = TaskStatusResponse(TaskRecord(taskId, 15))

      val gettingTask = TaskRepoMock.GetTask(
        assertion = Assertion.equalTo(taskId),
        result = Expectation.failure(TaskNotFound(taskId))
      )

      val mock = gettingTask.toLayer

      val statusChecking = TaskStatusChecker.check(taskId)

      (for
        actualResult <- statusChecking.either
        check = assertTrue(actualResult == Left(TaskNotFound(taskId)))
      yield check).provide(mock, TaskStatusChecker.Impl.layer)
    },
    test("for any other broke logic should return ServiceOverloaded") {
      val taskId       = TaskId(UUID.randomUUID())
      val expectedTask = TaskStatusResponse(TaskRecord(taskId, 15))

      val gettingTask = TaskRepoMock.GetTask(
        assertion = Assertion.equalTo(taskId),
        result = Expectation.failure(TaskProgressOverflown(taskId))
      )

      val mock = gettingTask.toLayer

      val statusChecking = TaskStatusChecker.check(taskId)

      (for
        actualResult <- statusChecking.either
        check = assertTrue(actualResult == Left(ServiceOverloaded))
      yield check).provide(mock, TaskStatusChecker.Impl.layer)
    }
  ) @@ TestAspect.sequential @@ MockReporter()
