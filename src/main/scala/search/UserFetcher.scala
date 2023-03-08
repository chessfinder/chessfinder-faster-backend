package chessfinder
package search

import search.entity.*

trait UserFetcher:

  def fetch(platform: ChessPlatform, userId: UserName): φ[UserId]
