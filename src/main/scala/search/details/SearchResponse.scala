package chessfinder
package search.details

import search.SearchResult

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema
import zio.json.*

import java.util.UUID

final case class SearchResponse(
    searchResultId: UUID
)

object SearchResponse:

  given Codec[SearchResponse]  = deriveCodec[SearchResponse]
  given Schema[SearchResponse] = Schema.derived[SearchResponse]

  given JsonEncoder[SearchResponse] = DeriveJsonEncoder.gen[SearchResponse]

  def fromSearchResult(result: SearchResult): SearchResponse = SearchResponse(result.id.value)
