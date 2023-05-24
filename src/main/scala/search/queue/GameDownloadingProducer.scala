package chessfinder
package search.queue

import search.*
import queue.*
import zio.ZIO
import zio.sqs.producer.Producer
import zio.sqs.producer.ProducerEvent
import zio.Cause
import search.entity.*
import chessfinder.client.chess_com.dto.Archives
import pubsub.core.Publisher
import pubsub.DownloadGameCommand
import zio.ZLayer

trait GameDownloadingProducer:

  def publish(user: UserIdentified, archives: Archives, taskId: TaskId): φ[Unit]

object GameDownloadingProducer:

  def publish(user: UserIdentified, archives: Archives, taskId: TaskId): ψ[GameDownloadingProducer, Unit] =
    ZIO.serviceWithZIO[GameDownloadingProducer](_.publish(user, archives, taskId))

  class Impl(publisher: Publisher[DownloadGameCommand]) extends GameDownloadingProducer:

    override def publish(user: UserIdentified, archives: Archives, taskId: TaskId): φ[Unit] =
      val commands = archives.archives.map(archive => DownloadGameCommand(user, archive, taskId))
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
