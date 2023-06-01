package chessfinder
package search.queue

import client.chess_com.dto.Archives
import pubsub.DownloadGameCommand
import pubsub.core.Publisher
import search.*
import search.entity.*
import search.queue.*

import zio.{ Cause, ZIO, ZLayer }
import zio.sqs.producer.{ Producer, ProducerEvent }

trait GameDownloadingProducer:

  def publish(user: UserIdentified, archives: Seq[ArchiveId], taskId: TaskId): φ[Unit]

object GameDownloadingProducer:

  class Impl(publisher: Publisher[DownloadGameCommand]) extends GameDownloadingProducer:

    override def publish(user: UserIdentified, archives: Seq[ArchiveId], taskId: TaskId): φ[Unit] =
      val commands = archives.map(archive => DownloadGameCommand(user, archive, taskId))
      ZIO.scoped {
        publisher
          .publish(commands.map(_.command))
          .tapBoth(
            err => ZIO.logErrorCause(s"Publishing events failed with ${err.getMessage()}", Cause.fail(err)),
            _ => ZIO.logInfo(s"Publishing ${commands.length} events has been successful.")
          )
          .ignore
      }

  object Impl:
    val layer =
      ZLayer.apply(ZIO.service[Publisher[DownloadGameCommand]].map(publisher => Impl(publisher)))
