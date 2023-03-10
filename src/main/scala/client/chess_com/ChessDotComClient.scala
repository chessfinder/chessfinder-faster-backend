package chessfinder
package client.chess_com

import chessfinder.search.entity.UserName
import sttp.model.Uri
import zio.ZIO
import zio.ZLayer
import client.ClientExt
import client.chess_com.dto.*
import zio.http.Client
import zio.http.Request
import zio.http.URL
import client.μ
import client.ClientError.*

trait ChessDotComClient:

  def profile(userName: UserName): μ[Profile]

  def archives(userName: UserName): μ[Archives]

  def games(archiveLocation: Uri): μ[Games]

object ChessDotComClient:
  class Impl(client: Client) extends ChessDotComClient:

    import ClientExt.*
    // private val profileClient = client.mapZIO()
    override def profile(userName: UserName): μ[Profile] = 
      val urlString = s"https://api.chess.com/pub/player/${userName.value}"
      val url = μ.fromEither(URL.fromString(urlString).left.map(_ => SomethingWentWrong))
      val effect = for {
        url <- url
        request = Request.get(url)
        response <- client.request(request)
        profile <- response.body.to[Profile]
      } yield profile
      effect.mapError(_ => SomethingWentWrong)
      
    override def archives(userName: UserName): μ[Archives] = ???
    override def games(archiveLocation: Uri): μ[Games]     = ???
  
  val impl: ZLayer[Client, Nothing, ChessDotComClient] = ZLayer {
    for {
      client <- ZIO.service[Client]
    } yield Impl(client)
  }