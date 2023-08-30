package chessfinder

import search.*

import sttp.model.Uri
import zio.IO

sealed trait BrokenComputation(val msg: String)
object BrokenComputation:
  case object InvalidSearchBoard extends BrokenComputation(s"Invalid board!")
  case object InvalidGame        extends BrokenComputation(s"Invalid game!")
  case class ProfileNotFound(user: User)
      extends BrokenComputation(s"Profile ${user.userName} from ${user.platform.toString} not found!")
  case class NoGameAvailable(user: User)
      extends BrokenComputation(
        s"Profile ${user.userName} from ${user.platform.toString} does not have any information about their played games!"
      )
  case object ServiceOverloaded extends BrokenComputation("Service is overloaded. Try later.")
  case class ProfileIsNotCached(user: User)
      extends BrokenComputation(s"Profile ${user.userName} from ${user.platform.toString} is not chached!")
  case class TaskProgressOverflown(taskId: TaskId)
      extends BrokenComputation(s"Task ${taskId.toString} progress is overflown")
  case class TaskNotFound(taskId: TaskId) extends BrokenComputation(s"Task ${taskId.toString} not found")
  case class SearchResultNotFound(searchId: SearchRequestId)
      extends BrokenComputation(s"Task ${searchId.toString} not found")
  case class ArchiveNotFound(archiveId: ArchiveId)
      extends BrokenComputation(s"Archive ${archiveId.value} not found")
  case class UndefinedArchive(resource: Uri)
      extends BrokenComputation(s"Archive resource ${resource.toString} does not contain archive period")

type Computation[T] = IO[BrokenComputation, T]
