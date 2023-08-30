package chessfinder
package api

import client.chess_com.ChessDotComClient
import download.details.{ ArchiveRepo, DownloadGameCommandPublisher, TaskRepo, UserRegister }
import download.{ ArchiveDownloader, DownloadStatusChecker }
import pubsub.{ DownloadGameCommand, SearchBoardCommand }
import search.*
import search.details.{ SearchBoardCommandPublisher, SearchResultRepo, UserFetcher }

import chessfinder.app.MainModule
import com.amazonaws.services.lambda.runtime.{ Context, RequestStreamHandler }
import io.circe.generic.auto.*
import sttp.tapir.serverless.aws.lambda.AwsRequest
import sttp.tapir.serverless.aws.ziolambda.{ AwsZioServerOptions, ZioLambdaHandler }
import sttp.tapir.ztapir.*
import zio.*
import zio.config.typesafe.TypesafeConfigProvider
import zio.dynamodb.*
import zio.http.{ App as _, Response }

import java.io.{ InputStream, OutputStream }

object Lambda extends MainModule with RequestStreamHandler:

  override protected val configLayer = Runtime.setConfigProvider(TypesafeConfigProvider.fromResourcePath())

  private def handler[R](endpoints: List[ZServerEndpoint[R, Any]]) =
    given RIOMonadError[R] = RIOMonadError[R]
    def options[R] =
      AwsZioServerOptions.noEncoding[R](
        AwsZioServerOptions
          .customiseInterceptors[R]
          .serverLog(serverLogger)
          .options
      )
    ZioLambdaHandler(
      endpoints,
      options
    )

  def process(input: InputStream, output: OutputStream) =
    handler(controller.rest)
      .process[AwsRequest](input, output)
      .provide(
        clientLayer,
        BoardValidator.Impl.layer,
        ChessDotComClient.Impl.layer,
        UserFetcher.Impl.layer,
        UserRegister.Impl.layer,
        TaskRepo.Impl.layer,
        ArchiveRepo.Impl.layer,
        SearchResultRepo.Impl.layer,
        DownloadStatusChecker.Impl.layer,
        ArchiveDownloader.Impl.layer,
        DownloadGameCommandPublisher.Impl.layer,
        DownloadGameCommand.Queue.layer,
        SearchBoardCommand.Queue.layer,
        SearchRequestRegister.Impl.layer,
        SearchResultChecker.Impl.layer,
        SearchBoardCommandPublisher.Impl.layer,
        dynamodbLayer,
        sqsLayer,
        ZLayer.succeed(zio.Random.RandomLive),
        ZLayer.succeed(zio.Clock.ClockLive),
        ZLayer.fromZIO(ZIO.unit) // Holly mother of God
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
