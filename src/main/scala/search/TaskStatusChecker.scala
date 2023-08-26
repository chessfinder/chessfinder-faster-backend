package chessfinder
package search

import api.{ TaskResponse, TaskStatusResponse }
import aspect.Span
import client.ClientError
import client.ClientError.ProfileNotFound
import client.chess_com.ChessDotComClient
import client.chess_com.dto.{ Archives, Games }
import persistence.{ GameRecord, PlatformType, UserRecord }
import search.BrokenLogic
import search.entity.*
import search.repo.*

import chess.format.pgn.PgnStr
import izumi.reflect.Tag
import sttp.model.Uri
import zio.dynamodb.*
import zio.{ UIO, ZIO, ZIOAspect, ZLayer }

import scala.annotation.tailrec

trait TaskStatusChecker:

  def check(taskId: TaskId): φ[TaskStatusResponse]

object TaskStatusChecker:

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
