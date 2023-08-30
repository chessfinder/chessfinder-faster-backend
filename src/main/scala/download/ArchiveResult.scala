package chessfinder
package download

import chessfinder.{ ArchiveId, BrokenComputation }
import sttp.model.Uri

import java.time.{ Instant, YearMonth, ZoneOffset }

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
  ): Either[BrokenComputation, ArchiveResult] =
    val yearAndMonth: Seq[Int] = resource.pathSegments.segments
      .takeRight(2)
      .flatMap(_.encoded.toIntOption)
      .toSeq
    yearAndMonth match
      case Seq(year, month) =>
        val archiveId = ArchiveId(resource.toString)
        val archive   = ArchiveResult(userId, archiveId, resource, year, month)
        Right(archive)
      case _ => Left(BrokenComputation.UndefinedArchive(resource))

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
