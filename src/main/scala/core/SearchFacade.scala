package chessfinder
package core

import chess.format.pgn.PgnStr

object SearchFacade:

  def find(pgn: PgnStr, probabilisticBoard: ProbabilisticBoard): Walidated[Boolean] =
    PgnReader.read(pgn).map(replay => Finder.find(replay, probabilisticBoard))
