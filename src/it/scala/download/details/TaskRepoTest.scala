package chessfinder
package download.details

import client.*
import client.ClientError.*
import client.chess_com.ChessDotComClient
import client.chess_com.*
import persistence.core.DefaultDynamoDBExecutor
import persistence.{ GameRecord, PlatformType, TaskRecord, UserRecord }
import search.*
import testkit.NarrowIntegrationSuite
import testkit.parser.JsonReader
import testkit.wiremock.ClientBackdoor
import util.UriParser

import chess.format.pgn.PgnStr
import chessfinder.BrokenComputation
import chessfinder.download.details.TaskRepo
import com.typesafe.config.ConfigFactory
import io.circe.*
import sttp.model.Uri
import sttp.model.Uri.UriContext
import zio.*
import zio.aws.core.config.AwsConfig
import zio.aws.netty
import zio.dynamodb.*
import zio.http.Client
import zio.test.*

import java.util.UUID
import scala.util.{ Success, Try }

object TaskRepoTest extends NarrowIntegrationSuite:

  val repo = ZIO.service[TaskRepo]
  def spec =
    suite("TaskRepo")(
      suite("initiate")(
        test("should create new task in the database") {
          val taskId         = TaskId(UUID.randomUUID())
          val expectedResult = TaskRecord(taskId, 15)

          for
            taskRepo       <- repo
            returnedResult <- taskRepo.initiate(taskId, 15)
            actualResult   <- TaskRecord.Table.get[TaskRecord](taskId)
            result1        <- assertTrue(actualResult == Right(expectedResult))
            result2        <- assertTrue(actualResult.map(ar => ar.toStatus) == Right(returnedResult))
          yield result1 && result2
        }
      ),
      suite("get")(
        test("should get the task from the table if it exists") {

          val taskId         = TaskId(UUID.randomUUID())
          val task           = TaskRecord(taskId, 1, 2, 3)
          val expectedResult = task.toStatus
          for
            taskRepo     <- repo
            _            <- TaskRecord.Table.put(task)
            actualResult <- taskRepo.get(taskId)
            result       <- assertTrue(actualResult == expectedResult)
          yield result
        },
        test("should give back TaskNotFound if task does not exists in the database") {

          val taskId         = TaskId(UUID.randomUUID())
          val expectedResult = BrokenComputation.TaskNotFound(taskId)

          for
            taskRepo     <- repo
            actualResult <- taskRepo.get(taskId).either
            result       <- assertTrue(actualResult == Left(expectedResult))
          yield result
        }
      ),
      suite("successIncrement")(
        test("should incerment success progress in the task if it exists") {

          val taskId         = TaskId(UUID.randomUUID())
          val initialTask    = TaskRecord(taskId, failed = 1, succeed = 2, pending = 3)
          val expectedResult = TaskRecord(taskId, failed = 1, succeed = 3, pending = 2)

          for
            taskRepo     <- repo
            _            <- TaskRecord.Table.put(initialTask)
            _            <- taskRepo.successIncrement(taskId)
            actualResult <- TaskRecord.Table.get[TaskRecord](taskId)
            result       <- assertTrue(actualResult == Right(expectedResult))
          yield result
        },
        test("should give back TaskOverflown if task status is broken") {

          val taskId         = TaskId(UUID.randomUUID())
          val task           = TaskRecord(taskId, succeed = 1, failed = 2, done = 3, pending = 7, total = 1)
          val extectedResult = BrokenComputation.TaskProgressOverflown(taskId)
          for
            taskRepo     <- repo
            _            <- TaskRecord.Table.put(task)
            actualResult <- taskRepo.successIncrement(taskId).either
            result       <- assertTrue(actualResult == Left(extectedResult))
          yield result
        },
        test("should give back TaskNotFound if task does not exists in the database") {

          val taskId         = TaskId(UUID.randomUUID())
          val expectedResult = BrokenComputation.TaskNotFound(taskId)

          for
            taskRepo     <- repo
            actualResult <- taskRepo.successIncrement(taskId).either
            result       <- assertTrue(actualResult == Left(expectedResult))
          yield result
        }
      ),
      suite("failureIncrement")(
        test("should incerment failure progress in the task if it exists") {

          val taskId         = TaskId(UUID.randomUUID())
          val initialTask    = TaskRecord(taskId, failed = 1, succeed = 2, pending = 3)
          val expectedResult = TaskRecord(taskId, failed = 2, succeed = 2, pending = 2)

          for
            taskRepo     <- repo
            _            <- TaskRecord.Table.put(initialTask)
            _            <- taskRepo.failureIncrement(taskId)
            actualResult <- TaskRecord.Table.get[TaskRecord](taskId)
            result       <- assertTrue(actualResult == Right(expectedResult))
          yield result
        },
        test("should give back TaskOverflown if task status is broken") {

          val taskId         = TaskId(UUID.randomUUID())
          val task           = TaskRecord(taskId, succeed = 1, failed = 2, done = 3, pending = 7, total = 1)
          val extectedResult = BrokenComputation.TaskProgressOverflown(taskId)
          for
            taskRepo     <- repo
            _            <- TaskRecord.Table.put(task)
            actualResult <- taskRepo.failureIncrement(taskId).either
            result       <- assertTrue(actualResult == Left(extectedResult))
          yield result
        },
        test("should give back TaskNotFound if task does not exists in the database") {

          val taskId         = TaskId(UUID.randomUUID())
          val expectedResult = BrokenComputation.TaskNotFound(taskId)

          for
            taskRepo     <- repo
            actualResult <- taskRepo.failureIncrement(taskId).either
            result       <- assertTrue(actualResult == Left(expectedResult))
          yield result
        }
      )
    ).provideLayer(dynamodbLayer >+> TaskRepo.Impl.layer) @@ TestAspect.sequential
