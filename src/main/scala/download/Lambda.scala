package chessfinder
package download

import client.chess_com.ChessDotComClient
import pubsub.DownloadGameCommand
import pubsub.core.Subscriber
import search.*
import search.*

import chessfinder.UserIdentified
import chessfinder.app.MainModule
import chessfinder.download.details.{ ArchiveRepo, GameSaver, TaskRepo }
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.amazonaws.services.lambda.runtime.{ Context, RequestHandler }
import io.circe.{ parser, Decoder }
import zio.aws.sqs.Sqs
import zio.config.typesafe.TypesafeConfigProvider
import zio.dynamodb.*
import zio.http.{ App as _, Response }
import zio.{ Runtime, Unsafe, ZIO, ZLayer }

import scala.jdk.CollectionConverters.*

object Lambda extends MainModule with RequestHandler[SQSEvent, Unit]:

  override protected val configLayer = Runtime.setConfigProvider(TypesafeConfigProvider.fromResourcePath())

  private def process(input: SQSEvent, context: Context) =
    for
      messages <- ZIO.succeed(input.getRecords().asScala)
      _        <- ZIO.logInfo(s"Received ${messages.length} DownloadGameCommand")
      _ <- ZIO.collectAll(
        messages.map(message =>
          processSingle(message, context) @@ aspect.MessageId.log(message.getMessageId())
        )
      )
      _ <- ZIO.logInfo(s"DownloadGameCommand in total ${messages.length} have been processed")
    yield ()

  private def processSingle(message: SQSMessage, context: Context) =
    val maybeCommand =
      Option(message.getBody)
        .toRight(
          new RuntimeException(
            s"Body is missing in DownloadGameCommand ${Option(message.getMessageId()).getOrElse("???")}"
          )
        )
        .flatMap(parser.parse)
        .flatMap(Decoder[DownloadGameCommand].decodeJson)

    val processing = maybeCommand match
      case Right(command) =>
        val user =
          UserIdentified(command.platform.toPlatform, UserName(command.userName), UserId(command.userId))
        val archiveId = ArchiveId(command.archiveId)
        val taskId    = TaskId(command.taskId)
        ZIO
          .serviceWithZIO[GameDownloader](_.download(user, archiveId, taskId))
          .tapBoth(
            err => ZIO.logError(s"DownloadGameCommand has failed with $err for archive ${command.archiveId}"),
            _ => ZIO.logInfo(s"DownloadGameCommand ${command.archiveId} has successfully processed")
          )
          .ignore
      case Left(err) => ZIO.logError(s"${err.getMessage} for archive ???")

    val deleting = ZIO
      .serviceWithZIO[Subscriber[DownloadGameCommand]](
        _.acknowledge(Option(message.getReceiptHandle()).getOrElse(""))
      )
      .tapBoth(
        _ => ZIO.logError(s"DownloadGameCommand was not acknowledged"),
        _ => ZIO.logInfo(s"DownloadGameCommand was acknowledged")
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
              clientLayer,
              ChessDotComClient.Impl.layer,
              GameSaver.Impl.layer,
              ArchiveRepo.Impl.layer,
              TaskRepo.Impl.layer,
              GameDownloader.Impl.layer,
              dynamodbLayer,
              sqsLayer,
              DownloadGameCommand.Queue.layer,
              ZLayer.succeed(zio.Clock.ClockLive)
            ) @@
            aspect.BuildInfo.log @@
            aspect.CorrelationId.log(context.getAwsRequestId())
        )
        .getOrThrowFiberFailure()
    }
