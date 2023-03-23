package chessfinder
package search

import search.entity.*
import core.SearchFen
import zio.ZIO
import zio.ZLayer
import chessfinder.core.ProbabilisticBoard

trait GameFinder:

  def find(board: SearchFen, platform: ChessPlatform, userName: UserName): φ[SearchResult]

object GameFinder:

  def find(board: SearchFen, platform: ChessPlatform, userName: UserName): ψ[GameFinder, SearchResult] =
    ψ.serviceWithZIO[GameFinder](_.find(board, platform, userName))

  class Impl(
      validator: BoardValidator,
      downloader: GameDownloader,
      searcher: Searcher
  ) extends GameFinder:
    def find(board: SearchFen, platform: ChessPlatform, userName: UserName): φ[SearchResult] =
      for
        validBoard <- validator.validate(board)
        user = User(platform, userName)
        games <- downloader.download(user = user)
        searchResult <- findAll(games, validBoard)
      yield searchResult

    private def findAll(games: DownloadingResult, board: ProbabilisticBoard): φ[SearchResult] =
      val matchingResult = ZIO.collect(games.games) { game =>
        searcher
          .find(game.png, board)
          .map(if _ then Some(MatchedGame(game.resource)) else None)
          .either
      }.memoize

      for 
        memoized <- matchingResult
        matchedGames <- memoized.map(_.collect{case Right(Some(game)) => game})
        allGamesWereParsed <- memoized.map(_.forall(_.isRight))
        dowloadStatus = 
          if games.unreachableArchives.isEmpty && allGamesWereParsed
          then DownloadStatus.Full
          else DownloadStatus.Partial
      yield 
        SearchResult(
        matched = matchedGames,
        status = dowloadStatus
      )

  object Impl:
    val layer = ZLayer {
      for
        validator  <- ZIO.service[BoardValidator]
        downloader <- ZIO.service[GameDownloader]
        searcher   <- ZIO.service[Searcher]
      yield Impl(validator, downloader, searcher)
    }
