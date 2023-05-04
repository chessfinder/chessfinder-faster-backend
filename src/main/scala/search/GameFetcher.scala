package chessfinder
package search

import search.entity.*
import zio.ZLayer
import zio.{ UIO, ZIO }
import chessfinder.client.chess_com.ChessDotComClient
import chessfinder.client.ClientError
import search.BrokenLogic
import sttp.model.Uri
import chessfinder.client.chess_com.dto.Games
import chessfinder.client.chess_com.dto.Archives
import annotation.tailrec
import chess.format.pgn.PgnStr
import chessfinder.persistence.GameRecord
import chessfinder.persistence.UserRecord
import chessfinder.persistence.PlatformType
import chessfinder.client.ClientError.ProfileNotFound
import zio.dynamodb.*
import search.repo.*
import api.ApiVersion
import izumi.reflect.Tag

trait GameFetcher[Version <: ApiVersion]:

  def fetch(user: User): φ[FetchingResult]

object GameFetcher:

  def fetch[Version <: ApiVersion: Tag](user: User): ψ[GameFetcher[Version], FetchingResult] =
    ZIO.serviceWithZIO[GameFetcher[Version]](_.fetch(user))

  @Deprecated
  class Impl(client: ChessDotComClient) extends GameFetcher[ApiVersion.Newborn.type]:
    def fetch(user: User): φ[FetchingResult] =
      val archives = client
        .archives(user.userName)
        .mapError {
          case ClientError.ProfileNotFound(userName) => BrokenLogic.ProfileNotFound(user)
          case _                                     => BrokenLogic.ServiceOverloaded
        }
      val games = for
        arch       <- archives
        downloaded <- downloadArchive(arch)
      yield downloaded

      games.filterOrFail(_.games.nonEmpty)(BrokenLogic.NoGameAvaliable(user))

    private def downloadArchive(archives: Archives): φ[FetchingResult] =
      @tailrec def rec(result: φ[FetchingResult], archives: List[Uri]): φ[FetchingResult] =
        archives match
          case resource :: tail =>
            val newRes = for
              games <- client.games(resource).either
              res   <- result
              newRes = games match
                case Right(games) =>
                  val historicalGames =
                    games.games.toList.map(game => HistoricalGame(game.url, PgnStr(game.pgn)))
                  FetchingResult(historicalGames ++ res.games, res.unreachableArchives)
                case Left(_) => FetchingResult(res.games, resource +: res.unreachableArchives)
            yield newRes
            rec(newRes, tail)
          case _ => result
      rec(φ.succeed(FetchingResult.empty), archives.archives.toList.reverse)

  @Deprecated
  object Impl:
    val layer = ZLayer {
      for client <- ZIO.service[ChessDotComClient]
      yield Impl(client)
    }

  class Local(userRepo: UserRepo, gameRepo: GameRepo) extends GameFetcher[ApiVersion.Async.type]:

    def fetch(user: User): φ[FetchingResult] =
      for
        userCached <- userRepo.get(user)
        games      <- gameRepo.list(userCached)
        result = FetchingResult(games, Seq.empty[Uri])
      yield result

  object Local:
    val layer = ZLayer {
      for
        userRepo <- ZIO.service[UserRepo]
        gameRepo <- ZIO.service[GameRepo]
      yield Local(userRepo, gameRepo)
    }
