package chessfinder
package client

import io.circe.{ Decoder, Encoder }

import io.circe.parser
import zio.Task
import zio.http.ZClient.ClientLive
import zio.http.Body
import java.nio.charset.StandardCharsets.UTF_8
import zio.ZIO

object ClientExt:
  extension [T](dto: T)(using Encoder[T])
    def toBody: Body = Body.fromString(Encoder[T].apply(dto).noSpaces, UTF_8)

  extension (body: Body)
    def to[T](using Decoder[T]): Task[T] =
      for {
        str  <- body.asString(UTF_8)
        json <- ZIO.fromEither(parser.parse(str))
        dto  <- ZIO.fromEither(Decoder[T].decodeJson(json))
      } yield dto
