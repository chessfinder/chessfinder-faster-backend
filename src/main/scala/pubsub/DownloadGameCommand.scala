package chessfinder
package pubsub

import search.entity.*
import persistence.PlatformType
import sttp.model.Uri
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import zio.sqs.producer.ProducerEvent
import zio.sqs.serialization.Serializer
import zio.aws.sqs.model.MessageAttributeValue
import java.util.UUID
import pubsub.core.PubSub
import zio.Config
import zio.config.*
import zio.config.magnolia.deriveConfig
import zio.ZLayer
import zio.ZIO
import zio.*
import zio.aws.sqs.*
import com.typesafe.config.ConfigFactory

final case class DownloadGameCommand(
    userName: String,
    userId: String,
    platform: Platform,
    resource: Uri,
    taskId: UUID
):
  def command: ProducerEvent[DownloadGameCommand] =
    ProducerEvent(
      data = this,
      attributes = Map.empty[String, MessageAttributeValue],
      groupId = Some(userId),
      deduplicationId = Some(resource.toString),
      delay = None
    )

object DownloadGameCommand:
  import chessfinder.util.UriCodec.given
  given Codec[DownloadGameCommand] = deriveCodec[DownloadGameCommand]

  def apply(user: UserIdentified, archive: Uri, taskId: TaskId): DownloadGameCommand =
    DownloadGameCommand(
      user.userName.value,
      user.userId.value,
      Platform.fromPlatform(user.platform),
      archive,
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
