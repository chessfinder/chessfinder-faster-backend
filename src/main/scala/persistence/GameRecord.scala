package chessfinder
package persistence

import persistence.core.DynamoTable
import zio.schema.{ DeriveSchema, Schema }
import java.time.Instant
import persistence.core.DynamoTypeMappers
import chessfinder.search.entity.*
import chessfinder.search.GameFinder
import chessfinder.search.entity.HistoricalGame
import sttp.model.Uri
import chess.format.pgn.PgnStr
import zio.schema.annotation.recordName

case class GameRecord(
    user_id: UserId,
    game_id: GameId,
    resource: Uri,
    pgn: PgnStr
):

  def toGame: HistoricalGame = HistoricalGame(resource, pgn)

object GameRecord:

  import DynamoTypeMappers.given

  given Schema[GameRecord] = DeriveSchema.gen[GameRecord]

  object Table
      extends DynamoTable.SortedSeq.Impl[UserId, GameId, GameRecord](
        name = "games",
        partitionKeyName = "user_id",
        sortKeyName = "game_id"
      )

  def fromGame(userId: UserId, game: HistoricalGame): GameRecord =
    GameRecord(
      userId,
      GameId(game.resource.toString),
      game.resource,
      game.pgn
    )
