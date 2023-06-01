package chessfinder
package core

import core.β.Ext.*

import cats.implicits.*
import cats.kernel.Monoid
import chess.Pos
import chess.format.pgn.PgnStr
import ornicar.scalalib.zeros.given_Zero_Option

object SearchFacade:

  def find(pgn: PgnStr, probabilisticBoard: ProbabilisticBoard): β[Boolean] =
    PgnReader.read(pgn).map(replay => Finder.find(replay, probabilisticBoard))
