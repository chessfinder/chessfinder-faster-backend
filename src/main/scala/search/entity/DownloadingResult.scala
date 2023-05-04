package chessfinder
package search.entity

import sttp.model.Uri

final case class DownloadingResult(failedArchives: Seq[Uri])

object DownloadingResult:
  val empty = DownloadingResult(List.empty)
