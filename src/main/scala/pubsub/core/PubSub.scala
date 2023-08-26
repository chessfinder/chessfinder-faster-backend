package chessfinder
package pubsub.core

import io.circe.{ parser, Codec, Decoder }
import software.amazon.awssdk.regions.Region
import zio.*
import zio.aws.sqs.*
import zio.aws.sqs.model.{ DeleteMessageRequest, Message, MessageAttributeValue }
import zio.config.*
import zio.config.magnolia.deriveConfig
import zio.sqs.*
import zio.sqs.producer.{ Producer, ProducerEvent, ProducerSettings }
import zio.sqs.serialization.Serializer
import zio.stream.*

import java.net.URI

trait Subscriber[T: Codec](
    queueUrl: String,
    sqs: Sqs
):
  def subscribe: ZStream[Any, Throwable, Either[Throwable, T]] =
    SqsStream(queueUrl, Subscriber.DefaultSettings)
      .map[Either[Throwable, T]](event =>
        event.body
          .toRight(new RuntimeException(s"Body is missing in Event ${event.messageId.getOrElse("???")}"))
          .flatMap(parser.parse)
          .flatMap(Decoder[T].decodeJson)
      )
      .provideLayer(ZLayer.succeed(sqs))

  def acknowledge(receiptHandle: String): Task[Unit] =
    sqs.deleteMessage(DeleteMessageRequest(queueUrl, receiptHandle)).mapError(_.toThrowable)

object Subscriber:
  val DefaultSettings: SqsStreamSettings = SqsStreamSettings(stopWhenQueueEmpty = true)

trait Publisher[T: Codec](queueUrl: String, sqs: Sqs):

  val producer = Producer.make(
    queueUrl,
    PubSub.serializer[T],
    settings = Publisher.DefaultSettings
  )

  private def publishEvents(data: Seq[T]): Task[Unit] =
    val events = data.map(event =>
      ProducerEvent(
        data = event,
        attributes = Map.empty[String, MessageAttributeValue],
        groupId = None,
        deduplicationId = None,
        delay = None
      )
    )
    publish(events)

  def publish(events: Seq[ProducerEvent[T]]): Task[Unit] =
    ZIO.scoped(producer.flatMap(_.produceBatch(events))).unit.provide(ZLayer.succeed(sqs))

object Publisher:
  val DefaultSettings: ProducerSettings = ProducerSettings().copy(batchSize = 1)

class PubSub[T: Codec](
    queueUrl: String,
    sqs: Sqs
) extends Subscriber(queueUrl, sqs)
    with Publisher(queueUrl, sqs)

object PubSub:

  def serializer[T: Codec]: Serializer[T] =
    Serializer.serializeString.contramap[T](command => Codec[T].apply(command).noSpaces)

  def layer[T: Codec](queueName: String): ZIO[Sqs, Throwable, PubSub[T]] =
    for
      queueUrl <- Utils.getQueueUrl(queueName)
      _        <- ZIO.log(s"Queue ${queueName} is available under the url ${queueUrl}")
      sqs      <- ZIO.service[Sqs]
    yield PubSub(queueUrl, sqs)
