package chessfinder
package client.chess_com

import util.UriCodec.given

import io.circe.generic.semiauto.*
import io.circe.{ Decoder, Encoder }
import sttp.model.Uri

final case class Archives(archives: Seq[Uri])

object Archives:
  given Decoder[Archives] = deriveDecoder[Archives]
