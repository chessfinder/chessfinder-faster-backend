package chessfinder
package api

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import sttp.tapir.*

case class ApiError(code: String, msg: String)

object ApiError:
  given Codec[ApiError]  = deriveCodec[ApiError]
  given Schema[ApiError] = Schema.derived[ApiError]

  def fromBrokenLogic(err: BrokenComputation): ApiError = err match
    case err: BrokenComputation.ProfileNotFound => ApiError("PROFILE_NOT_FOUND", err.msg)
    case err: BrokenComputation.TaskNotFound    => ApiError("TASK_NOT_FOUND", err.msg)
    case BrokenComputation.InvalidSearchBoard   => ApiError("INVALID_SEARCH_BOARD", err.msg)
    case err: BrokenComputation.NoGameAvailable => ApiError("NO_GAME_AVAILABLE", err.msg)
    case err                                    => ApiError("SERVER_OVERLOADED", err.msg)
