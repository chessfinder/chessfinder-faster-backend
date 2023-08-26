package chessfinder
package api

import search.entity.{ ChessPlatform, SearchStatus }

import io.circe.generic.semiauto.deriveCodec
import io.circe.{ Codec, Decoder, Encoder }
import sttp.tapir.Schema
import zio.json.{ DeriveJsonDecoder, JsonDecoder }

import scala.util.Try

enum SearchStatusType:
  case InProgress, SearchedAll, SearchedPartially

object SearchStatusType:

  def fromSearchStatus(status: SearchStatus): SearchStatusType =
    status match
      case SearchStatus.InProgress        => SearchStatusType.InProgress
      case SearchStatus.SearchedAll       => SearchStatusType.SearchedAll
      case SearchStatus.SearchedPartially => SearchStatusType.SearchedPartially

  private val encoder = Encoder[String].contramap[SearchStatusType](_.toString())
  private val decoder = Decoder[String].emapTry[SearchStatusType](str => Try(SearchStatusType.valueOf(str)))
  given Codec[SearchStatusType]  = Codec.from[SearchStatusType](decoder, encoder)
  given Schema[SearchStatusType] = Schema.derivedEnumeration.defaultStringBased
