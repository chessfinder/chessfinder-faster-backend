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
  given Codec[SearchBoardCommand] = deriveCodec[SearchBoardCommand]

  object Queue:
    case class Configuration(name: String)
    object Configuration:
      given config: Config[Configuration] =
        deriveConfig[Configuration].nested("sqs-config", "queues", "search-board")

    val layer: ZLayer[Sqs, Throwable, PubSub[SearchBoardCommand]] = ZLayer.fromZIO {
      for
        config <- ZIO.config[Configuration](Configuration.config)
        queue  <- PubSub.layer[SearchBoardCommand](config.name)
      yield queue
    }
