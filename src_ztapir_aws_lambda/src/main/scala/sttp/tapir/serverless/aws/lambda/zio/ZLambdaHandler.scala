package sttp.tapir.serverless.aws.lambda.zio

import cats.implicits.*
import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import io.circe.*
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.EncoderOps
import sttp.tapir.server.ServerEndpoint
import java.io.{ BufferedWriter, InputStream, OutputStream, OutputStreamWriter }
import java.nio.charset.StandardCharsets
import zio.{ RIO, Task, ZIO }
import sttp.tapir.ztapir.*
import scala.util.Try
import sttp.tapir.serverless.aws.lambda.{ AwsRequest, AwsRequestV1, AwsResponse }
import cats.MonadError
import sttp.tapir.server.ziohttp.ZioHttpServerOptions

/** [[ZLambdaHandler]] is an entry point for handling requests sent to AWS Lambda application which exposes Tapir endpoints.
  *
  * @tparam F
  *   The effect type constructor used in the endpoint.
  * @tparam R
  *   AWS API Gateway request type [[AwsRequestV1]] or [[AwsRequest]]. At the moment mapping is required as there is no support for
  *   generating API Gateway V2 definitions with AWS CDK v2.
  */
abstract class ZLambdaHandler[Env: RIOMonadError]:

  protected def getAllEndpoints: List[ZServerEndpoint[Env, Any]]

  def process[R: Decoder](input: InputStream, output: OutputStream): RIO[Env, Unit] =

    val server: AwsZServerInterpreter[Env] =
      val serverLogger =
        ZioHttpServerOptions.defaultServerLog[Env]
          .copy(
            logWhenReceived = true,
            logAllDecodeFailures = true
          )

      val options =
        AwsZServerOptions.noEncoding[Env](
          AwsZServerOptions.customiseInterceptors[Env]
            .serverLog(serverLogger)
            .options
        )
      AwsZServerInterpreter[Env](options)

    for
      allBytes <- ZIO.attempt(input.readAllBytes())
      str = new String(allBytes, StandardCharsets.UTF_8)
      decoded = decode[R](str)
      response <- decoded match
        case Left(e) => ZIO.succeed(AwsResponse.badRequest(s"Invalid AWS request: ${e.getMessage}"))
        case Right(r: AwsRequestV1) => server.toRoute(getAllEndpoints)(r.toV2)
        case Right(r: AwsRequest) => server.toRoute(getAllEndpoints)(r)
        case Right(r) =>
          val message = s"Request of type ${r.getClass.getCanonicalName} is not supported"
          ZIO.fail(new IllegalArgumentException(message))
      _ <- writerResource(response, output)
    yield ()

  private def writerResource(response: AwsResponse, output: OutputStream): Task[Unit] =
    val acquire = ZIO.attempt(new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8)))
    val release = (writer: BufferedWriter) =>
      ZIO.attempt {
        writer.flush()
        writer.close()
      }.orDie
    val use = (writer: BufferedWriter) => ZIO.attempt(writer.write(Printer.noSpaces.print(response.asJson)))
    ZIO.acquireReleaseWith(acquire)(release)(use)

object ZLambdaHandler:

  def apply[Env: RIOMonadError](endpoints: List[ZServerEndpoint[Env, Any]]): ZLambdaHandler[Env] =
    new ZLambdaHandler[Env]:
      override protected def getAllEndpoints: List[ZServerEndpoint[Env, Any]] = endpoints

  def withMonadError[Env](endpoints: List[ZServerEndpoint[Env, Any]]): ZLambdaHandler[Env] =
    given RIOMonadError[Env] = RIOMonadError[Env]
    ZLambdaHandler(endpoints)
