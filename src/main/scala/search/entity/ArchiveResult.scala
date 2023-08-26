package chessfinder
package search.entity

import search.entity.MatchedGames
import search.{ φ, BrokenLogic }

import ornicar.scalalib.newtypes
import sttp.model.Uri
import zio.ZIO

import java.time.{ Instant, YearMonth, ZoneOffset }

// final case class SearchResult(matched: MatchedGames, status: DownloadStatus)
final case class ArchiveResult(
    userId: UserId,
    archiveId: ArchiveId,
    resource: Uri,
    till: Instant,
    lastGamePlayed: Option[GameId],
    downloaded: Int,
    status: ArchiveStatus
):

  def update(
      lastGamePlayed: Option[GameId],
      lastDownloaded: Int,
      now: Instant
  ): ArchiveResult =
    val status =
      if !till.isBefore(now) then ArchiveStatus.PartiallyDownloaded else ArchiveStatus.FullyDownloaded
    val totalDownloaded = downloaded + lastDownloaded
    this.copy(
      downloaded = totalDownloaded,
      status = status,
      lastGamePlayed = lastGamePlayed
    )

object ArchiveResult:

  def apply(
      userId: UserId,
      resource: Uri
  ): Either[BrokenLogic, ArchiveResult] =
    val yearAndMonth: Seq[Int] = resource.pathSegments.segments
      .takeRight(2)
      .flatMap(_.encoded.toIntOption)
      .toSeq
    yearAndMonth match
      case Seq(year, month) =>
        val archiveId = ArchiveId(resource.toString)
        val archive   = ArchiveResult(userId, archiveId, resource, year, month)
        Right(archive)
      case _ => Left(BrokenLogic.UndefinedArchive(resource))

  private def apply(
      userId: UserId,
      archiveId: ArchiveId,
      resource: Uri,
      year: Int,
      month: Int
  ): ArchiveResult =
    val till = YearMonth.of(year, month).plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant()

    ArchiveResult(
      userId = userId,
      archiveId = archiveId,
      resource = resource,
      till = till,
      lastGamePlayed = None,
      downloaded = 0,
      status = ArchiveStatus.NotDownloaded
    )
