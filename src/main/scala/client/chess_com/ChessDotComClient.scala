package chessfinder
package client.chess_com

import chessfinder.search.entity.UserName
import chessfinder.search.entity.UserId
import sttp.model.Uri
import zio.ZIO

import client.chess_com.dto.*

trait ChessDotComClient:

  def profile(userName: UserName): φ[Profile]

  def archives(userName: UserName): φ[Archives]

  def games(archiveLocation: Uri): φ[Games]

object ChessDotComClient:
  class Impl extends ChessDotComClient:
    override def profile(userName: UserName): φ[Profile] =
      ZIO.succeed(Profile(sttp.model.Uri.parse("https://www.chess.com/member/tigran-c-137").toOption.get))
    override def archives(userName: UserName): φ[Archives] = ???
    override def games(archiveLocation: Uri): φ[Games]     = ???
