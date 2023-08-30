package chessfinder

import chess.format.pgn.Reader.Result
import chess.format.pgn.{ PgnStr, Reader }
import sttp.model.Uri

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
