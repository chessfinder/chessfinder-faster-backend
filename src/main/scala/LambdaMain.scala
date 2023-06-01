package chessfinder

import client.ZLoggingAspect
import client.chess_com.ChessDotComClient
import persistence.core.DefaultDynamoDBExecutor
import pubsub.{ DownloadGameCommand, SearchBoardCommand }
import search.*
import search.queue.{ BoardSearchingProducer, GameDownloadingProducer }
import search.repo.*
import sttp.apispec.openapi.Server as OAServer
import sttp.apispec.openapi.circe.yaml.*
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.redoc.*
import sttp.tapir.server.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.serverless.aws.lambda.zio.{ AwsZServerOptions, ZLambdaHandler }
import sttp.tapir.serverless.aws.lambda.{ AwsRequest, LambdaHandler }
import sttp.tapir.swagger.*
import sttp.tapir.ztapir.*
import util.EndpointCombiner

import cats.effect.unsafe.implicits.global
import cats.implicits.*
import com.amazonaws.services.lambda.runtime.{ Context, RequestStreamHandler }
import io.circe.generic.auto.*
import zio.aws.core.config.AwsConfig
import zio.aws.netty
import zio.config.typesafe.TypesafeConfigProvider
import zio.dynamodb.*
import zio.http.{ App as _, HttpApp, Request, Response, * }
import zio.logging.*
import zio.{ Runtime, Task, Unsafe, ZIO, ZIOApp, ZIOAppDefault, * }

import java.io.{ InputStream, OutputStream }

object LambdaMain extends BaseMain with RequestStreamHandler:

  override protected val configLayer = Runtime.setConfigProvider(TypesafeConfigProvider.fromResourcePath())

  private val handler =
    def options[R] =
      AwsZServerOptions.noEncoding[R](
        AwsZServerOptions
          .customiseInterceptors[R]
          .serverLog(serverLogger)
          .options
      )
    ZLambdaHandler.withMonadError(
      controller.rest,
      options
    )

  def process(input: InputStream, output: OutputStream) =
    handler
      .process[AwsRequest](input, output)
      .provide(
        clientLayer,
        BoardValidator.Impl.layer,
        ChessDotComClient.Impl.layer,
        UserRepo.Impl.layer,
        TaskRepo.Impl.layer,
        ArchiveRepo.Impl.layer,
        SearchResultRepo.Impl.layer,
        TaskStatusChecker.Impl.layer,
        ArchiveDownloader.Impl.layer,
        GameDownloadingProducer.Impl.layer,
        DownloadGameCommand.Queue.layer,
        SearchBoardCommand.Queue.layer,
        SearchRequestAcceptor.Impl.layer,
        BoardSearchingProducer.Impl.layer,
        dynamodbLayer,
        sqsLayer,
        ZLayer.succeed(zio.Random.RandomLive),
        ZLayer.succeed(zio.Clock.ClockLive)
      )

  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit =
    Unsafe.unsafe { implicit unsafe =>
      val runtime = Runtime.unsafe.fromLayer(configLayer >+> loggingLayer)
      runtime.unsafe
        .run(
          process(input, output) @@ aspect.BuildInfo.log @@ aspect.CorrelationId
            .log(context.getAwsRequestId())
        )
        .getOrThrowFiberFailure()
    }
