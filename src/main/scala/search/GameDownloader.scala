package chessfinder
package search

import search.entity.*

trait GameDownloader:

  def download(userId: User): φ[DownloadingResult]

object GameDownloader:

  class Impl() extends GameDownloader:
    def download(userId: User): φ[DownloadingResult] = ???
