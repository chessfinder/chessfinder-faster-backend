package chessfinder
package client.chess_com.dto

import zio.json.*
import sttp.model.Uri
import io.circe.{ Decoder, Encoder }
import io.circe.generic.semiauto.*

final case class Profile(url: Uri)

object Profile:

  given Decoder[Uri] = Decoder[String].emap(Uri.parse)

  given Decoder[Profile] = deriveDecoder[Profile]
