package chessfinder
package search

import aspect.Span
import core.{ ProbabilisticBoard, SearchFen }
import search.entity.*
import search.repo.{ GameRepo, SearchResultRepo }

import izumi.reflect.Tag
import zio.{ ZIO, ZLayer }
import zio.Clock

trait BoardFinder:

  def find(board: SearchFen, userId: UserId, searchRequestId: SearchRequestId): φ[Unit]

object BoardFinder:

  class Impl(
      validator: BoardValidator,
      searchResultRepo: SearchResultRepo,
      gameRepo: GameRepo,
      searcher: Searcher,
      clock: Clock
  ) extends BoardFinder:

    def find(board: SearchFen, userId: UserId, searchRequestId: SearchRequestId): φ[Unit] =
      val eff = for
        validatedBoard <- validator.validate(board)
        searchResult   <- searchResultRepo.get(searchRequestId)
        games          <- gameRepo.list(userId)
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
        validator  <- ZIO.service[BoardValidator]
        searchRepo <- ZIO.service[SearchResultRepo]
        gameRepo   <- ZIO.service[GameRepo]
        searcher   <- ZIO.service[Searcher]
        clock      <- ZIO.service[Clock]
      yield Impl(validator, searchRepo, gameRepo, searcher, clock)
    }
