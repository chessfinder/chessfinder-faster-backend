package chessfinder
package search.entity

enum ArchiveStatus(val repr: String):
  case FullyDownloaded     extends ArchiveStatus("FULLY_DOWNLOADED")
  case PartiallyDownloaded extends ArchiveStatus("PARTIALLY_DOWNLOADED")
  case NotDownloaded       extends ArchiveStatus("NOT_DOWNLOADED")

object ArchiveStatus:
  def fromRepr(repr: String): Either[String, ArchiveStatus] =
    repr match
      case "FULLY_DOWNLOADED"     => Right(ArchiveStatus.FullyDownloaded)
      case "PARTIALLY_DOWNLOADED" => Right(ArchiveStatus.PartiallyDownloaded)
      case "NOT_DOWNLOADED"       => Right(ArchiveStatus.NotDownloaded)
      case str                    => Left(s"ArchiveStatus does not have value for $str")
