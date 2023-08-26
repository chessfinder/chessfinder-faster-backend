package chessfinder
package api

import search.entity.ChessPlatform

import io.circe.generic.semiauto.deriveCodec
import io.circe.{ Codec, Decoder, Encoder }
import sttp.tapir.Schema
import zio.json.{ DeriveJsonDecoder, JsonDecoder }

import scala.util.Try

final case class DownloadRequest(
    user: String,
    platform: Platform
)

object DownloadRequest:
  given Codec[DownloadRequest]  = deriveCodec[DownloadRequest]
  given Schema[DownloadRequest] = Schema.derived[DownloadRequest]

  given JsonDecoder[DownloadRequest] = DeriveJsonDecoder.gen[DownloadRequest]
