package chessfinder
package search.repo

import api.{ TaskResponse, TaskStatusResponse }
import aspect.Span
import persistence.{ ArchiveRecord, PlatformType, UserRecord }
import search.*
import search.entity.*
import sttp.model.Uri

import zio.dynamodb.{ DynamoDBError, DynamoDBExecutor }
import zio.{ Cause, ZIO, ZLayer }

trait ArchiveRepo:

  def get(
      userId: UserId,
      archiveId: ArchiveId
  ): φ[ArchiveResult]

  def initiate(
      userId: UserId,
      resources: Seq[Uri]
  ): φ[Unit]

  def getAll(
      userId: UserId
  ): φ[Seq[ArchiveResult]]

  def update(archive: ArchiveResult): φ[Unit]

object ArchiveRepo:

  class Impl(executor: DynamoDBExecutor) extends ArchiveRepo:
    private val layer = ZLayer.succeed(executor)

    override def get(userId: UserId, archiveId: ArchiveId): φ[ArchiveResult] =
      ArchiveRecord.Table
        .get[ArchiveRecord](userId, archiveId)
        .provideLayer(layer)
        .tapError(e => ZIO.logErrorCause(e.getMessage(), Cause.fail(e)))
        .flatMap(ZIO.fromEither)
        .map(_.toArchiveResult)
        .tapSomeError { case e: DynamoDBError.DecodingError =>
          ZIO.logErrorCause(e.getMessage(), Cause.fail(e))
        }
        .catchNonFatalOrDie {
          case e: DynamoDBError.ValueNotFound => ZIO.fail(BrokenLogic.ArchiveNotFound(archiveId))
          case _                              => ZIO.fail(BrokenLogic.ServiceOverloaded)
        }

    override def getAll(userId: UserId): φ[Seq[ArchiveResult]] =
      ArchiveRecord.Table
        .list[ArchiveRecord](userId)
        .provideLayer(layer)
        .catchNonFatalOrDie(e => ZIO.logError(e.getMessage()) *> ZIO.fail(BrokenLogic.ServiceOverloaded))
        .map(_.map(_.toArchiveResult))

    override def initiate(
        userId: UserId,
        resources: Seq[Uri]
    ): φ[Unit] =
      val archivesOrFailure = resources.map(resource => ArchiveResult(userId, resource))
      val archives = archivesOrFailure.collect { case Right(archive) =>
        ArchiveRecord.fromArchiveResult(archive)
      }
      val failures = archivesOrFailure.collect { case Left(message) => message }

      val logFailedArchives = ZIO.foreach(failures)(brokenLogic => ZIO.logError(brokenLogic.msg))

      val saveSuccessfulArchives =
        ArchiveRecord.Table
          .putMany(archives*)
          .provideLayer(layer)
          .tapError(e => ZIO.logErrorCause(e.getMessage(), Cause.fail(e)))
          .mapError(_ => BrokenLogic.ServiceOverloaded)

      logFailedArchives *> saveSuccessfulArchives

    override def update(archive: ArchiveResult): φ[Unit] =
      ArchiveRecord.Table
        .put(ArchiveRecord.fromArchiveResult(archive))
        .provideLayer(layer)
        .tapError(e => ZIO.logErrorCause(e.getMessage(), Cause.fail(e)))
        .mapError(_ => BrokenLogic.ServiceOverloaded)

  object Impl:
    val layer = ZLayer {
      for executor <- ZIO.service[DynamoDBExecutor]
      yield Impl(executor)
    }
