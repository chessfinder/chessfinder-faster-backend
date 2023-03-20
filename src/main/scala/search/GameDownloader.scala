package chessfinder
package search

import search.entity.*
import zio.ZLayer
import zio.ZIO

trait GameDownloader:

  def download(user: User): φ[DownloadingResult]

object GameDownloader:

  def download(userId: User): ψ[GameDownloader, DownloadingResult] =
    ZIO.serviceWithZIO[GameDownloader](_.download(userId)) 

  class Impl() extends GameDownloader:
    def download(userId: User): φ[DownloadingResult] = ???

  object Impl:
    val layer = ZLayer.succeed(Impl())    
