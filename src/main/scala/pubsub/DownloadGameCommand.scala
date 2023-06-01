package chessfinder
package pubsub

import persistence.PlatformType
import pubsub.core.PubSub
import search.entity.*
import sttp.model.Uri

import com.typesafe.config.ConfigFactory
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import zio.*
import zio.aws.sqs.*
import zio.aws.sqs.model.MessageAttributeValue
import zio.config.*
import zio.config.magnolia.deriveConfig
import zio.sqs.producer.ProducerEvent
import zio.sqs.serialization.Serializer

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
      groupId = Some(userId),
      deduplicationId = Some(archiveId),
      delay = None
    )

object DownloadGameCommand:
  import chessfinder.util.UriCodec.given
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
    case class Configuration(url: String)
    object Configuration:
      given config: Config[Configuration] =
        deriveConfig[Configuration].nested("sqs-config", "queues", "download-games")

    val layer: ZLayer[Sqs, Throwable, PubSub[DownloadGameCommand]] = ZLayer.fromZIO {
      for
        config <- ZIO.config[Configuration](Configuration.config)
        queue  <- PubSub.layer[DownloadGameCommand](config.url)
      yield queue
    }
