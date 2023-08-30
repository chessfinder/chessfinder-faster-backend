package chessfinder
package download.details

import persistence.TaskRecord

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.Schema
import zio.json.*

import java.util.UUID

final case class DownloadStatusResponse(
    taskId: UUID,
    failed: Int,
    succeed: Int,
    done: Int,
    pending: Int,
    total: Int
)

object DownloadStatusResponse:

  def apply(
      task: TaskRecord
  ): DownloadStatusResponse =
    DownloadStatusResponse(
      taskId = task.task_id.value,
      failed = task.failed,
      succeed = task.succeed,
      done = task.done,
      pending = task.pending,
      total = task.total
    )

  given Codec[DownloadStatusResponse]  = deriveCodec[DownloadStatusResponse]
  given Schema[DownloadStatusResponse] = Schema.derived[DownloadStatusResponse]

  given JsonEncoder[DownloadStatusResponse] = DeriveJsonEncoder.gen[DownloadStatusResponse]
