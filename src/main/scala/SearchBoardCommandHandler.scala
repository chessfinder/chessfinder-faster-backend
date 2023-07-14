package chessfinder

import api.Controller
import client.ZLoggingAspect
import client.chess_com.ChessDotComClient
import client.chess_com.dto.Archives
import persistence.core.DefaultDynamoDBExecutor
import pubsub.SearchBoardCommand
import pubsub.core.Subscriber
import search.*
import search.entity.*
import search.queue.*
import search.repo.{ GameRepo, TaskRepo, UserRepo }
import sttp.apispec.openapi.Server as OAServer
import sttp.apispec.openapi.circe.yaml.*
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.redoc.*
import sttp.tapir.server.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.serverless.aws.ziolambda.{ AwsZioServerOptions, ZioLambdaHandler }
import sttp.tapir.serverless.aws.lambda.{ AwsRequest, LambdaHandler }
import sttp.tapir.swagger.*
import sttp.tapir.ztapir.*
import util.EndpointCombiner

import cats.effect.unsafe.implicits.global
import cats.implicits.*
import com.amazonaws.services.lambda.runtime.events.SQSEvent
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage
import com.amazonaws.services.lambda.runtime.{ Context, RequestHandler, RequestStreamHandler }
import io.circe.generic.auto.*
import io.circe.{ parser, Decoder }
import zio.aws.core.config.AwsConfig
import zio.aws.netty
import zio.config.typesafe.TypesafeConfigProvider
import zio.dynamodb.*
import zio.http.{ App as _, HttpApp, Request, Response, * }
import zio.logging.*
import zio.sqs.producer.{ Producer, ProducerEvent }
import zio.stream.ZSink
import zio.{ Cause, Runtime, Task, Unsafe, ZIO, ZIOApp, ZIOAppDefault, ZLayer, * }
import core.SearchFen
import java.io.{ InputStream, OutputStream }
import scala.jdk.CollectionConverters.*
import search.repo.SearchResultRepo

object SearchBoardCommandHandler extends BaseMain with RequestHandler[SQSEvent, Unit]:

  override protected val configLayer = Runtime.setConfigProvider(TypesafeConfigProvider.fromResourcePath())

  private def process(input: SQSEvent, context: Context) =
    for
      messages <- ZIO.succeed(input.getRecords().asScala)
      _        <- ZIO.logInfo(s"Recieved ${messages.length} SearchBoardCommand")
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
        err => ZIO.logError(s"SearchBoardCommand was not acknowledged"),
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
              GameRepo.Impl.layer,
              BoardFinder.Impl.layer,
              BoardValidator.Impl.layer,
              SearchResultRepo.Impl.layer,
              Searcher.Impl.layer,
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
