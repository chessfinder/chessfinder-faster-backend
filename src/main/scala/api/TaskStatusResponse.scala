package chessfinder
package api

import io.circe.generic.semiauto.deriveCodec
import io.circe.Codec
import sttp.tapir.Schema
import sttp.model.Uri
import util.UriCodec.given
import search.entity.{ DownloadStatus, SearchResult }
import zio.json.*
import java.util.UUID
import persistence.TaskRecord

final case class TaskStatusResponse(
    taskId: UUID,
    failed: Int,
    succeed: Int,
    done: Int,
    pending: Int,
    total: Int
)

object TaskStatusResponse:

  def apply(
      task: TaskRecord
  ): TaskStatusResponse =
    TaskStatusResponse(
      taskId = task.task_id.value,
      failed = task.failed,
      succeed = task.succeed,
      done = task.done,
      pending = task.pending,
      total = task.total
    )

  given Codec[TaskStatusResponse]  = deriveCodec[TaskStatusResponse]
  given Schema[TaskStatusResponse] = Schema.derived[TaskStatusResponse]

  given JsonEncoder[TaskStatusResponse] = DeriveJsonEncoder.gen[TaskStatusResponse]
