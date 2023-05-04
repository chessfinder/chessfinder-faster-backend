package chessfinder
package search.repo

import zio.test.*
import zio.*
import client.chess_com.ChessDotComClient
import chessfinder.testkit.wiremock.ClientBackdoor
import sttp.model.Uri
import client.chess_com.dto.*
import client.*
import client.ClientError.*
import search.entity.UserName
import scala.util.Success
import zio.http.Client
import sttp.model.Uri.UriContext
import com.typesafe.config.ConfigFactory
import scala.util.Try
import zio.ZLayer
import search.entity.*
import testkit.parser.JsonReader
import chessfinder.client.ClientError.ArchiveNotFound
import testkit.NarrowIntegrationSuite
import zio.aws.netty
import zio.aws.core.config.AwsConfig
import persistence.core.DefaultDynamoDBExecutor
import zio.dynamodb.*
import chessfinder.persistence.PlatformType
import chessfinder.persistence.UserRecord
import persistence.GameRecord
import util.UriParser
import chess.format.pgn.PgnStr
import io.circe.*
import java.util.UUID
import chessfinder.persistence.TaskRecord
import chessfinder.search.BrokenLogic

object TaskRepoTest extends NarrowIntegrationSuite:
  def spec =
    suite("TaskRepo")(
      suite("initiate")(
        test("should create new task in the database") {
          val taskId         = TaskId(UUID.randomUUID())
          val expectedResult = TaskRecord(taskId, 15)

          for
            _            <- TaskRepo.initiate(taskId, 15)
            actualResult <- TaskRecord.Table.get[TaskRecord](taskId)
            result       <- assertTrue(actualResult == Right(expectedResult))
          yield result
        }
      ),
      suite("get")(
        test("should get the task from the table if it exists") {

          val taskId         = TaskId(UUID.randomUUID())
          val task           = TaskRecord(taskId, 1, 2, 3)
          val expectedResult = task.toStatus
          for
            _            <- TaskRecord.Table.put(task)
            actualResult <- TaskRepo.get(taskId)
            result       <- assertTrue(actualResult == expectedResult)
          yield result
        },
        test("should give back TaskNotFound if task does not exists in the database") {

          val taskId         = TaskId(UUID.randomUUID())
          val expectedResult = BrokenLogic.TaskNotFound(taskId)

          for
            actualResult <- TaskRepo.get(taskId).either
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
            _            <- TaskRecord.Table.put(initialTask)
            _            <- TaskRepo.successIncrement(taskId)
            actualResult <- TaskRecord.Table.get[TaskRecord](taskId)
            result       <- assertTrue(actualResult == Right(expectedResult))
          yield result
        },
        test("should give back TaskOverflown if task status is broken") {

          val taskId         = TaskId(UUID.randomUUID())
          val task           = TaskRecord(taskId, succeed = 1, failed = 2, done = 3, pending = 7, total = 1)
          val extectedResult = BrokenLogic.TaskProgressOverflown(taskId)
          for
            _            <- TaskRecord.Table.put(task)
            actualResult <- TaskRepo.successIncrement(taskId).either
            result       <- assertTrue(actualResult == Left(extectedResult))
          yield result
        },
        test("should give back TaskNotFound if task does not exists in the database") {

          val taskId         = TaskId(UUID.randomUUID())
          val expectedResult = BrokenLogic.TaskNotFound(taskId)

          for
            actualResult <- TaskRepo.successIncrement(taskId).either
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
            _            <- TaskRecord.Table.put(initialTask)
            _            <- TaskRepo.failureIncrement(taskId)
            actualResult <- TaskRecord.Table.get[TaskRecord](taskId)
            result       <- assertTrue(actualResult == Right(expectedResult))
          yield result
        },
        test("should give back TaskOverflown if task status is broken") {

          val taskId         = TaskId(UUID.randomUUID())
          val task           = TaskRecord(taskId, succeed = 1, failed = 2, done = 3, pending = 7, total = 1)
          val extectedResult = BrokenLogic.TaskProgressOverflown(taskId)
          for
            _            <- TaskRecord.Table.put(task)
            actualResult <- TaskRepo.failureIncrement(taskId).either
            result       <- assertTrue(actualResult == Left(extectedResult))
          yield result
        },
        test("should give back TaskNotFound if task does not exists in the database") {

          val taskId         = TaskId(UUID.randomUUID())
          val expectedResult = BrokenLogic.TaskNotFound(taskId)

          for
            actualResult <- TaskRepo.failureIncrement(taskId).either
            result       <- assertTrue(actualResult == Left(expectedResult))
          yield result
        }
      )
    ).provideLayer(dynamodbLayer >+> TaskRepo.Impl.layer) @@ TestAspect.sequential
