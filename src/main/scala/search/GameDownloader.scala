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

trait GameDownloader:

  def download(user: User): φ[DownloadingResult]

object GameDownloader:

  def download(user: User): ψ[GameDownloader, DownloadingResult] =
    ZIO.serviceWithZIO[GameDownloader](_.download(user))

  class Impl(client: ChessDotComClient) extends GameDownloader:
    def download(user: User): φ[DownloadingResult] =
      val archives = client
        .archives(user.userName)
        .mapError {
          case ClientError.ProfileNotFound(userName) => BrokenLogic.ProfileNotFound(userName)
          case _                                     => BrokenLogic.ServiceOverloaded
        }
      val games = for
        arch       <- archives
        downloaded <- downloadArchive(arch)
      yield downloaded

      games.filterOrFail(_.games.nonEmpty)(BrokenLogic.NoGameAvaliable(user.userName))

    private def downloadArchive(archives: Archives): φ[DownloadingResult] =
      @tailrec def rec(result: φ[DownloadingResult], archives: List[Uri]): φ[DownloadingResult] =
        archives match
          case resource :: tail =>
            val newRes = for
              games <- client.games(resource).either
              res   <- result
              newRes = games match
                case Right(games) =>
                  val historicalGames =
                    games.games.toList.map(game => HistoricalGame(game.url, PgnStr(game.pgn)))
                  DownloadingResult(historicalGames ++ res.games, res.unreachableArchives)
                case Left(_) => DownloadingResult(res.games, resource :: res.unreachableArchives)
            yield newRes
            rec(newRes, tail)
          case _ => result
      rec(φ.succeed(DownloadingResult.empty), archives.archives.toList.reverse)

  object Impl:
    val layer = ZLayer {
      for client <- ZIO.service[ChessDotComClient]
      yield Impl(client)
    }
