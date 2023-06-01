package chessfinder
package api

import search.entity.ChessPlatform
import sttp.tapir.Schema

import io.circe.generic.semiauto.deriveCodec
import io.circe.{ Codec, Decoder, Encoder }
import zio.json.{ DeriveJsonDecoder, JsonDecoder }

import scala.util.Try

final case class SearchRequest(
    user: String,
    platform: Platform,
    board: String
)

object SearchRequest:
  given Codec[SearchRequest]  = deriveCodec[SearchRequest]
  given Schema[SearchRequest] = Schema.derived[SearchRequest]

  given JsonDecoder[SearchRequest] = DeriveJsonDecoder.gen[SearchRequest]
