package chessfinder
package search

enum SearchStatus(val repr: String):
  case InProgress        extends SearchStatus("IN_PROGRESS")
  case SearchedAll       extends SearchStatus("SEARCHED_ALL")
  case SearchedPartially extends SearchStatus("SEARCHED_PARTIALLY")

object SearchStatus:
  def fromRepr(repr: String): Either[String, SearchStatus] =
    repr match
      case "IN_PROGRESS"        => Right(SearchStatus.InProgress)
      case "SEARCHED_ALL"       => Right(SearchStatus.SearchedAll)
      case "SEARCHED_PARTIALLY" => Right(SearchStatus.SearchedPartially)
      case str                  => Left(s"SearchStatus does not have value for $str")
