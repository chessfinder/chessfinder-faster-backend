package chessfinder
package persistence

import chessfinder.ChessPlatform
import chessfinder.search.SearchStatus

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

object SearchStatusType:

  def fromSearchStatus(s: SearchStatus) = s match
    case SearchStatus.InProgress        => SearchStatusType.IN_PROGRESS
    case SearchStatus.SearchedAll       => SearchStatusType.SEARCHED_ALL
    case SearchStatus.SearchedPartially => SearchStatusType.SEARCHED_PARTIALLY

  def fromString(str: String): Either[String, SearchStatusType] =
    Try(SearchStatusType.valueOf(str)).toEither.left.map(_.getMessage())
