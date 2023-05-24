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
import pubsub.DownloadGameCommand
import zio.ZLayer
import pubsub.core.Subscriber
import zio.stream.ZSink

object GameDownloadingConsumer:

  def consume =
    ZIO.serviceWithZIO[Subscriber[DownloadGameCommand]] { sub =>
      val eff = sub.subscribe.foreach {
        case Right(command) =>
          val user =
            UserIdentified(command.platform.toPlatform, UserName(command.userName), UserId(command.userId))
          val archives = Archives(Seq(command.resource))
          val taskId   = TaskId(command.taskId)
          GameDownloader
            .download(user, archives, taskId)
            .tapBoth(
              err => ZIO.logError(s"$err for archive ${command.resource}"),
              _ => ZIO.logInfo(s"Command ${command.resource} has succeessfully processed")
            )
            .ignore
        case Left(err) => ZIO.logError(s"${err.getMessage} for archive ???")
      }
      eff.tapError(err => ZIO.logError(s"${err.getMessage} for archive ???"))
    }
