package chessfinder
package search

import api.{ TaskResponse, TaskStatusResponse }
import client.ClientError
import client.chess_com.dto.*
import core.{ ProbabilisticBoard, SearchFen }
import persistence.TaskRecord
import search.BrokenLogic.*
import search.TaskStatusChecker
import search.entity.*
import util.UriParser

import chess.format.pgn.PgnStr
import sttp.model.Uri
import sttp.model.Uri.UriContext
import zio.ZIO
import zio.mock.{ Expectation, MockRandom, MockReporter }
import zio.test.*

import java.util.UUID

object TaskStatusCheckerTest extends ZIOSpecDefault with Mocks:

  val service = ZIO.service[TaskStatusChecker]
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

      val testResult =
        for
          taskStatusChecker <- service
          actualResult      <- taskStatusChecker.check(taskId)
          check = assertTrue(actualResult == expectedTask)
        yield check
      testResult.provide(mock, TaskStatusChecker.Impl.layer)
    },
    test("when either task is not found should pass through the error") {
      val taskId       = TaskId(UUID.randomUUID())
      val expectedTask = TaskStatusResponse(TaskRecord(taskId, 15))

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
      testResult.provide(mock, TaskStatusChecker.Impl.layer)
    },
    test("for any other broke logic should return ServiceOverloaded") {
      val taskId       = TaskId(UUID.randomUUID())
      val expectedTask = TaskStatusResponse(TaskRecord(taskId, 15))

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
      testResult.provide(mock, TaskStatusChecker.Impl.layer)
    }
  ) @@ TestAspect.sequential @@ MockReporter()
