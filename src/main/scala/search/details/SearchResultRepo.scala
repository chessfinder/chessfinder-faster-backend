package chessfinder
package search.details

import aspect.Span
import download.details.{ DownloadResponse, DownloadStatusResponse }
import persistence.{ PlatformType, SearchResultRecord, UserRecord }
import search.*

import zio.dynamodb.{ DynamoDBError, DynamoDBExecutor }
import zio.{ Cause, ZIO, ZLayer }

import java.time.Instant

trait SearchResultRepo:

  def initiate(
      id: SearchRequestId,
      startSearchAt: Instant,
      total: Int
  ): Computation[SearchResult]

  def get(searchRequestId: SearchRequestId): Computation[SearchResult]

  def update(searchResult: SearchResult): Computation[Unit]

object SearchResultRepo:

  class Impl(executor: DynamoDBExecutor) extends SearchResultRepo:
    private val layer = ZLayer.succeed(executor)

    override def get(searchRequestId: SearchRequestId): Computation[SearchResult] =
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
          case e: DynamoDBError.ValueNotFound =>
            ZIO.fail(BrokenComputation.SearchResultNotFound(searchRequestId))
          case _ => ZIO.fail(BrokenComputation.ServiceOverloaded)
        }

    override def update(searchResult: SearchResult): Computation[Unit] =
      SearchResultRecord.Table
        .put(SearchResultRecord.fromSearchResult(searchResult))
        .provideLayer(layer)
        .tapError(e => ZIO.logErrorCause(e.getMessage(), Cause.fail(e)))
        .mapError(_ => BrokenComputation.ServiceOverloaded)

    override def initiate(
        id: SearchRequestId,
        startSearchAt: Instant,
        total: Int
    ): Computation[SearchResult] =
      val searchResult = SearchResult(id, startSearchAt, total)
      SearchResultRecord.Table
        .put(SearchResultRecord.fromSearchResult(searchResult))
        .provideLayer(layer)
        .tapError(e => ZIO.logErrorCause(e.getMessage(), Cause.fail(e)))
        .mapError(_ => BrokenComputation.ServiceOverloaded)
        .map(_ => searchResult)

  object Impl:
    val layer = ZLayer {
      for executor <- ZIO.service[DynamoDBExecutor]
      yield Impl(executor)
    }
