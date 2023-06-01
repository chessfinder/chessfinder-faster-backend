package chessfinder
package core

import core.β.Ext.*

import chess.Replay
import chess.format.pgn.*
import chess.format.pgn.Reader.Result

object PgnReader:
  def read(pgnStr: PgnStr): β[Replay] =
    β
      .fromValidated {
        Reader.full(pgnStr).leftMap(_.value)
      }
      .andThen {
        case Result.Complete(replay)            => replay.validated
        case Result.Incomplete(replay, failure) => (failure.value: String).failed[Replay]
      }
