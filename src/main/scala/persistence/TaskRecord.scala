package chessfinder
package persistence

import persistence.core.DynamoTable
import persistence.core.DynamoTypeMappers
import zio.schema.{ DeriveSchema, Schema }
import search.entity.TaskId
import chessfinder.api.TaskStatusResponse

case class TaskRecord(
    task_id: TaskId,
    succeed: Int,
    failed: Int,
    done: Int,
    pending: Int,
    total: Int
):

  def incrementSuccess: TaskRecord =
    this.copy(
      succeed = succeed + 1,
      done = done + 1,
      pending = pending - 1
    )

  def incrementFailure: TaskRecord =
    this.copy(
      failed = failed + 1,
      done = done + 1,
      pending = pending - 1
    )

  def toStatus: TaskStatusResponse =
    TaskStatusResponse(
      taskId = task_id.value,
      succeed = succeed,
      failed = failed,
      done = done,
      pending = pending,
      total = total
    )

object TaskRecord:

  def apply(task_id: TaskId, total: Int): TaskRecord =
    TaskRecord(task_id, 0, 0, total)

  def apply(
      task_id: TaskId,
      failed: Int,
      succeed: Int,
      pending: Int
  ): TaskRecord =
    val done  = failed + succeed
    val total = done + pending
    TaskRecord(
      task_id = task_id,
      failed = failed,
      succeed = succeed,
      done = done,
      pending = pending,
      total = total
    )

  import DynamoTypeMappers.given

  given Schema[TaskRecord] = DeriveSchema.gen[TaskRecord]

  object Table
      extends DynamoTable.Unique.Impl[TaskId, TaskRecord](name = "tasks", partitionKeyName = "task_id")
