package chessfinder
package search

import chessfinder.search.details.SearchResultRepo
import zio.{ ZIO, ZLayer }

trait SearchResultChecker:

  def check(searchId: SearchRequestId): Computation[SearchResult]

object SearchResultChecker:

  class Impl(
      searchResultRepo: SearchResultRepo
  ) extends SearchResultChecker:

    def check(searchId: SearchRequestId): Computation[SearchResult] =
      searchResultRepo.get(searchId).mapError {
        case err: BrokenComputation.SearchResultNotFound => err
        case _                                           => BrokenComputation.ServiceOverloaded
      }

  object Impl:
    def layer = ZLayer {
      for searchResultRepo <- ZIO.service[SearchResultRepo]
      yield Impl(searchResultRepo)
    }
