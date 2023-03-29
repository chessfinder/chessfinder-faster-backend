package chessfinder
package api

import io.circe.generic.semiauto.deriveCodec
import io.circe.Codec
import sttp.tapir.Schema
import sttp.model.Uri
import util.UriCodec.given
import search.entity.{ DownloadStatus, SearchResult }
import zio.json.*

final case class FindResponse(
    resources: Seq[Uri],
    message: String
)

object FindResponse:
  import util.UriCodec.given

  given Codec[FindResponse]  = deriveCodec[FindResponse]
  given Schema[FindResponse] = Schema.derived[FindResponse]

  given JsonEncoder[Uri] = JsonEncoder[String].contramap(_.toString)
  given JsonEncoder[FindResponse] = DeriveJsonEncoder.gen[FindResponse]

  def fromSearchResult(result: SearchResult): FindResponse =
    val message = result.status match
      case DownloadStatus.Full    => "All games are successfully downloaded and analized."
      case DownloadStatus.Partial => "Not all games are downloaded and analized."
    FindResponse(resources = result.matched.map(_.resource), message = message)
