package chessfinder
package search.repo

import chessfinder.search.entity.User
import chessfinder.persistence.UserRecord
import search.entity.*
import zio.{ ZIO, ZLayer }
import zio.dynamodb.DynamoDBExecutor
import persistence.PlatformType
import search.*
import chessfinder.persistence.TaskRecord
import chessfinder.api.TaskStatusResponse
import chessfinder.api.TaskResponse
import zio.dynamodb.DynamoDBError
import zio.Cause
import aspect.Span

trait TaskRepo:
  def get(taskId: TaskId): φ[TaskStatusResponse]

  def successIncrement(taskId: TaskId): φ[Unit]

  def failureIncrement(taskId: TaskId): φ[Unit]

  def initiate(taskId: TaskId, total: Int): φ[Unit]

object TaskRepo:

  def get(taskId: TaskId): ψ[TaskRepo, TaskStatusResponse] =
    ψ.serviceWithZIO[TaskRepo](_.get(taskId))

  def successIncrement(taskId: TaskId): ψ[TaskRepo, Unit] =
    ψ.serviceWithZIO[TaskRepo](_.successIncrement(taskId))

  def failureIncrement(taskId: TaskId): ψ[TaskRepo, Unit] =
    ψ.serviceWithZIO[TaskRepo](_.failureIncrement(taskId))

  def initiate(taskId: TaskId, total: Int): ψ[TaskRepo, Unit] =
    ψ.serviceWithZIO[TaskRepo](_.initiate(taskId, total))

  class Impl(executor: DynamoDBExecutor) extends TaskRepo:
    private val layer = ZLayer.succeed(executor)

    override def get(taskId: TaskId): φ[TaskStatusResponse] =
      getTaskRecord(taskId).map(_.toStatus)

    override def successIncrement(taskId: TaskId): φ[Unit] =
      val eff =
        for
          task <- getTaskRecord(taskId)
          _ <-
            if task.succeed < task.total
            then putTaskRecord(task.incrementSuccess)
            else ZIO.fail(BrokenLogic.TaskProgressOverflown(taskId))
        yield ()
      eff @@ Span.log

    override def failureIncrement(taskId: TaskId): φ[Unit] =
      val eff =
        for
          task <- getTaskRecord(taskId)
          _ <-
            if task.succeed < task.total
            then putTaskRecord(task.incrementFailure)
            else ZIO.fail(BrokenLogic.TaskProgressOverflown(taskId))
        yield ()
      eff @@ Span.log

    override def initiate(taskId: TaskId, total: Int): φ[Unit] =
      putTaskRecord(TaskRecord(taskId, total)) @@ Span.log

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
