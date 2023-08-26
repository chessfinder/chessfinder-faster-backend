package chessfinder
package api

import search.entity.{ DownloadStatus, SearchResult }
import util.UriCodec.given

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.model.Uri
import sttp.tapir.Schema
import zio.json.*

import java.util.UUID

case class TaskResponse(
    taskId: UUID
)

object TaskResponse:

  given Codec[TaskResponse]  = deriveCodec[TaskResponse]
  given Schema[TaskResponse] = Schema.derived[TaskResponse]

  given JsonEncoder[TaskResponse] = DeriveJsonEncoder.gen[TaskResponse]
