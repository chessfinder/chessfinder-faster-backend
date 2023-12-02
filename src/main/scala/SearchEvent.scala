package chessfinder

import io.circe.{ Codec, Decoder, Encoder }
import io.circe.generic.semiauto.{ deriveCodec, deriveDecoder, deriveEncoder }

case class SearchCommand(
    requestId: String,
    board: String,
    games: List[Game]
)

object SearchCommand:
  given Decoder[SearchCommand] = deriveDecoder[SearchCommand]

case class Game(
    gameId: String,
    pgn: String
)

object Game:
  given Decoder[Game] = deriveDecoder[Game]

case class SearchResult(
    requestId: String,
    machedGameIds: List[String]
)

object SearchResult:
  given Encoder[SearchResult] = deriveEncoder[SearchResult]
