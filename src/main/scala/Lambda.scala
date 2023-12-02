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

object Lambda extends RequestStreamHandler:

  override def handleRequest(input: InputStream, output: OutputStream, context: Context): Unit =
    val allBytes  = input.readAllBytes()
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
    val searchCommand = decode[SearchCommand](input).toTry.get
    val machedGameIds = searchCommand.games.map(_.gameId).headOption.toList
    val result        = SearchResult(searchCommand.requestId, machedGameIds)
    Encoder[SearchResult].apply(result).noSpaces
