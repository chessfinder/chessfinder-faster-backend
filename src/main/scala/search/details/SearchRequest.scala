package chessfinder
package search.details

import api.Platform

import io.circe.generic.semiauto.deriveCodec
import io.circe.{ Codec, Decoder, Encoder }
import sttp.tapir.Schema
import zio.json.{ DeriveJsonDecoder, JsonDecoder }

final case class SearchRequest(
    user: String,
    platform: Platform,
    board: String
)

object SearchRequest:
  given Codec[SearchRequest]  = deriveCodec[SearchRequest]
  given Schema[SearchRequest] = Schema.derived[SearchRequest]

  given JsonDecoder[SearchRequest] = DeriveJsonDecoder.gen[SearchRequest]
