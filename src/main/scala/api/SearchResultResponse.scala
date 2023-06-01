package chessfinder
package api

import search.entity.{ DownloadStatus, SearchResult, SearchStatus }
import sttp.model.Uri
import sttp.tapir.Schema

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import zio.json.*

import java.time.Instant
import java.util.UUID

final case class SearchResultResponse(
    id: UUID,
    startSearchAt: Instant,
    lastExaminedAt: Instant,
    examined: Int,
    total: Int,
    matched: Seq[Uri],
    status: SearchStatusType
)

object SearchResultResponse:
  import util.UriCodec.given

  given Codec[SearchResultResponse]  = deriveCodec[SearchResultResponse]
  given Schema[SearchResultResponse] = Schema.derived[SearchResultResponse]

  def fromSearchResult(result: SearchResult): SearchResultResponse =
    SearchResultResponse(
      id = result.id.value,
      startSearchAt = result.startSearchAt,
      lastExaminedAt = result.lastExaminedAt,
      examined = result.examined,
      total = result.total,
      matched = result.matched.map(_.resource),
      status = SearchStatusType.fromSearchStatus(result.status)
    )
