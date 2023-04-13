package chessfinder
package core

import cats.implicits.*
import cats.kernel.Monoid
import ornicar.scalalib.zeros.given_Zero_Option
import β.Ext.*
import chess.Pos
import chess.format.pgn.PgnStr

object SearchFacade:

  def find(pgn: PgnStr, probabilisticBoard: ProbabilisticBoard): β[Boolean] =
    PgnReader.read(pgn).map(replay => Finder.find(replay, probabilisticBoard))
