package chessfinder
package search

import search.entity.*
import core.format.SearchFen

trait GameFinder:

  def find(board: SearchFen, platform: ChessPlatform, userName: UserName): φ[SearchResult]

object GameFinder:
  class Impl() extends GameFinder:
    def find(board: SearchFen, platform: ChessPlatform, userName: UserName): φ[SearchResult] = ???
