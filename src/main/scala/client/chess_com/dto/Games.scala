package chessfinder
package client.chess_com.dto

import io.circe.{ Decoder, Encoder }
import io.circe.generic.semiauto.*

final case class Games(
    games: Seq[Game]
)

object Games:
  given Decoder[Games] = deriveDecoder[Games]
