package chessfinder
package api

import io.circe.{ Codec, Decoder, Encoder }
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema
import search.entity.ChessPlatform
import scala.util.Try
import zio.json.{ DeriveJsonDecoder, JsonDecoder }

final case class DownloadRequest(
    user: String,
    platform: Platform
)

object DownloadRequest:
  given Codec[DownloadRequest]  = deriveCodec[DownloadRequest]
  given Schema[DownloadRequest] = Schema.derived[DownloadRequest]

  given JsonDecoder[DownloadRequest] = DeriveJsonDecoder.gen[DownloadRequest]
