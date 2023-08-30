package chessfinder
package download.details

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema
import zio.json.*

import java.util.UUID

case class DownloadResponse(
    taskId: UUID
)

object DownloadResponse:

  given Codec[DownloadResponse]  = deriveCodec[DownloadResponse]
  given Schema[DownloadResponse] = Schema.derived[DownloadResponse]

  given JsonEncoder[DownloadResponse] = DeriveJsonEncoder.gen[DownloadResponse]
