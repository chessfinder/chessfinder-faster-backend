package chessfinder
package persistence

import persistence.core.{ DynamoTable, DynamoTypeMappers }
import search.entity.*
import sttp.model.Uri

import chess.format.pgn.PgnStr
import zio.schema.annotation.recordName
import zio.schema.{ DeriveSchema, Schema }

import java.time.Instant

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
