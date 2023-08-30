package chessfinder
package download.details

import pubsub.DownloadGameCommand
import pubsub.core.Publisher

import zio.{ Cause, ZIO, ZLayer }

trait DownloadGameCommandPublisher:

  def publish(user: UserIdentified, archives: Seq[ArchiveId], taskId: TaskId): Computation[Unit]

object DownloadGameCommandPublisher:

  class Impl(publisher: Publisher[DownloadGameCommand]) extends DownloadGameCommandPublisher:

    override def publish(user: UserIdentified, archives: Seq[ArchiveId], taskId: TaskId): Computation[Unit] =
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
