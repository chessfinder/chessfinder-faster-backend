package chessfinder
package search.details

import client.*
import client.ClientError.*
import core.SearchFen
import pubsub.core.PubSub
import pubsub.{ Platform, SearchBoardCommand }
import search.*
import testkit.NarrowIntegrationSuite
import util.RandomReadableString

import zio.*
import zio.stream.ZSink
import zio.test.*

import java.util.UUID

object SearchBoardCommandPublisherTest extends NarrowIntegrationSuite:

  val queue = ZIO.service[SearchBoardCommandPublisher]
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
      (configLayer >+> sqsLayer >+> SearchBoardCommand.Queue.layer) >+> SearchBoardCommandPublisher.Impl.layer
    ) @@ TestAspect.sequential
