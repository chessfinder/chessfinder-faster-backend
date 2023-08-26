package chessfinder
package search.queue

import client.chess_com.dto.Archives
import core.SearchFen
import pubsub.SearchBoardCommand
import pubsub.core.Publisher
import search.*
import search.entity.*
import search.queue.*

import zio.sqs.producer.{ Producer, ProducerEvent }
import zio.{ Cause, ZIO, ZLayer }

trait BoardSearchingProducer:

  def publish(user: UserIdentified, board: SearchFen, searchRequestId: SearchRequestId): φ[Unit]

object BoardSearchingProducer:

  class Impl(publisher: Publisher[SearchBoardCommand]) extends BoardSearchingProducer:

    override def publish(user: UserIdentified, board: SearchFen, searchRequestId: SearchRequestId): φ[Unit] =
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
