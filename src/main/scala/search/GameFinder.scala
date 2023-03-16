package chessfinder
package search

import search.entity.*
import core.format.SearchFen
import zio.ZIO
import zio.ZLayer

trait GameFinder:

  def find(board: SearchFen, platform: ChessPlatform, userName: UserName): φ[SearchResult]

object GameFinder:

  def find(board: SearchFen, platform: ChessPlatform, userName: UserName): ψ[GameFinder, SearchResult] = 
    ψ.serviceWithZIO[GameFinder](_.find(board, platform, userName))

  class Impl() extends GameFinder:
    def find(board: SearchFen, platform: ChessPlatform, userName: UserName): φ[SearchResult] = ???

  object Impl: 
    val layer = ZLayer.succeed(GameFinder.Impl())

