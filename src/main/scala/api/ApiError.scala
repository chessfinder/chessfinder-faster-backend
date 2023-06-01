package chessfinder
package api

import search.BrokenLogic
import sttp.model.StatusCode
import sttp.tapir.EndpointOutput.OneOfVariant
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.*

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class ApiError(code: String, msg: String)

object ApiError:
  given Codec[ApiError]  = deriveCodec[ApiError]
  given Schema[ApiError] = Schema.derived[ApiError]

  def fromBrokenLogic(err: BrokenLogic): ApiError = err match
    case err: BrokenLogic.ProfileNotFound => ApiError("PROFILE_NOT_FOUND", err.msg)
    case err: BrokenLogic.TaskNotFound    => ApiError("TASK_NOT_FOUND", err.msg)
    case BrokenLogic.InvalidSearchBoard   => ApiError("INVALID_SEARCH_BOARD", err.msg)
    case err: BrokenLogic.NoGameAvailable => ApiError("NO_GAME_AVAILABLE", err.msg)
    case err                              => ApiError("SERVER_OVERLOADED", err.msg)
