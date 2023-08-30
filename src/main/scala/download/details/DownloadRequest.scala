package chessfinder
package download.details

import api.Platform

import io.circe.generic.semiauto.deriveCodec
import io.circe.{ Codec, Decoder, Encoder }
import sttp.tapir.Schema
import zio.json.{ DeriveJsonDecoder, JsonDecoder }

final case class DownloadRequest(
    user: String,
    platform: Platform
)

object DownloadRequest:
  given Codec[DownloadRequest]  = deriveCodec[DownloadRequest]
  given Schema[DownloadRequest] = Schema.derived[DownloadRequest]

  given JsonDecoder[DownloadRequest] = DeriveJsonDecoder.gen[DownloadRequest]
