package chessfinder
package download.details

import aspect.Span
import download.details.{ DownloadResponse, DownloadStatusResponse }
import persistence.{ PlatformType, TaskRecord, UserRecord }
import search.*

import zio.dynamodb.{ DynamoDBError, DynamoDBExecutor }
import zio.{ Cause, ZIO, ZLayer }

trait TaskRepo:
  def get(taskId: TaskId): Computation[DownloadStatusResponse]

  def successIncrement(taskId: TaskId): Computation[Unit]

  def failureIncrement(taskId: TaskId): Computation[Unit]

  def initiate(taskId: TaskId, total: Int): Computation[DownloadStatusResponse]

object TaskRepo:

  class Impl(executor: DynamoDBExecutor) extends TaskRepo:
    private val layer = ZLayer.succeed(executor)

    override def get(taskId: TaskId): Computation[DownloadStatusResponse] =
      getTaskRecord(taskId).map(_.toStatus)

    override def successIncrement(taskId: TaskId): Computation[Unit] =
      val eff =
        for
          task <- getTaskRecord(taskId)
          _ <-
            if task.done < task.total
            then putTaskRecord(task.incrementSuccess)
            else ZIO.fail(BrokenComputation.TaskProgressOverflown(taskId))
        yield ()
      eff @@ Span.log

    override def failureIncrement(taskId: TaskId): Computation[Unit] =
      val eff =
        for
          task <- getTaskRecord(taskId)
          _ <-
            if task.done < task.total
            then putTaskRecord(task.incrementFailure)
            else ZIO.fail(BrokenComputation.TaskProgressOverflown(taskId))
        yield ()
      eff @@ Span.log

    override def initiate(taskId: TaskId, total: Int): Computation[DownloadStatusResponse] =
      val taskRecord = TaskRecord(taskId, total)
      putTaskRecord(taskRecord).map(_ => taskRecord.toStatus)

    private def getTaskRecord(taskId: TaskId): Computation[TaskRecord] =
      val eff = TaskRecord.Table
        .get[TaskRecord](taskId)
        .provideLayer(layer)
        .tapError(e => ZIO.logErrorCause(e.getMessage(), Cause.fail(e)))
        .flatMap(ZIO.fromEither)
        .tapSomeError { case e: DynamoDBError.DecodingError =>
          ZIO.logErrorCause(e.getMessage(), Cause.fail(e))
        }
        .catchNonFatalOrDie {
          case e: DynamoDBError.ValueNotFound => ZIO.fail(BrokenComputation.TaskNotFound(taskId))
          case _                              => ZIO.fail(BrokenComputation.ServiceOverloaded)
        }
      eff @@ Span.log

    private def putTaskRecord(task: TaskRecord): Computation[Unit] =
      val eff = TaskRecord.Table
        .put(task)
        .provideLayer(layer)
        .tapError(e => ZIO.logErrorCause(e.getMessage(), Cause.fail(e)))
        .mapError(_ => BrokenComputation.ServiceOverloaded)
      eff @@ Span.log

  object Impl:
    val layer = ZLayer {
      for executor <- ZIO.service[DynamoDBExecutor]
      yield Impl(executor)
    }
