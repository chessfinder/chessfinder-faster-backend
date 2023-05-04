package chessfinder
package api

import io.circe.{ Codec, Decoder, Encoder }
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema
import search.entity.ChessPlatform
import scala.util.Try
import zio.json.{ DeriveJsonDecoder, JsonDecoder }

enum Platform:
  case `chess.com`

  def toPlatform = this match
    case `chess.com` => ChessPlatform.ChessDotCom

object Platform:

  private val encoder    = Encoder[String].contramap[Platform](_.toString())
  private val decoder    = Decoder[String].emapTry[Platform](str => Try(Platform.valueOf(str)))
  given Codec[Platform]  = Codec.from[Platform](decoder, encoder)
  given Schema[Platform] = Schema.derivedEnumeration.defaultStringBased
  given JsonDecoder[Platform] =
    JsonDecoder[String].mapOrFail(s => Try(Platform.valueOf(s)).toEither.left.map(_.getMessage()))
