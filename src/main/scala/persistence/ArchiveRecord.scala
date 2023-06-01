package chessfinder
package persistence

import api.TaskStatusResponse
import persistence.core.{ DynamoTable, DynamoTypeMappers }
import search.entity.*
import sttp.model.Uri

import zio.schema.{ DeriveSchema, Schema }

import java.time.Instant
import java.util.UUID

case class ArchiveRecord(
    user_id: UserId,
    archive_id: ArchiveId,
    resource: Uri,
    last_game_played: Option[GameId],
    downloaded: Int,
    status: ArchiveStatus,
    till: Instant
):

  def toArchiveResult: ArchiveResult =
    ArchiveResult(
      userId = user_id,
      archiveId = archive_id,
      resource = resource,
      lastGamePlayed = last_game_played,
      downloaded = downloaded,
      status = status,
      till = till
    )

object ArchiveRecord:

  import DynamoTypeMappers.given

  given Schema[ArchiveRecord] = DeriveSchema.gen[ArchiveRecord]

  object Table
      extends DynamoTable.SortedSeq.Impl[UserId, ArchiveId, ArchiveRecord](
        name = "archives",
        partitionKeyName = "user_id",
        sortKeyName = "archive_id"
      )

  def fromArchiveResult(result: ArchiveResult): ArchiveRecord =
    ArchiveRecord(
      user_id = result.userId,
      archive_id = result.archiveId,
      resource = result.resource,
      last_game_played = result.lastGamePlayed,
      downloaded = result.downloaded,
      status = result.status,
      till = result.till
    )
