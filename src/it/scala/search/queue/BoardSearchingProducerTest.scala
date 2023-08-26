package chessfinder
package search.queue

import client.*
import client.ClientError.*
import client.chess_com.ChessDotComClient
import client.chess_com.dto.*
import core.SearchFen
import persistence.GameRecord
import persistence.core.DefaultDynamoDBExecutor
import pubsub.core.PubSub
import pubsub.{ Platform, SearchBoardCommand }
import search.entity.*
import testkit.NarrowIntegrationSuite
import testkit.parser.JsonReader
import testkit.wiremock.ClientBackdoor
import util.{ RandomReadableString, UriParser }

import chess.format.pgn.PgnStr
import com.typesafe.config.ConfigFactory
import io.circe.*
import sttp.model.Uri
import sttp.model.Uri.UriContext
import sttp.model.UriInterpolator.*
import zio.*
import zio.aws.core.config.AwsConfig
import zio.aws.netty
import zio.dynamodb.*
import zio.http.Client
import zio.stream.ZSink
import zio.test.*

import java.util.UUID
import scala.util.{ Success, Try }

object BoardSearchingProducerTest extends NarrowIntegrationSuite:

  val queue = ZIO.service[BoardSearchingProducer]
  def spec =
    suite("BoardSearchingProducer")(
      suite("publish")(
        test("should writes commands into the queue") {

          val userName     = UserName(RandomReadableString())
          val platformType = Platform.CHESS_DOT_COM
          val userId       = UserId(RandomReadableString())
          val user         = UserIdentified(platformType.toPlatform, userName, userId)

          val searchRequestId = SearchRequestId(UUID.randomUUID())
          val board = SearchFen("????R?r?/?????kq?/????Q???/????????/????????/????????/????????/????????")

          val expectedCommand = SearchBoardCommand(
            userId = userId.value,
            board = board.value,
            searchRequestId = searchRequestId.value
          )

          val expectedResult = Set(expectedCommand)

          val readingFromQueue =
            for
              subscriber             <- ZIO.service[PubSub[SearchBoardCommand]]
              actualCommandsOrErrors <- subscriber.subscribe.run(ZSink.collectAllToSet)
              actualCommands = actualCommandsOrErrors.collect { case Right(command) => command }
            yield actualCommands

          for
            boardSearchingProducer <- queue
            _                      <- boardSearchingProducer.publish(user, board, searchRequestId)
            actualResult           <- readingFromQueue
            result                 <- assertTrue(actualResult == expectedResult)
          yield result
        }
      )
    ).provideLayer(
      (configLayer >+> sqsLayer >+> SearchBoardCommand.Queue.layer) >+> BoardSearchingProducer.Impl.layer
    ) @@ TestAspect.sequential
