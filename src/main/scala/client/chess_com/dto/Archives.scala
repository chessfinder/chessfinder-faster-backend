package chessfinder
package client.chess_com.dto

import sttp.model.Uri
import util.UriCodec.given

import io.circe.generic.semiauto.*
import io.circe.{ Decoder, Encoder }

final case class Archives(archives: Seq[Uri])

object Archives:
  given Decoder[Archives] = deriveDecoder[Archives]
