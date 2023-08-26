package chessfinder
package client.chess_com.dto

import util.UriCodec.given

import io.circe.generic.semiauto.*
import io.circe.{ Decoder, Encoder }
import sttp.model.Uri

final case class Profile(`@id`: Uri)

object Profile:

  given Decoder[Profile] = deriveDecoder[Profile]
