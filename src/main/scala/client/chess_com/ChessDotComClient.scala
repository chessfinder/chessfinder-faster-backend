package chessfinder
package client.chess_com

import aspect.Span
import client.ClientError.*
import client.chess_com.dto.*
import client.{ κ, μ, ClientError, ClientExt }
import search.entity.UserName
import sttp.model.Uri
import util.UriCodec.given

import io.circe.generic.semiauto.*
import io.circe.{ Decoder, Encoder }
import zio.{ Cause, Config, ZIO, ZLayer }
import zio.config.magnolia.deriveConfig
import zio.http.{ Client, Request, Status, URL }

trait ChessDotComClient:

  def profile(userName: UserName): μ[Profile]

  def archives(userName: UserName): μ[Archives]

  def games(archiveLocation: Uri): μ[Games]

object ChessDotComClient:

  def profile(userName: UserName): κ[ChessDotComClient, Profile] =
    κ.serviceWithZIO[ChessDotComClient](_.profile(userName)) @@ Span.log

  def archives(userName: UserName): κ[ChessDotComClient, Archives] =
    κ.serviceWithZIO[ChessDotComClient](_.archives(userName)) @@ Span.log

  def games(archiveLocation: Uri): κ[ChessDotComClient, Games] =
    κ.serviceWithZIO[ChessDotComClient](_.games(archiveLocation)) @@ Span.log

  class Impl(config: Impl.Configuration, client: Client) extends ChessDotComClient:

    import ClientExt.*

    override def profile(userName: UserName): μ[Profile] =
      val urlString = s"${config.baseUrl}/pub/player/${userName.value}"
      val url       = μ.fromEither(URL.decode(urlString).left.map(_ => SomethingWentWrong))
      val effect =
        for
          url <- url
          request = Request.get(url)
          _        <- ZIO.logInfo(s"Request $urlString")
          response <- client.request(request)
          profile <- response.status match
            case Status.Ok       => response.body.to[Profile].map(Right.apply)
            case Status.NotFound => μ.succeed(Left(ProfileNotFound(userName)))
            case _               => μ.succeed(Left(SomethingWentWrong))
        yield profile
      effect.foldZIO(_ => μ.fail(SomethingWentWrong), μ.fromEither)

    override def archives(userName: UserName): μ[Archives] =
      val urlString = s"${config.baseUrl}/pub/player/${userName.value}/games/archives"
      val url       = μ.fromEither(URL.decode(urlString).left.map(_ => SomethingWentWrong))
      val effect =
        for
          url <- url
          request = Request.get(url)
          _        <- ZIO.logInfo(s"Request $urlString")
          response <- client.request(request)
          profile <- response.status match
            case Status.Ok       => response.body.to[Archives].map(Right.apply)
            case Status.NotFound => μ.succeed(Left(ProfileNotFound(userName)))
            case _               => μ.succeed(Left(SomethingWentWrong))
        yield profile
      effect.foldZIO(_ => μ.fail(SomethingWentWrong), μ.fromEither)

    override def games(archiveLocation: Uri): μ[Games] =
      val maybeUrl =
        val pathSegments = archiveLocation.path
        for
          indexToDrop <- Option(pathSegments.indexWhere(_ == "pub"))
            .filter(_ >= 0)
            .toRight(SomethingWentWrong)
          remainigPath = pathSegments.drop(indexToDrop).mkString("/")
          urlString    = s"${config.baseUrl}/${remainigPath}"
          url <- URL.decode(urlString)
        yield url
      val url = μ.fromEither(maybeUrl.left.map(_ => SomethingWentWrong))
      val effect =
        for
          url <- url
          request = Request.get(url)
          _ <- ZIO.logInfo(s"Request ${url.toString()}")
          response <- client
            .request(request)
            .tapError(err =>
              ZIO.logErrorCause(
                s"Fetching the archive ${archiveLocation} has resulted an error ${err.getMessage()}",
                Cause.fail(err)
              )
            )
          profile <- response.status match
            case Status.Ok =>
              response.body
                .to[Games]
                .map(Right.apply)
                .tapError(err =>
                  ZIO.logErrorCause(
                    s"Parsing the archive ${archiveLocation} has resulted an error ${err.getMessage()}",
                    Cause.fail(err)
                  )
                )
            case status =>
              val logging = for
                bodyAsString <- response.body.asString.orElseSucceed("Request body is not a string")
                _            <- ZIO.logError(s"Response ${status.toString}: $bodyAsString")
              yield ()
              logging.ignore *> μ.succeed(Left(SomethingWentWrong))
        yield profile

      effect.foldZIO(
        err =>
          ZIO.logError(s"Fetching the archive ${archiveLocation} has resulted an error ${err.toString}") *> μ
            .fail(SomethingWentWrong),
        μ.fromEither
      )

  object Impl:
    val layer = ZLayer.apply {
      for
        clientConfig <- ZIO.config[Configuration](Configuration.config)
        client       <- ZIO.service[Client]
      yield Impl(clientConfig, client)
    }

    case class Configuration(baseUrl: Uri)
    object Configuration:
      given config: Config[Configuration] = deriveConfig[Configuration].nested("client", "chesscom")
