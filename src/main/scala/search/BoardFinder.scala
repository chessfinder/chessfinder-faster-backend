package chessfinder
package search

import core.SearchFen
import search.details.{ GameFetcher, SearchResultRepo }

import zio.{ Clock, ZIO, ZLayer }

trait BoardFinder:

  def find(board: SearchFen, userId: UserId, searchRequestId: SearchRequestId): Computation[Unit]

object BoardFinder:

  class Impl(
      validator: BoardValidator,
      searchResultRepo: SearchResultRepo,
      gameFetcher: GameFetcher,
      searcher: SearchFacadeAdapter,
      clock: Clock
  ) extends BoardFinder:

    def find(board: SearchFen, userId: UserId, searchRequestId: SearchRequestId): Computation[Unit] =
      val eff = for
        validatedBoard <- validator.validate(board)
        searchResult   <- searchResultRepo.get(searchRequestId)
        games          <- gameFetcher.list(userId)
        matchingResult <- ZIO.collect(games) { game =>
          searcher
            .find(game.pgn, validatedBoard)
            .map(if _ then Some(MatchedGame(game.resource)) else None)
            .either
        }
        matchedGames = matchingResult.collect { case Right(Some(game)) => game }
        now <- clock.instant
        updatedResult = searchResult.update(now, games.length, matchedGames).doFinalize
        _ <- searchResultRepo.update(updatedResult)
      yield ()

      eff
        .tapError(err => ZIO.logError(s"Failure is registering for ${err}..."))
        .ignore

  object Impl:
    def layer = ZLayer {
      for
        validator   <- ZIO.service[BoardValidator]
        searchRepo  <- ZIO.service[SearchResultRepo]
        gameFetcher <- ZIO.service[GameFetcher]
        searcher    <- ZIO.service[SearchFacadeAdapter]
        clock       <- ZIO.service[Clock]
      yield Impl(validator, searchRepo, gameFetcher, searcher, clock)
    }
