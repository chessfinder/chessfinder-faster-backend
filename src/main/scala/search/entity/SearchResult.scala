package chessfinder
package search.entity

import chessfinder.search.entity.MatchedGames
import sttp.model.Uri

final case class SearchResult(matched: MatchedGames, status: DownloadStatus)
