package chessfinder

import org.graalvm.nativeimage.IsolateThread
import org.graalvm.nativeimage.c.function.CEntryPoint
import org.graalvm.nativeimage.c.`type`.CCharPointer
import org.graalvm.nativeimage.c.`type`.CTypeConversion
import chessfinder.core.{ Finder, PgnReader, SearchFen }
import chess.format.pgn.PgnStr
import cats.implicits.catsSyntaxTuple2Semigroupal

class ChessfinderFacade
object ChessfinderFacade:

  @CEntryPoint(name = "validate")
  @annotation.static
  def validate(
      thread: IsolateThread,
      searchFenCString: CCharPointer
  ): Boolean =
    val searchFen          = SearchFen(CTypeConversion.toJavaString(searchFenCString))
    val probabilisticBoard = SearchFen.read(searchFen)
    probabilisticBoard.isValid

  @CEntryPoint(name = "find")
  @annotation.static
  def find(
      thread: IsolateThread,
      searchFenCString: CCharPointer,
      gamePgnCString: CCharPointer
  ): Boolean =
    val searchFen          = SearchFen(CTypeConversion.toJavaString(searchFenCString))
    val gamePgn            = PgnStr(CTypeConversion.toJavaString(gamePgnCString))
    val probabilisticBoard = SearchFen.read(searchFen)
    val game               = PgnReader.read(gamePgn)
    (probabilisticBoard, game)
      .mapN { (probabilisticBoard, game) =>
        Finder.find(game, probabilisticBoard)
      }
      .getOrElse(false)
