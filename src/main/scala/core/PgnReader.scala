package chessfinder
package core

import core.Walidated.Ext.*

import chess.Replay
import chess.format.pgn.*
import chess.format.pgn.Reader.Result

object PgnReader:
  def read(pgnStr: PgnStr): Walidated[Replay] =
    Walidated
      .fromValidated {
        Reader.full(pgnStr).leftMap(_.value)
      }
      .andThen {
        case Result.Complete(replay)       => replay.validated
        case Result.Incomplete(_, failure) => (failure.value: String).failed[Replay]
      }
