package chessfinder
package api

import io.circe.generic.semiauto.deriveCodec
import io.circe.Codec
import sttp.tapir.Schema
import sttp.model.Uri
import util.UriCodec.given
import search.entity.{ DownloadStatus, SearchResult }
import zio.json.*

final case class DownloadAndFindResponse(
    resources: Seq[Uri],
    message: String
)

object DownloadAndFindResponse:
  import util.UriCodec.given

  given Codec[DownloadAndFindResponse]  = deriveCodec[DownloadAndFindResponse]
  given Schema[DownloadAndFindResponse] = Schema.derived[DownloadAndFindResponse]

  given JsonEncoder[Uri]                     = JsonEncoder[String].contramap(_.toString)
  given JsonEncoder[DownloadAndFindResponse] = DeriveJsonEncoder.gen[DownloadAndFindResponse]

  def fromSearchResult(result: SearchResult): DownloadAndFindResponse =
    val message = result.status match
      case DownloadStatus.Full    => "All games are successfully downloaded and analized."
      case DownloadStatus.Partial => "Not all games are downloaded and analized."
    DownloadAndFindResponse(resources = result.matched.map(_.resource), message = message)
