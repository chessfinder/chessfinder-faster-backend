package chessfinder

import client.chess_com.ChessDotComClient
import sttp.apispec.openapi.Server as OAServer
import sttp.apispec.openapi.circe.yaml.*
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.redoc.*
import sttp.tapir.server.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.serverless.aws.lambda.{ AwsRequest, LambdaHandler }
import sttp.tapir.swagger.*
import sttp.tapir.ztapir.*

import cats.effect.unsafe.implicits.global
import cats.implicits.*
import io.circe.generic.auto.*
import zio.http.{ App as _, * }
import chessfinder.api.Controller
import client.ZLoggingAspect
import client.chess_com.dto.Archives
import persistence.core.DefaultDynamoDBExecutor
import pubsub.DownloadGameCommand
import pubsub.core.Subscriber
import search.*
import search.entity.*
import search.queue.*
import search.repo.{ ArchiveRepo, GameRepo, TaskRepo, UserRepo }
import sttp.tapir.serverless.aws.lambda.zio.{ AwsZServerOptions, ZLambdaHandler }
import util.EndpointCombiner

import com.amazonaws.services.lambda.runtime.{ Context, RequestHandler, RequestStreamHandler }
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import io.circe.{ parser, Decoder }
import zio.aws.core.config.AwsConfig
import zio.aws.netty
import zio.config.typesafe.TypesafeConfigProvider
import zio.dynamodb.*
import zio.http.{ HttpApp, Request, Response }
import zio.logging.*
import zio.logging.backend.SLF4J
import zio.sqs.producer.{ Producer, ProducerEvent }
import zio.stream.ZSink
import zio.{ Cause, Runtime, Task, Unsafe, ZIO, ZIOApp, ZIOAppDefault, ZLayer, * }
import zio.aws.sqs.Sqs
import zio.aws.sqs.model.DeleteMessageRequest
import java.io.{ InputStream, OutputStream }
import scala.jdk.CollectionConverters.*

object DownloadGameCommandHandler extends BaseMain with RequestHandler[SQSEvent, Unit]:

  override protected val configLayer = Runtime.setConfigProvider(TypesafeConfigProvider.fromResourcePath())

  private def process(input: SQSEvent, context: Context) =
    for
      messages <- ZIO.succeed(input.getRecords().asScala)
      _        <- ZIO.logInfo(s"Recieved ${messages.length} DownloadGameCommand")
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
        err => ZIO.logError(s"DownloadGameCommand was not acknowledged"),
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
              GameRepo.Impl.layer,
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
