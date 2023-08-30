package chessfinder
package search.details

import core.SearchFen
import pubsub.SearchBoardCommand
import pubsub.core.Publisher

import zio.sqs.producer.ProducerEvent
import zio.{ Cause, ZIO, ZLayer }

trait SearchBoardCommandPublisher:

  def publish(user: UserIdentified, board: SearchFen, searchRequestId: SearchRequestId): Computation[Unit]

object SearchBoardCommandPublisher:

  class Impl(publisher: Publisher[SearchBoardCommand]) extends SearchBoardCommandPublisher:

    override def publish(
        user: UserIdentified,
        board: SearchFen,
        searchRequestId: SearchRequestId
    ): Computation[Unit] =
      val command = SearchBoardCommand(user.userId.value, board.value, searchRequestId.value)
      ZIO.scoped {
        publisher
          .publish(Seq(command.command))
          .tapBoth(
            err => ZIO.logErrorCause(s"Publishing event failed with ${command.toString}", Cause.fail(err)),
            _ => ZIO.logInfo(s"Publishing ${command.searchRequestId} event has been successful.")
          )
          .ignore
      }

  object Impl:
    val layer =
      ZLayer.apply(ZIO.service[Publisher[SearchBoardCommand]].map(publisher => Impl(publisher)))
