package chessfinder
package pubsub

import pubsub.core.PubSub

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import zio.*
import zio.aws.sqs.*
import zio.aws.sqs.model.MessageAttributeValue
import zio.config.*
import zio.config.magnolia.deriveConfig
import zio.sqs.producer.ProducerEvent

import java.util.UUID

final case class DownloadGameCommand(
    userName: String,
    userId: String,
    platform: Platform,
    archiveId: String,
    taskId: UUID
):
  def command: ProducerEvent[DownloadGameCommand] =
    ProducerEvent(
      data = this,
      attributes = Map.empty[String, MessageAttributeValue],
      groupId = None,
      deduplicationId = None,
      delay = None
    )

object DownloadGameCommand:
  given Codec[DownloadGameCommand] = deriveCodec[DownloadGameCommand]

  def apply(user: UserIdentified, archiveId: ArchiveId, taskId: TaskId): DownloadGameCommand =
    DownloadGameCommand(
      user.userName.value,
      user.userId.value,
      Platform.fromPlatform(user.platform),
      archiveId.value,
      taskId.value
    )

  object Queue:
    case class Configuration(name: String)
    object Configuration:
      given config: Config[Configuration] =
        deriveConfig[Configuration].nested("sqs-config", "queues", "download-games")

    val layer: ZLayer[Sqs, Throwable, PubSub[DownloadGameCommand]] = ZLayer.fromZIO {
      for
        config <- ZIO.config[Configuration](Configuration.config)
        queue  <- PubSub.layer[DownloadGameCommand](config.name)
      yield queue
    }
