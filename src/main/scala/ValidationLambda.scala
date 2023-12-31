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

object ValidationLambda extends RequestStreamHandler:

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
    val validationCommand       = decode[ValidationCommand](input).toTry.get
    val maybeProbabilisticBoard = SearchFen.read(SearchFen(validationCommand.board))

    val result = ValidationResult(
      requestId = validationCommand.requestId,
      isValid = maybeProbabilisticBoard.isValid,
      comment = None
    )
    Encoder[ValidationResult].apply(result).noSpaces
