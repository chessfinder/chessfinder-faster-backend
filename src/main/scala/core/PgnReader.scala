package chessfinder
package core

import chess.format.pgn.*
import chess.Replay
import Reader.Result
import β.Ext.*

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
