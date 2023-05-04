package chessfinder
package api

import io.circe.{ Codec, Decoder, Encoder }
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema
import search.entity.ChessPlatform
import scala.util.Try
import zio.json.{ DeriveJsonDecoder, JsonDecoder }

final case class FindRequest(
    user: String,
    platform: Platform,
    board: String
)

object FindRequest:
  given Codec[FindRequest]  = deriveCodec[FindRequest]
  given Schema[FindRequest] = Schema.derived[FindRequest]

  given JsonDecoder[FindRequest] = DeriveJsonDecoder.gen[FindRequest]
