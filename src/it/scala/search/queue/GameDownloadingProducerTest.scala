package chessfinder
package search.queue

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
import chessfinder.pubsub.Platform
import persistence.GameRecord
import util.UriParser
import chess.format.pgn.PgnStr
import io.circe.*
import chessfinder.util.RandomReadableString
import java.util.UUID
import sttp.model.UriInterpolator.*
import chessfinder.pubsub.DownloadGameCommand
import pubsub.core.PubSub
import zio.stream.ZSink

object GameDownloadingProducerTest extends NarrowIntegrationSuite:

  def spec =
    suite("GameDownloadingProducer")(
      suite("publish")(
        test("should writes commands into the queue") {

          val userName     = UserName(RandomReadableString())
          val platformType = Platform.CHESS_DOT_COM
          val userId       = UserId(RandomReadableString())
          val user         = UserIdentified(platformType.toPlatform, userName, userId)
          val taskId       = TaskId(UUID.randomUUID())

          val resource1 = uri"http://example.com/1"
          val resource2 = uri"http://example.com/2"
          val archives  = Archives(Seq(resource1, resource2))

          val expectedCommand1 = DownloadGameCommand(
            userName = userName.value,
            userId = userId.value,
            platform = Platform.CHESS_DOT_COM,
            resource = resource1,
            taskId = taskId.value
          )

          val expectedCommand2 = DownloadGameCommand(
            userName = userName.value,
            userId = userId.value,
            platform = Platform.CHESS_DOT_COM,
            resource = resource2,
            taskId = taskId.value
          )

          val expectedResult = Set(expectedCommand1, expectedCommand2)

          val firingCommands =
            GameDownloadingProducer.publish(user, archives, taskId)

          val readingFromQueue =
            for
              subscriber             <- ZIO.service[PubSub[DownloadGameCommand]]
              actualCommandsOrErrors <- subscriber.subscribe.run(ZSink.collectAllToSet)
              actualCommands = actualCommandsOrErrors.collect { case Right(command) => command }
            yield actualCommands
          for
            _            <- firingCommands
            actualResult <- readingFromQueue
            result       <- assertTrue(actualResult == expectedResult)
          yield result
        }
      )
    ).provideLayer(
      (configLayer >+> (dynamodbLayer ++ sqsLayer) >+> DownloadGameCommand.Queue.layer) >+> GameDownloadingProducer.Impl.layer
    ) @@ TestAspect.sequential
