package chessfinder
package search

import aspect.Span
import core.{ ProbabilisticBoard, SearchFen }
import search.BrokenLogic.NoGameAvailable
import search.entity.*
import search.queue.BoardSearchingProducer
import search.repo.{ ArchiveRepo, SearchResultRepo, UserRepo }

import izumi.reflect.Tag
import zio.{ Clock, Random, ZIO, ZLayer }

trait SearchRequestAcceptor:

  def register(board: SearchFen, platform: ChessPlatform, userName: UserName): φ[SearchResult]

  def check(searchId: SearchRequestId): φ[SearchResult]

object SearchRequestAcceptor:

  class Impl(
      validator: BoardValidator,
      boardSearchingProducer: BoardSearchingProducer,
      userRepo: UserRepo,
      archiveRepo: ArchiveRepo,
      searchResultRepo: SearchResultRepo,
      clock: Clock,
      random: Random
  ) extends SearchRequestAcceptor:

    def register(board: SearchFen, platform: ChessPlatform, userName: UserName): φ[SearchResult] =
      for
        _ <- validator.validate(board)
        user = User(platform, userName)
        userIdentified <- userRepo.get(user)
        archives       <- archiveRepo.getAll(userIdentified.userId)
        totalGames = archives.map(_.downloaded).sum
        _               <- ZIO.cond(totalGames > 0, (), NoGameAvailable(user))
        now             <- clock.instant
        searchRequestId <- random.nextUUID
        searchResult    <- searchResultRepo.initiate(SearchRequestId(searchRequestId), now, totalGames)
        _               <- boardSearchingProducer.publish(userIdentified, board, searchResult.id)
      yield searchResult

    def check(searchId: SearchRequestId): φ[SearchResult] =
      searchResultRepo.get(searchId).mapError {
        case err: BrokenLogic.SearchResultNotFound => err
        case _                                     => BrokenLogic.ServiceOverloaded
      }

  object Impl:
    def layer = ZLayer {
      for
        validator              <- ZIO.service[BoardValidator]
        boardSearchingProducer <- ZIO.service[BoardSearchingProducer]
        userRepo               <- ZIO.service[UserRepo]
        archiveRepo            <- ZIO.service[ArchiveRepo]
        searchResultRepo       <- ZIO.service[SearchResultRepo]
        clock                  <- ZIO.service[Clock]
        random                 <- ZIO.service[Random]
      yield Impl(validator, boardSearchingProducer, userRepo, archiveRepo, searchResultRepo, clock, random)
    }
