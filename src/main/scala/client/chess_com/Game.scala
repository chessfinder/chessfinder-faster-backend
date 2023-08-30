package chessfinder
package client.chess_com

import util.UriCodec.given

import io.circe.generic.semiauto.*
import io.circe.{ Decoder, Encoder }
import sttp.model.Uri

final case class Game(
    url: Uri,
    pgn: String,
    end_time: Long
)

object Game:
  given Decoder[Game] = deriveDecoder[Game]
