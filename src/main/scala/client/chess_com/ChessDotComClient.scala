package chessfinder
package client.chess_com

import com.typesafe.config.*
import chessfinder.search.entity.UserName
import sttp.model.Uri
import zio.ZIO
import zio.ZLayer
import client.ClientExt
import client.chess_com.dto.*
import zio.http.Client
import zio.http.Request
import zio.http.URL
import client.{μ, κ}
import client.ClientError.*
import io.circe.Decoder
import sttp.model.Uri
import io.circe.{ Decoder, Encoder }
import io.circe.generic.semiauto.*
import util.UriCodec.given
import io.circe.config.syntax.*
import io.circe.config.*
import zio.IO

trait ChessDotComClient:

  def profile(userName: UserName): μ[Profile]

  def archives(userName: UserName): μ[Archives]

  def games(archiveLocation: Uri): μ[Games]

object ChessDotComClient:

  def profile(userName: UserName): κ[ChessDotComClient, Profile] = κ.serviceWithZIO[ChessDotComClient](_.profile(userName))

  def srchives(userName: UserName): κ[ChessDotComClient, Archives] = κ.serviceWithZIO[ChessDotComClient](_.archives(userName))

  def games(archiveLocation: Uri): κ[ChessDotComClient, Games] = κ.serviceWithZIO[ChessDotComClient](_.games(archiveLocation))


  class Impl(config: Impl.Configuration, client: Client) extends ChessDotComClient:

    import ClientExt.*

    override def profile(userName: UserName): μ[Profile] = 
      val urlString = s"${config.baseUrl}/pub/player/${userName.value}"
      val url = μ.fromEither(URL.fromString(urlString).left.map(_ => SomethingWentWrong))
      val effect = 
      for
        url <- url
        request = Request.get(url)
        response <- client.request(request)
        profile <- response.body.to[Profile]
      yield profile
      effect.mapError(_ => SomethingWentWrong)
      
    override def archives(userName: UserName): μ[Archives] = ???
    override def games(archiveLocation: Uri): μ[Games]     = ???
  
  object Impl:
    val layer = ZLayer.apply{
      for 
        conf <- ZIO.service[Config]
        clientConfig <- Configuration.fromConfig(conf)
        client <- ZIO.service[Client]
      
      yield Impl(clientConfig, client)
    } 

    case class Configuration(baseUrl: Uri)
    object Configuration: 
      given Decoder[Configuration] = deriveDecoder[Configuration]

      def fromConfig(rootConfig: Config): IO[io.circe.Error, Configuration] =
        ZIO.fromEither(rootConfig.as[Configuration]("client.chesscom"))