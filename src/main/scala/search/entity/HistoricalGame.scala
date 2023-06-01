package chessfinder
package search.entity

import core.β
import sttp.model.Uri

import chess.format.pgn.{ PgnStr, Reader }
import chess.format.pgn.Reader.Result

case class HistoricalGame(resource: Uri, pgn: PgnStr):
  val id: GameId = GameId(resource.toString)

object HistoricalGame:
  case class WithTimePlayed(game: HistoricalGame, endTimeEpoch: Long)
  object WithTimePlayed:
    def apply(resource: Uri, pgn: PgnStr, endTimeEpoch: Long): WithTimePlayed =
      HistoricalGame.WithTimePlayed(
        HistoricalGame(resource, pgn),
        endTimeEpoch
      )
