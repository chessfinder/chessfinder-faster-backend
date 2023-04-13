package chessfinder
package util

import sttp.model.Uri
import io.circe.{ Codec, Decoder, Encoder }
import sttp.tapir.Schema

object UriCodec:
  private val decoder: Decoder[Uri] = Decoder[String].emap(Uri.parse)
  private val encoder: Encoder[Uri] = Encoder[String].contramap(_.toString)
  given Codec[Uri]                  = Codec.from(decoder, encoder)
  given Schema[Uri]                 = Schema.schemaForString.map(str => Uri.parse(str).toOption)(_.toString)
