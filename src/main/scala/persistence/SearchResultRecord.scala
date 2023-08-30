package chessfinder
package persistence

import persistence.core.{ DynamoTable, DynamoTypeMappers }
import chessfinder.SearchRequestId
import chessfinder.download.details.DownloadStatusResponse
import chessfinder.search.{ MatchedGame, SearchResult, SearchStatus }

import sttp.model.Uri
import zio.schema.{ DeriveSchema, Schema }

import java.time.Instant
import java.util.UUID

case class SearchResultRecord(
    search_request_id: SearchRequestId,
    start_search_at: Instant,
    last_examined_at: Instant,
    examined: Int,
    total: Int,
    matched: Set[Uri],
    status: SearchStatus
):

  def toSearchResult: SearchResult =
    SearchResult(
      id = search_request_id,
      startSearchAt = start_search_at,
      lastExaminedAt = last_examined_at,
      examined = examined,
      total = total,
      matched = matched.map(MatchedGame.apply).toSeq,
      status = status
    )

object SearchResultRecord:

  import DynamoTypeMappers.given

  given Schema[SearchResultRecord] = DeriveSchema.gen[SearchResultRecord]

  object Table
      extends DynamoTable.Unique.Impl[SearchRequestId, SearchResultRecord](
        name = "searches",
        partitionKeyName = "search_request_id"
      )

  def fromSearchResult(result: SearchResult): SearchResultRecord =
    SearchResultRecord(
      search_request_id = result.id,
      start_search_at = result.startSearchAt,
      last_examined_at = result.lastExaminedAt,
      examined = result.examined,
      total = result.total,
      matched = result.matched.map(_.resource).toSet,
      status = result.status
    )
