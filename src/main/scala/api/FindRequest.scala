package chessfinder
package api

import io.circe.{ Encoder, Decoder, Codec}
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema
import search.entity.ChessPlatform
import scala.util.Try

final case class FindRequest(
  user: String,
  platform: Platform,
  board: String
)

object FindRequest:
  given Codec[FindRequest] = deriveCodec[FindRequest]
  given Schema[FindRequest] = Schema.derived[FindRequest]

enum Platform:
  case `chess.com`

  def toPlatform = this match
    case `chess.com` => ChessPlatform.ChessDotCom
    
object Platform:

  private val encoder = Encoder[String].contramap[Platform](_.toString())
  private val decoder = Decoder[String].emapTry[Platform](str => Try(Platform.valueOf(str)))
  given Codec[Platform] = Codec.from[Platform](decoder, encoder)
  given Schema[Platform] = Schema.derivedEnumeration.defaultStringBased
