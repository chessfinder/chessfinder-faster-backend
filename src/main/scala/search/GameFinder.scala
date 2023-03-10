package chessfinder
package search

import search.entity.*

trait GameFinder:

  def find(board: RawBoard, platform: ChessPlatform, userName: UserName): φ[SearchResult]

object GameFinder:
  class Impl() extends GameFinder:
    def find(board: RawBoard, platform: ChessPlatform, userName: UserName): φ[SearchResult] = ???
