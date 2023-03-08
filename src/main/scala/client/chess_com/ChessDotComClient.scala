package chessfinder
package client.chess_com

import chessfinder.search.entity.UserName
import chessfinder.search.entity.UserId
import sttp.model.Uri
import zio.ZIO
import zio.ZLayer
import client.ClientExt
import client.chess_com.dto.*
import zio.http.Client
import zio.http.Request
import zio.http.URL
import chessfinder.client.chess_com.dto.errors.ServiceIsOverloaded

trait ChessDotComClient:

  def profile(userName: UserName): φ[Profile]

  def archives(userName: UserName): φ[Archives]

  def games(archiveLocation: Uri): φ[Games]

object ChessDotComClient:
  class Impl(client: Client) extends ChessDotComClient:

    import ClientExt.*
    // private val profileClient = client.mapZIO()
    override def profile(userName: UserName): φ[Profile] = 
      val urlString = s"https://api.chess.com/pub/player/${userName.value}"
      val url = φ.fromEither(URL.fromString(urlString).left.map(_ => ServiceIsOverloaded))
      val effect = for {
        url <- url
        request = Request.get(url)
        response <- client.request(request)
        profile <- response.body.to[Profile]
      } yield profile
      effect.mapError(_ => ServiceIsOverloaded)
      
    override def archives(userName: UserName): φ[Archives] = ???
    override def games(archiveLocation: Uri): φ[Games]     = ???
  
  val impl: ZLayer[Client, Nothing, ChessDotComClient] = ZLayer {
    for {
      client <- ZIO.service[Client]
    } yield Impl(client)
  }