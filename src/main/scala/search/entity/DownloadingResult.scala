package chessfinder
package search.entity

import cats.data.NonEmptySeq
import sttp.model.Uri

case class DownloadingResult(games: HistoricalGames, unreachableArchives: Seq[Uri])

