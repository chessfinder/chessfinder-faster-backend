package chessfinder
package search.repo

import api.{ TaskResponse, TaskStatusResponse }
import aspect.Span
import persistence.{ PlatformType, SearchResultRecord, UserRecord }
import search.*
import search.entity.*

import zio.dynamodb.{ DynamoDBError, DynamoDBExecutor }
import zio.{ Cause, ZIO, ZLayer }

import java.time.Instant

trait SearchResultRepo:

  def initiate(
      id: SearchRequestId,
      startSearchAt: Instant,
      total: Int
  ): φ[SearchResult]

  def get(searchRequestId: SearchRequestId): φ[SearchResult]

  def update(searchResult: SearchResult): φ[Unit]

object SearchResultRepo:

  class Impl(executor: DynamoDBExecutor) extends SearchResultRepo:
    private val layer = ZLayer.succeed(executor)

    override def get(searchRequestId: SearchRequestId): φ[SearchResult] =
      SearchResultRecord.Table
        .get[SearchResultRecord](searchRequestId)
        .provideLayer(layer)
        .tapError(e => ZIO.logErrorCause(e.getMessage(), Cause.fail(e)))
        .flatMap(ZIO.fromEither)
        .map(_.toSearchResult)
        .tapSomeError { case e: DynamoDBError.DecodingError =>
          ZIO.logErrorCause(e.getMessage(), Cause.fail(e))
        }
        .catchNonFatalOrDie {
          case e: DynamoDBError.ValueNotFound => ZIO.fail(BrokenLogic.SearchResultNotFound(searchRequestId))
          case _                              => ZIO.fail(BrokenLogic.ServiceOverloaded)
        }

    override def update(searchResult: SearchResult): φ[Unit] =
      SearchResultRecord.Table
        .put(SearchResultRecord.fromSearchResult(searchResult))
        .provideLayer(layer)
        .tapError(e => ZIO.logErrorCause(e.getMessage(), Cause.fail(e)))
        .mapError(_ => BrokenLogic.ServiceOverloaded)

    override def initiate(
        id: SearchRequestId,
        startSearchAt: Instant,
        total: Int
    ): φ[SearchResult] =
      val searchResult = SearchResult(id, startSearchAt, total)
      SearchResultRecord.Table
        .put(SearchResultRecord.fromSearchResult(searchResult))
        .provideLayer(layer)
        .tapError(e => ZIO.logErrorCause(e.getMessage(), Cause.fail(e)))
        .mapError(_ => BrokenLogic.ServiceOverloaded)
        .map(_ => searchResult)

  object Impl:
    val layer = ZLayer {
      for executor <- ZIO.service[DynamoDBExecutor]
      yield Impl(executor)
    }
