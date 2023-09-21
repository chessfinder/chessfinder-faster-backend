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
