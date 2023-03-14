package chessfinder
package api

import io.circe.generic.semiauto.deriveCodec
import io.circe.Codec
import sttp.tapir.Schema
import sttp.model.StatusCode
import sttp.tapir.EndpointOutput.OneOfVariant
import sttp.tapir.{ emptyOutputAs, oneOf, oneOfDefaultVariant, oneOfVariant, statusCode, EndpointOutput }
import sttp.tapir.json.circe.jsonBody
import search.BrokenLogic

case class ApiError(code: String, msg: String)

object ApiError:
  given Codec[ApiError]  = deriveCodec[ApiError]
  given Schema[ApiError] = Schema.derived[ApiError]

  def fromBrokenLogic(err: BrokenLogic): ApiError = err match
    case err: BrokenLogic.ProfileNotFound => ApiError("PROFILE_NOT_FOUND", err.msg)
    case BrokenLogic.InvalidSearchBoard   => ApiError("INVALID_SEARCH_BOARD", err.msg)
    case err: BrokenLogic.NoGameAvaliable => ApiError("NO_GAME_AVAILABLE", err.msg)
