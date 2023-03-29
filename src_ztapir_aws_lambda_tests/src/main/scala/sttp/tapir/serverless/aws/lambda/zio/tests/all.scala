package sttp.tapir.serverless.aws.lambda.zio.tests

import cats.implicits.*
import sttp.tapir.*
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.tests.Basic._
import sttp.tapir.tests.Mapping.in_4query_out_4header_extended
import sttp.tapir.tests.TestUtil.inputStreamToByteArray
import sttp.tapir.ztapir.ZServerEndpoint
import java.io.{ByteArrayInputStream, InputStream}
import sttp.tapir.ztapir.ZTapir
import zio.ZIO

object all extends ZTapir:

  type ZEndpoint = ZServerEndpoint[Any, Any]
  // this endpoint is used to wait until sam local starts up before running actual tests
  val health_endpoint: ZEndpoint =
    endpoint.get.in("health").zServerLogic(_ => ZIO.unit)

  val in_path_path_out_string_endpoint: ZEndpoint =
    in_path_path_out_string.zServerLogic { case (fruit: String, amount: Int) =>
      ZIO.succeed(s"$fruit $amount")
    }

  val in_string_out_string_endpoint: ZEndpoint =
    in_string_out_string.in("string").zServerLogic(ZIO.succeed)

  val in_json_out_json_endpoint: ZEndpoint =
    in_json_out_json.in("json").zServerLogic(ZIO.succeed)

  val in_headers_out_headers_endpoint: ZEndpoint =
    in_headers_out_headers.zServerLogic(ZIO.succeed)

  val in_input_stream_out_input_stream_endpoint: ZEndpoint =
    in_input_stream_out_input_stream.in("is").zServerLogic { is => ZIO.attempt(new ByteArrayInputStream(inputStreamToByteArray(is)): InputStream).orDie}

  val in_4query_out_4header_extended_endpoint: ZEndpoint =
    in_4query_out_4header_extended.in("echo" / "query").zServerLogic(ZIO.succeed)

  val allEndpoints: Set[ZEndpoint] = Set(
    health_endpoint,
    in_path_path_out_string_endpoint,
    in_string_out_string_endpoint,
    in_json_out_json_endpoint,
    in_headers_out_headers_endpoint,
    in_input_stream_out_input_stream_endpoint,
    in_4query_out_4header_extended_endpoint
  )

