package chessfinder
package search

import app.MainModule
import core.SearchFen
import pubsub.SearchBoardCommand
import pubsub.core.Subscriber
import search.details.{ GameFetcher, SearchResultRepo }

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.amazonaws.services.lambda.runtime.{ Context, RequestHandler }
import io.circe.{ parser, Decoder }
import zio.config.typesafe.TypesafeConfigProvider
import zio.dynamodb.*
import zio.http.App as _
import zio.{ Runtime, Unsafe, ZIO, ZLayer }

import scala.jdk.CollectionConverters.*

object Lambda extends MainModule with RequestHandler[SQSEvent, Unit]:

  override protected val configLayer = Runtime.setConfigProvider(TypesafeConfigProvider.fromResourcePath())

  private def process(input: SQSEvent, context: Context) =
    for
      messages <- ZIO.succeed(input.getRecords().asScala)
      _        <- ZIO.logInfo(s"Received ${messages.length} SearchBoardCommand")
      _ <- ZIO.collectAll(
        messages.map(message =>
          processSingle(message, context) @@ aspect.MessageId.log(message.getMessageId())
        )
      )
      _ <- ZIO.logInfo(s"SearchBoardCommand in total ${messages.length} have processed")
    yield ()

  private def processSingle(message: SQSMessage, context: Context) =
    val maybeCommand =
      Option(message.getBody)
        .toRight(
          new RuntimeException(s"Body is missing in Event ${Option(message.getMessageId()).getOrElse("???")}")
        )
        .flatMap(parser.parse)
        .flatMap(Decoder[SearchBoardCommand].decodeJson)

    val processing = maybeCommand match
      case Right(command) =>
        ZIO
          .serviceWithZIO[BoardFinder](
            _.find(SearchFen(command.board), UserId(command.userId), SearchRequestId(command.searchRequestId))
          )
          .tapBoth(
            err =>
              ZIO.logError(
                s"$err for SearchBoardCommand for the user ${command.userId} for the board ${command.board}"
              ),
            _ =>
              ZIO.logInfo(
                s"SearchBoardCommand for the user ${command.userId} for the board ${command.board} has succeessfully processed"
              )
          )
          .ignore
      case Left(err) => ZIO.logError(s"${err.getMessage} for archive ???")

    val deleting = ZIO
      .serviceWithZIO[Subscriber[SearchBoardCommand]](
        _.acknowledge(Option(message.getReceiptHandle()).getOrElse(""))
      )
      .tapBoth(
        _ => ZIO.logError(s"SearchBoardCommand was not acknowledged"),
        _ => ZIO.logInfo(s"SearchBoardCommand was acknowledged")
      )
      .ignore

    processing *> deleting

  override def handleRequest(input: SQSEvent, context: Context): Unit =
    Unsafe.unsafe { implicit unsafe =>
      val runtime = Runtime.unsafe.fromLayer(configLayer >+> loggingLayer)
      runtime.unsafe
        .run(
          process(input, context)
            .provide(
              GameFetcher.Impl.layer,
              BoardFinder.Impl.layer,
              BoardValidator.Impl.layer,
              SearchResultRepo.Impl.layer,
              SearchFacadeAdapter.Impl.layer,
              sqsLayer,
              SearchBoardCommand.Queue.layer,
              dynamodbLayer,
              ZLayer.succeed(zio.Clock.ClockLive)
            ) @@
            aspect.BuildInfo.log @@
            aspect.CorrelationId.log(context.getAwsRequestId())
        )
        .getOrThrowFiberFailure()
    }
