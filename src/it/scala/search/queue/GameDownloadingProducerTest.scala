package chessfinder
package search.queue

import client.*
import client.ClientError.*
import client.chess_com.ChessDotComClient
import client.chess_com.dto.*
import persistence.GameRecord
import persistence.core.DefaultDynamoDBExecutor
import pubsub.{ DownloadGameCommand, Platform }
import pubsub.core.PubSub
import search.entity.*
import sttp.model.Uri
import sttp.model.Uri.UriContext
import sttp.model.UriInterpolator.*
import testkit.NarrowIntegrationSuite
import testkit.parser.JsonReader
import testkit.wiremock.ClientBackdoor
import util.{ RandomReadableString, UriParser }

import chess.format.pgn.PgnStr
import com.typesafe.config.ConfigFactory
import io.circe.*
import zio.*
import zio.aws.core.config.AwsConfig
import zio.aws.netty
import zio.dynamodb.*
import zio.http.Client
import zio.stream.ZSink
import zio.test.*

import java.util.UUID
import scala.util.{ Success, Try }

object GameDownloadingProducerTest extends NarrowIntegrationSuite:

  val queue = ZIO.service[GameDownloadingProducer]

  def spec =
    suite("GameDownloadingProducer")(
      suite("publish")(
        test("should writes commands into the queue") {

          val userName     = UserName(RandomReadableString())
          val platformType = Platform.CHESS_DOT_COM
          val userId       = UserId(RandomReadableString())
          val user         = UserIdentified(platformType.toPlatform, userName, userId)
          val taskId       = TaskId(UUID.randomUUID())

          val archiveId1 = ArchiveId("http://example.com/1")
          val archiveId2 = ArchiveId("http://example.com/2")
          val archives   = Seq(archiveId1, archiveId2)

          val expectedCommand1 = DownloadGameCommand(
            userName = userName.value,
            userId = userId.value,
            platform = Platform.CHESS_DOT_COM,
            archiveId = archiveId1.value,
            taskId = taskId.value
          )

          val expectedCommand2 = DownloadGameCommand(
            userName = userName.value,
            userId = userId.value,
            platform = Platform.CHESS_DOT_COM,
            archiveId = archiveId2.value,
            taskId = taskId.value
          )

          val expectedResult = Set(expectedCommand1, expectedCommand2)

          val readingFromQueue =
            for
              subscriber             <- ZIO.service[PubSub[DownloadGameCommand]]
              actualCommandsOrErrors <- subscriber.subscribe.run(ZSink.collectAllToSet)
              actualCommands = actualCommandsOrErrors.collect { case Right(command) => command }
            yield actualCommands
          for
            gameDownloadingProducer <- queue
            _                       <- gameDownloadingProducer.publish(user, archives, taskId)
            actualResult            <- readingFromQueue
            result                  <- assertTrue(actualResult == expectedResult)
          yield result
        }
      )
    ).provideLayer(
      (configLayer >+> sqsLayer >+> DownloadGameCommand.Queue.layer) >+> GameDownloadingProducer.Impl.layer
    ) @@ TestAspect.sequential
