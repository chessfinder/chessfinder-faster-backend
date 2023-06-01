package chessfinder
package client.chess_com.dto

import io.circe.generic.semiauto.*
import io.circe.{ Decoder, Encoder }

final case class Games(
    games: Seq[Game]
)

object Games:
  given Decoder[Games] = deriveDecoder[Games]
