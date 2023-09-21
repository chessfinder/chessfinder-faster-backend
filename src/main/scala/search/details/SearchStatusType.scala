package chessfinder
package search.details

import search.SearchStatus

import io.circe.{ Codec, Decoder, Encoder }
import sttp.tapir.Schema

import scala.util.Try

enum SearchStatusType(val repr: String):
  case InProgress        extends SearchStatusType("IN_PROGRESS")
  case SearchedAll       extends SearchStatusType("SEARCHED_ALL")
  case SearchedPartially extends SearchStatusType("SEARCHED_PARTIALLY")

object SearchStatusType:

  def fromSearchStatus(status: SearchStatus): SearchStatusType =
    status match
      case SearchStatus.InProgress        => SearchStatusType.InProgress
      case SearchStatus.SearchedAll       => SearchStatusType.SearchedAll
      case SearchStatus.SearchedPartially => SearchStatusType.SearchedPartially

  def fromRepr(repr: String): Either[String, SearchStatusType] =
    repr match
      case "IN_PROGRESS"        => Right(SearchStatusType.InProgress)
      case "SEARCHED_ALL"       => Right(SearchStatusType.SearchedAll)
      case "SEARCHED_PARTIALLY" => Right(SearchStatusType.SearchedPartially)
      case str                  => Left(s"SearchStatus does not have value for $str")

  private val encoder            = Encoder[String].contramap[SearchStatusType](_.repr)
  private val decoder            = Decoder[String].emap[SearchStatusType](fromRepr)
  given Codec[SearchStatusType]  = Codec.from[SearchStatusType](decoder, encoder)
  given Schema[SearchStatusType] = Schema.derivedEnumeration.defaultStringBased
