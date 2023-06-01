package chessfinder
package search.repo

import api.{ TaskResponse, TaskStatusResponse }
import aspect.Span
import persistence.{ PlatformType, TaskRecord, UserRecord }
import search.*
import search.entity.*

import zio.dynamodb.{ DynamoDBError, DynamoDBExecutor }
import zio.{ Cause, ZIO, ZLayer }

trait TaskRepo:
  def get(taskId: TaskId): φ[TaskStatusResponse]

  def successIncrement(taskId: TaskId): φ[Unit]

  def failureIncrement(taskId: TaskId): φ[Unit]

  def initiate(taskId: TaskId, total: Int): φ[TaskStatusResponse]

object TaskRepo:

  class Impl(executor: DynamoDBExecutor) extends TaskRepo:
    private val layer = ZLayer.succeed(executor)

    override def get(taskId: TaskId): φ[TaskStatusResponse] =
      getTaskRecord(taskId).map(_.toStatus)

    override def successIncrement(taskId: TaskId): φ[Unit] =
      val eff =
        for
          task <- getTaskRecord(taskId)
          _ <-
            if task.done < task.total
            then putTaskRecord(task.incrementSuccess)
            else ZIO.fail(BrokenLogic.TaskProgressOverflown(taskId))
        yield ()
      eff @@ Span.log

    override def failureIncrement(taskId: TaskId): φ[Unit] =
      val eff =
        for
          task <- getTaskRecord(taskId)
          _ <-
            if task.done < task.total
            then putTaskRecord(task.incrementFailure)
            else ZIO.fail(BrokenLogic.TaskProgressOverflown(taskId))
        yield ()
      eff @@ Span.log

    override def initiate(taskId: TaskId, total: Int): φ[TaskStatusResponse] =
      val taskRecord = TaskRecord(taskId, total)
      putTaskRecord(taskRecord).map(_ => taskRecord.toStatus)

    private def getTaskRecord(taskId: TaskId): φ[TaskRecord] =
      val eff = TaskRecord.Table
        .get[TaskRecord](taskId)
        .provideLayer(layer)
        .tapError(e => ZIO.logErrorCause(e.getMessage(), Cause.fail(e)))
        .flatMap(ZIO.fromEither)
        .tapSomeError { case e: DynamoDBError.DecodingError =>
          ZIO.logErrorCause(e.getMessage(), Cause.fail(e))
        }
        .catchNonFatalOrDie {
          case e: DynamoDBError.ValueNotFound => ZIO.fail(BrokenLogic.TaskNotFound(taskId))
          case _                              => ZIO.fail(BrokenLogic.ServiceOverloaded)
        }
      eff @@ Span.log

    private def putTaskRecord(task: TaskRecord): φ[Unit] =
      val eff = TaskRecord.Table
        .put(task)
        .provideLayer(layer)
        .tapError(e => ZIO.logErrorCause(e.getMessage(), Cause.fail(e)))
        .mapError(_ => BrokenLogic.ServiceOverloaded)
      eff @@ Span.log

  object Impl:
    val layer = ZLayer {
      for executor <- ZIO.service[DynamoDBExecutor]
      yield Impl(executor)
    }
