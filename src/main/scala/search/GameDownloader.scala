package chessfinder
package search

import search.entity.*

trait GameDownloader:

  def download(userId: UserId): φ[Seq[HistoricalGame]]
