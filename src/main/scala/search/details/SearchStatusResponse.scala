package chessfinder
package search.details

import search.SearchResult

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.model.Uri
import sttp.tapir.Schema

import java.time.Instant
import java.util.UUID

final case class SearchStatusResponse(
    id: UUID,
    startSearchAt: Instant,
    lastExaminedAt: Instant,
    examined: Int,
    total: Int,
    matched: Seq[Uri],
    status: SearchStatusType
)

object SearchStatusResponse:
  import util.UriCodec.given

  given Codec[SearchStatusResponse]  = deriveCodec[SearchStatusResponse]
  given Schema[SearchStatusResponse] = Schema.derived[SearchStatusResponse]

  def fromSearchResult(result: SearchResult): SearchStatusResponse =
    SearchStatusResponse(
      id = result.searchRequestId.value,
      startSearchAt = result.startSearchAt,
      lastExaminedAt = result.lastExaminedAt,
      examined = result.examined,
      total = result.total,
      matched = result.matched.map(_.resource),
      status = SearchStatusType.fromSearchStatus(result.status)
    )
