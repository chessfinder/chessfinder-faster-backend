package chessfinder
package search.entity

import sttp.model.Uri

case class MatchedGame(resource: Uri)

type MatchedGames = Seq[MatchedGame]
