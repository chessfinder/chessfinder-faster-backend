package chessfinder
package api

import search.entity.{ DownloadStatus, SearchResult, SearchStatus }
import sttp.model.Uri
import sttp.tapir.Schema

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import zio.json.*

import java.util.UUID

final case class SearchResponse(
    searchResultId: UUID
)

object SearchResponse:
  import util.UriCodec.given

  given Codec[SearchResponse]  = deriveCodec[SearchResponse]
  given Schema[SearchResponse] = Schema.derived[SearchResponse]

  given JsonEncoder[SearchResponse] = DeriveJsonEncoder.gen[SearchResponse]

  def fromSearchResult(result: SearchResult): SearchResponse = SearchResponse(result.id.value)
