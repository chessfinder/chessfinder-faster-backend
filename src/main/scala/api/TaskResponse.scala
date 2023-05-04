package chessfinder
package api

import java.util.UUID
import io.circe.generic.semiauto.deriveCodec
import io.circe.Codec
import sttp.tapir.Schema
import sttp.model.Uri
import util.UriCodec.given
import search.entity.{ DownloadStatus, SearchResult }
import zio.json.*
import java.util.UUID

case class TaskResponse(
    taskId: UUID
)

object TaskResponse:

  given Codec[TaskResponse]  = deriveCodec[TaskResponse]
  given Schema[TaskResponse] = Schema.derived[TaskResponse]

  given JsonEncoder[TaskResponse] = DeriveJsonEncoder.gen[TaskResponse]
