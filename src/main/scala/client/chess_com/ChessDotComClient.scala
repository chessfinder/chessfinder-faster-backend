package chessfinder
package client.chess_com

import client.ClientError.*
import client.{ Call, ClientError, ClientExt }
import util.UriCodec.given
import chessfinder.UserName

import sttp.model.Uri
import zio.config.magnolia.deriveConfig
import zio.http.{ Client, Request, Status, URL }
import zio.{ Cause, Config, ZIO, ZLayer }

trait ChessDotComClient:

  def profile(userName: UserName): Call[Profile]

  def archives(userName: UserName): Call[Archives]

  def games(archiveLocation: Uri): Call[Games]

object ChessDotComClient:

  class Impl(config: Impl.Configuration, client: Client) extends ChessDotComClient:

    import ClientExt.*

    override def profile(userName: UserName): Call[Profile] =
      val urlString = s"${config.baseUrl}/pub/player/${userName.value}"
      val url       = ZIO.fromEither(URL.decode(urlString).left.map(_ => SomethingWentWrong))
      val effect =
        for
          url <- url
          request = Request.get(url)
          _        <- ZIO.logInfo(s"Request $urlString")
          response <- client.request(request)
          profile <- response.status match
            case Status.Ok       => response.body.to[Profile].map(Right.apply)
            case Status.NotFound => ZIO.succeed(Left(ProfileNotFound(userName)))
            case _               => ZIO.succeed(Left(SomethingWentWrong))
        yield profile
      effect.foldZIO(err => ZIO.log(err.toString()) *> ZIO.fail(SomethingWentWrong), ZIO.fromEither)

    override def archives(userName: UserName): Call[Archives] =
      val urlString = s"${config.baseUrl}/pub/player/${userName.value}/games/archives"
      val url       = ZIO.fromEither(URL.decode(urlString).left.map(_ => SomethingWentWrong))
      val effect =
        for
          url <- url
          request = Request.get(url)
          _        <- ZIO.logInfo(s"Request $urlString")
          response <- client.request(request)
          profile <- response.status match
            case Status.Ok       => response.body.to[Archives].map(Right.apply)
            case Status.NotFound => ZIO.succeed(Left(ProfileNotFound(userName)))
            case _               => ZIO.succeed(Left(SomethingWentWrong))
        yield profile
      effect.foldZIO(_ => ZIO.fail(SomethingWentWrong), ZIO.fromEither)

    override def games(archiveLocation: Uri): Call[Games] =
      val maybeUrl =
        val pathSegments = archiveLocation.path
        for
          indexToDrop <- Option(pathSegments.indexWhere(_ == "pub"))
            .filter(_ >= 0)
            .toRight(SomethingWentWrong)
          remainingPath = pathSegments.drop(indexToDrop).mkString("/")
          urlString     = s"${config.baseUrl}/${remainingPath}"
          url <- URL.decode(urlString)
        yield url
      val url = ZIO.fromEither(maybeUrl.left.map(_ => SomethingWentWrong))
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
              val preParsing = ZIO.logInfo("Got 200. Preparing to parse the body...")
              val parsing = response.body
                .to[Games]
                .tapError(err =>
                  ZIO.logErrorCause(
                    s"Parsing the archive ${archiveLocation} has resulted an error ${err.getMessage()}",
                    Cause.fail(err)
                  )
                )
              def parsedSuccessfully(games: Games) = ZIO.logInfo(s"Parsed in total ${games.games.length} games")
              for {
                _ <- preParsing
                _ <- ZIO.logInfo(s"${response.body.getClass.toString}")
                games <- parsing
                _ <- parsedSuccessfully(games)
              } yield Right(games)

            case status =>
              val logging = for
                bodyAsString <- response.body.asString.orElseSucceed("Request body is not a string")
                _            <- ZIO.logError(s"Response ${status.toString}: $bodyAsString")
              yield ()
              logging.ignore *> ZIO.succeed(Left(SomethingWentWrong))
        yield profile

      effect.foldZIO(
        err =>
          ZIO.logError(s"Fetching the archive ${archiveLocation} has resulted an error ${err.toString}") *>
            ZIO.fail(SomethingWentWrong),
        ZIO.fromEither
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
