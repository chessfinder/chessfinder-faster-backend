package chessfinder
package pubsub

import chessfinder.core.SearchFen
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

final case class SearchBoardCommand(
    userId: String,
    board: String,
    searchRequestId: UUID
):
  def command: ProducerEvent[SearchBoardCommand] =
    ProducerEvent(
      data = this,
      attributes = Map.empty[String, MessageAttributeValue],
      groupId = Some(userId),
      deduplicationId = Some(searchRequestId.toString),
      delay = None
    )

object SearchBoardCommand:
  import chessfinder.util.UriCodec.given
  given Codec[SearchBoardCommand] = deriveCodec[SearchBoardCommand]

  object Queue:
    case class Configuration(url: String)
    object Configuration:
      given config: Config[Configuration] =
        deriveConfig[Configuration].nested("sqs-config", "queues", "search-board")

    val layer: ZLayer[Sqs, Throwable, PubSub[SearchBoardCommand]] = ZLayer.fromZIO {
      for
        config <- ZIO.config[Configuration](Configuration.config)
        queue  <- PubSub.layer[SearchBoardCommand](config.url)
      yield queue
    }
