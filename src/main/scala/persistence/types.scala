package chessfinder
package persistence

import search.entity.{ ChessPlatform, SearchStatus }

import java.util.UUID
import scala.util.Try

enum PlatformType:
  case CHESS_DOT_COM
  case LICHESS

  def toPlatform = this match
    case CHESS_DOT_COM => ChessPlatform.ChessDotCom
    case LICHESS       => ChessPlatform.Lichess

object PlatformType:
  def fromPlatform(p: ChessPlatform) = p match
    case ChessPlatform.ChessDotCom => CHESS_DOT_COM
    case ChessPlatform.Lichess     => LICHESS

  def fromString(str: String): Either[String, PlatformType] =
    Try(PlatformType.valueOf(str)).toEither.left.map(_.getMessage())

enum SearchStatusType:
  case IN_PROGRESS, SEARCHED_ALL, SEARCHED_PARTIALLY

  def toSearchStatus: SearchStatus = this match
    case IN_PROGRESS        => SearchStatus.InProgress
    case SEARCHED_ALL       => SearchStatus.SearchedAll
    case SEARCHED_PARTIALLY => SearchStatus.SearchedPartially

object SearchStatusType:

  def fromSearchStatus(s: SearchStatus) = s match
    case SearchStatus.InProgress        => SearchStatusType.IN_PROGRESS
    case SearchStatus.SearchedAll       => SearchStatusType.SEARCHED_ALL
    case SearchStatus.SearchedPartially => SearchStatusType.SEARCHED_PARTIALLY

  def fromString(str: String): Either[String, SearchStatusType] =
    Try(SearchStatusType.valueOf(str)).toEither.left.map(_.getMessage())
