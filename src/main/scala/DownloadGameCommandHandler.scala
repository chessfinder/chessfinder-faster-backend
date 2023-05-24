package chessfinder

import zio.ZIOApp
import zio.ZIOAppDefault

import sttp.tapir.ztapir.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import zio.http.{ HttpApp, Request, Response }
import zio.*
import zio.http.{ App as _, * }
import chessfinder.search.GameFinder
import sttp.apispec.openapi.Server as OAServer
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.swagger.*
import sttp.tapir.redoc.*
import sttp.tapir.redoc.RedocUIOptions
import sttp.apispec.openapi.circe.yaml.*
import sttp.tapir.server.*
import chessfinder.search.BoardValidator
import chessfinder.search.GameFetcher
import chessfinder.search.Searcher
import chessfinder.search.TaskStatusChecker
import chessfinder.search.GameDownloader
import chessfinder.search.ArchiveDownloader
import chessfinder.client.chess_com.ChessDotComClient
import sttp.tapir.serverless.aws.lambda.LambdaHandler

import cats.effect.unsafe.implicits.global
import com.amazonaws.services.lambda.runtime.Context
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.serverless.aws.lambda.{ AwsRequest, LambdaHandler }
import java.io.{ InputStream, OutputStream }
import cats.implicits.*
import sttp.tapir.serverless.aws.lambda.zio.ZLambdaHandler
import zio.Task
import zio.{ Task, ZIO }
import cats.effect.unsafe.implicits.global
import com.amazonaws.services.lambda.runtime.Context
import io.circe.generic.auto.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.serverless.aws.lambda.{ AwsRequest, LambdaHandler }
import java.io.{ InputStream, OutputStream }
import sttp.tapir.serverless.aws.lambda.zio.ZLambdaHandler
import sttp.tapir.ztapir.ZServerEndpoint
import sttp.tapir.ztapir.RIOMonadError
import zio.{ Runtime, Unsafe }
import chessfinder.api.{ AsyncController, SyncController }
import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import zio.logging.*
import chessfinder.client.ZLoggingAspect
import zio.logging.backend.SLF4J
import chessfinder.api.ApiVersion
import chessfinder.search.repo.{ GameRepo, TaskRepo, UserRepo }
import zio.aws.netty
import zio.aws.core.config.AwsConfig
import persistence.core.DefaultDynamoDBExecutor
import zio.dynamodb.*
import util.EndpointCombiner
import zio.config.typesafe.TypesafeConfigProvider
import sttp.tapir.serverless.aws.lambda.zio.AwsZServerOptions
import search.queue.GameDownloadingProducer
import pubsub.DownloadGameCommand

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage

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
import io.circe.Decoder
import io.circe.parser
import scala.jdk.CollectionConverters.*

object DownloadGameCommandHandler extends BaseMain with RequestHandler[SQSEvent, Unit]:

  override protected val configLayer = Runtime.setConfigProvider(TypesafeConfigProvider.fromResourcePath())

  private def process(input: SQSEvent, context: Context) =
    for
      messages <- ZIO.succeed(input.getRecords().asScala)
      _        <- ZIO.logInfo(s"Recieved Messages ${messages.length}")
      _ <- ZIO.collectAll(
        messages.map(message =>
          processSingle(message, context) @@ aspect.MessageId.log(message.getMessageId())
        )
      )
      _ <- ZIO.logInfo(s"Messages ${messages.length} are processed")
    yield ()

  private def processSingle(message: SQSMessage, context: Context) =
    val maybeCommand =
      Option(message.getBody)
        .toRight(
          new RuntimeException(s"Body is missing in Event ${Option(message.getMessageId()).getOrElse("???")}")
        )
        .flatMap(parser.parse)
        .flatMap(Decoder[DownloadGameCommand].decodeJson)

    maybeCommand match
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

  override def handleRequest(input: SQSEvent, context: Context): Unit =
    Unsafe.unsafe { implicit unsafe =>
      val runtime = Runtime.unsafe.fromLayer(configLayer >+> loggingLayer)
      runtime.unsafe
        .run(
          process(input, context)
            .provide(
              clientLayer,
              ChessDotComClient.Impl.layer,
              UserRepo.Impl.layer,
              GameRepo.Impl.layer,
              TaskRepo.Impl.layer,
              GameDownloader.Impl.layer,
              dynamodbLayer,
              ZLayer.succeed(zio.Random.RandomLive)
            ) @@
            aspect.BuildInfo.log @@
            aspect.CorrelationId.log(context.getAwsRequestId())
        )
        .getOrThrowFiberFailure()
    }
