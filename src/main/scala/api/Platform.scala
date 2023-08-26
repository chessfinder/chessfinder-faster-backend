package chessfinder
package api

import search.entity.ChessPlatform

import io.circe.generic.semiauto.deriveCodec
import io.circe.{ Codec, Decoder, Encoder }
import sttp.tapir.Schema
import zio.json.{ DeriveJsonDecoder, JsonDecoder }

import scala.util.Try

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
