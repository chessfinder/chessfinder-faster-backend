package chessfinder
package search.entity

import cats.data.NonEmptySeq
import sttp.model.Uri

case class DownloadingResult(games: HistoricalGames, unreachableArchives: List[Uri])

object DownloadingResult:
  val empty = DownloadingResult(List.empty, List.empty)

