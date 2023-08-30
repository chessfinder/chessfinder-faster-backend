package chessfinder
package client

import io.circe.{ parser, Decoder, Encoder }
import zio.http.Body
import zio.http.ZClient.ClientLive
import zio.{ Task, ZIO }

import java.nio.charset.StandardCharsets.UTF_8

object ClientExt:
  extension [T](dto: T)(using Encoder[T])
    def toBody: Body = Body.fromString(Encoder[T].apply(dto).noSpaces, UTF_8)

  extension (body: Body)
    def to[T](using Decoder[T]): Task[T] =
      for {
        str  <- body.asString(UTF_8)
//        _ <- ZIO.logInfo(str)
        json <- ZIO.fromEither(parser.parse(str))
//        _ <- ZIO.logInfo(json.noSpaces)
        dto  <- ZIO.fromEither(Decoder[T].decodeJson(json))
//        _ <- ZIO.logInfo(dto.toString)
      } yield dto
