package chessfinder
package api

import java.nio.charset.StandardCharsets

import com.amazonaws.services.lambda.runtime.{ Context, RequestStreamHandler }
import java.io.{ BufferedWriter, InputStream, OutputStream, OutputStreamWriter }
import io.circe.*
import scala.util.Try
import java.io.{ InputStream, OutputStream }
import io.circe.Parser
import io.circe.parser.decode
import chessfinder.core.SearchFen
import cats.data.Validated.Valid
import cats.data.Validated.Invalid
import chess.format.pgn.PgnStr
import chessfinder.core.{ Finder, PgnReader }
import scala.annotation.tailrec

object SearchLambda extends RequestStreamHandler:

  val StopSearchIfFound = 10

  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit =
    val allBytes = input.readAllBytes()
    input.close()
    val inputStr  = new String(allBytes, StandardCharsets.UTF_8)
    val outputStr = handleStringRequest(inputStr, context)
    val buffer    = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))
    val writing = Try {
      buffer.write(outputStr)
    }
    buffer.flush()
    buffer.close()
    writing.get

  private def handleStringRequest(input: String, context: Context): String =
    val searchCommand           = decode[SearchCommand](input).toTry.get
    val maybeProbabilisticBoard = SearchFen.read(SearchFen(searchCommand.board))
    val probabilisticBoard = maybeProbabilisticBoard match {
      case Valid(board)    => board
      case Invalid(errors) => throw new RuntimeException(errors.toNonEmptyList.toList.mkString(", "))
    }

    @tailrec()
    def searchGames(
        games: List[Game],
        found: List[Game],
        examined: Int,
        remains: Int
    ): (List[Game], Int) =
      games match
        case game :: tail if remains > 0 =>
          val gamePgn   = PgnStr(game.pgn)
          val maybeGame = PgnReader.read(gamePgn)
          val isFound   = maybeGame.map(g => Finder.find(g, probabilisticBoard)).getOrElse(false)
          if isFound
          then searchGames(tail, game :: found, examined + 1, remains - 1)
          else searchGames(tail, found, examined + 1, remains)
        case _ => (found, examined)

    val (machedGame, examined) = searchGames(searchCommand.games, Nil, 0, StopSearchIfFound)
    val machedGameIds          = machedGame.map(_.resource).reverse

    val result = SearchResult(searchCommand.requestId, examined, machedGameIds)
    Encoder[SearchResult].apply(result).noSpaces
