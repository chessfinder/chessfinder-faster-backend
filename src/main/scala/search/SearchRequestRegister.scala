package chessfinder
package search

import BrokenComputation.NoGameAvailable
import core.SearchFen
import download.details.ArchiveRepo
import search.details.{ SearchBoardCommandPublisher, SearchResultRepo, UserFetcher }

import zio.{ Clock, Random, ZIO, ZLayer }

trait SearchRequestRegister:

  def register(board: SearchFen, platform: ChessPlatform, userName: UserName): Computation[SearchResult]

object SearchRequestRegister:

  class Impl(
      validator: BoardValidator,
      boardSearchingProducer: SearchBoardCommandPublisher,
      userFetcher: UserFetcher,
      archiveRepo: ArchiveRepo,
      searchResultRepo: SearchResultRepo,
      clock: Clock,
      random: Random
  ) extends SearchRequestRegister:

    def register(board: SearchFen, platform: ChessPlatform, userName: UserName): Computation[SearchResult] =
      for
        _ <- validator.validate(board)
        user = User(platform, userName)
        userIdentified <- userFetcher.get(user)
        archives       <- archiveRepo.getAll(userIdentified.userId)
        totalGames = archives.map(_.downloaded).sum
        _               <- ZIO.cond(totalGames > 0, (), NoGameAvailable(user))
        now             <- clock.instant
        searchRequestId <- random.nextUUID
        searchResult    <- searchResultRepo.initiate(SearchRequestId(searchRequestId), now, totalGames)
        _               <- boardSearchingProducer.publish(userIdentified, board, searchResult.id)
      yield searchResult

  object Impl:
    def layer = ZLayer {
      for
        validator              <- ZIO.service[BoardValidator]
        boardSearchingProducer <- ZIO.service[SearchBoardCommandPublisher]
        userFetcher            <- ZIO.service[UserFetcher]
        archiveRepo            <- ZIO.service[ArchiveRepo]
        searchResultRepo       <- ZIO.service[SearchResultRepo]
        clock                  <- ZIO.service[Clock]
        random                 <- ZIO.service[Random]
      yield Impl(validator, boardSearchingProducer, userFetcher, archiveRepo, searchResultRepo, clock, random)
    }
