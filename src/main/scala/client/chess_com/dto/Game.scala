package chessfinder
package client.chess_com.dto

import sttp.model.Uri
import io.circe.{ Decoder, Encoder }
import io.circe.generic.semiauto.*
import util.UriCodec.given

final case class Game(
    url: Uri,
    pgn: String
)

object Game:
  given Decoder[Game] = deriveDecoder[Game]
