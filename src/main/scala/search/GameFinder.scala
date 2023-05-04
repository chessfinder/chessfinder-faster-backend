package chessfinder
package search

import search.entity.*
import core.SearchFen
import zio.ZIO
import zio.ZLayer
import chessfinder.core.ProbabilisticBoard
import api.ApiVersion
import izumi.reflect.Tag

trait GameFinder[Version <: ApiVersion]:

  def find(board: SearchFen, platform: ChessPlatform, userName: UserName): φ[SearchResult]

object GameFinder:

  def find[Version <: ApiVersion: Tag](
      board: SearchFen,
      platform: ChessPlatform,
      userName: UserName
  ): ψ[GameFinder[Version], SearchResult] =
    ψ.serviceWithZIO[GameFinder[Version]](_.find(board, platform, userName))

  class Impl[Version <: ApiVersion](
      validator: BoardValidator,
      fetcher: GameFetcher[Version],
      searcher: Searcher
  ) extends GameFinder[Version]:
    def find(board: SearchFen, platform: ChessPlatform, userName: UserName): φ[SearchResult] =
      for
        validBoard <- validator.validate(board)
        user = User(platform, userName)
        games        <- fetcher.fetch(user = user)
        searchResult <- findAll(games, validBoard)
      yield searchResult

    private def findAll(games: FetchingResult, board: ProbabilisticBoard): φ[SearchResult] =
      val matchingResult = ZIO
        .collect(games.games) { game =>
          searcher
            .find(game.pgn, board)
            .map(if _ then Some(MatchedGame(game.resource)) else None)
            .either
        }
        .memoize

      for
        memoized           <- matchingResult
        matchedGames       <- memoized.map(_.collect { case Right(Some(game)) => game })
        allGamesWereParsed <- memoized.map(_.forall(_.isRight))
        dowloadStatus =
          if games.unreachableArchives.isEmpty && allGamesWereParsed
          then DownloadStatus.Full
          else DownloadStatus.Partial
      yield SearchResult(
        matched = matchedGames,
        status = dowloadStatus
      )

  object Impl:
    def layer[Version <: ApiVersion: Tag] = ZLayer {
      for
        validator  <- ZIO.service[BoardValidator]
        downloader <- ZIO.service[GameFetcher[Version]]
        searcher   <- ZIO.service[Searcher]
      yield Impl(validator, downloader, searcher)
    }
