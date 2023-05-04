package chessfinder
package search

import search.entity.*
import zio.ZLayer
import zio.{ UIO, ZIO }
import chessfinder.client.chess_com.ChessDotComClient
import chessfinder.client.ClientError
import search.BrokenLogic
import sttp.model.Uri
import chessfinder.client.chess_com.dto.Games
import chessfinder.client.chess_com.dto.Archives
import annotation.tailrec
import chess.format.pgn.PgnStr
import chessfinder.persistence.GameRecord
import chessfinder.persistence.UserRecord
import chessfinder.persistence.PlatformType
import chessfinder.client.ClientError.ProfileNotFound
import zio.dynamodb.*
import search.repo.*
import api.ApiVersion
import izumi.reflect.Tag
import chessfinder.api.TaskResponse
import chessfinder.api.TaskStatusResponse
import aspect.Span
import zio.ZIOAspect

trait TaskStatusChecker:

  def check(taskId: TaskId): φ[TaskStatusResponse]

object TaskStatusChecker:

  import zio.logging.fileAsyncJsonLogger

  def check(taskId: TaskId): ψ[TaskStatusChecker, TaskStatusResponse] =
    ZIO.serviceWithZIO[TaskStatusChecker](_.check(taskId)) @@ Span.log("TaskStatusChecker") @@ ZIOAspect
      .annotated("user", "muser")

  class Impl(taskRepo: TaskRepo) extends TaskStatusChecker:

    def check(taskId: TaskId): φ[TaskStatusResponse] =
      taskRepo.get(taskId).mapError {
        case err: BrokenLogic.TaskNotFound => err
        case _                             => BrokenLogic.ServiceOverloaded
      }

  object Impl:
    val layer = ZLayer {
      for taskRepo <- ZIO.service[TaskRepo]
      yield Impl(taskRepo)
    }
