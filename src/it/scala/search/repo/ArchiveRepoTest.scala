package chessfinder
package search.repo

import persistence.{ ArchiveRecord, GameRecord, PlatformType, UserRecord }
import persistence.core.DefaultDynamoDBExecutor
import search.entity.*
import search.BrokenLogic
import sttp.model.Uri
import sttp.model.Uri.UriContext
import testkit.NarrowIntegrationSuite
import testkit.parser.JsonReader
import testkit.wiremock.ClientBackdoor
import util.{ RandomReadableString, UriParser }

import cats.effect.kernel.syntax.resource
import chess.format.pgn.PgnStr
import com.typesafe.config.ConfigFactory
import io.circe.*
import zio.*
import zio.aws.core.config.AwsConfig
import zio.aws.netty
import zio.dynamodb.*
import zio.http.Client
import zio.test.*

import java.time.*
import java.util.UUID
import scala.util.{ Success, Try }

object ArchiveRepoTest extends NarrowIntegrationSuite:

  val repo = ZIO.service[ArchiveRepo]

  def spec =
    suite("ArchiveRepo")(
      suite("initiate")(
        test("should initiate new undowloaded archive in the database") {

          val userId = UserId(RandomReadableString())

          val archive1 = ArchiveRecord(
            user_id = userId,
            archive_id = ArchiveId("https://example.com/pub/player/userName/games/2022/10"),
            resource = uri"https://example.com/pub/player/userName/games/2022/10",
            last_game_played = None,
            downloaded = 0,
            status = ArchiveStatus.NotDownloaded,
            till = LocalDate.of(2022, 11, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
          )

          val archive2 = ArchiveRecord(
            user_id = userId,
            archive_id = ArchiveId("https://example.com/pub/player/userName/games/2022/11"),
            resource = uri"https://example.com/pub/player/userName/games/2022/11",
            last_game_played = None,
            downloaded = 0,
            status = ArchiveStatus.NotDownloaded,
            till = LocalDate.of(2022, 12, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
          )

          val archiveResources = Seq(
            uri"https://example.com/pub/player/userName/games/2022/10",
            uri"https://example.com/pub/player/userName/games/2022/11"
          )

          for
            archiveRepo  <- repo
            _            <- archiveRepo.initiate(userId, archiveResources)
            actualResult <- ArchiveRecord.Table.list[ArchiveRecord](userId)
            expectedResult = Set(archive1, archive2)
            result <- assertTrue(actualResult.toSet == expectedResult)
          yield result
        }
      ),
      suite("get")(
        test("should get the archive from the database if it exists") {

          val userId    = UserId(RandomReadableString())
          val archiveId = ArchiveId("https://example.com/pub/player/userName/games/2022/10")

          val archive = ArchiveRecord(
            user_id = userId,
            archive_id = archiveId,
            resource = uri"https://example.com/pub/player/userName/games/2022/10",
            last_game_played = None,
            downloaded = 0,
            status = ArchiveStatus.NotDownloaded,
            till = LocalDate.of(2022, 11, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
          )

          val expectedResult =
            ArchiveResult(
              userId = userId,
              archiveId = archiveId,
              resource = uri"https://example.com/pub/player/userName/games/2022/10",
              lastGamePlayed = None,
              downloaded = 0,
              status = ArchiveStatus.NotDownloaded,
              till = LocalDate.of(2022, 11, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
            )

          for
            archiveRepo  <- repo
            _            <- ArchiveRecord.Table.put(archive)
            actualResult <- archiveRepo.get(userId, archiveId)
            result       <- assertTrue(actualResult == expectedResult)
          yield result
        },
        test("should return ArchiveNotFound if it does not exists in the database") {

          val userId    = UserId(RandomReadableString())
          val archiveId = ArchiveId("https://example.com/pub/player/userName/games/2022/11")

          for
            archiveRepo  <- repo
            actualResult <- archiveRepo.get(userId, archiveId).either
            result       <- assertTrue(actualResult == Left(BrokenLogic.ArchiveNotFound(archiveId)))
          yield result
        }
      ),
      suite("getAll")(
        test("should return all archive of the give user") {

          val userId = UserId(RandomReadableString())

          val archive1 = ArchiveRecord(
            user_id = userId,
            archive_id = ArchiveId("https://example.com/pub/player/userName/games/2022/10"),
            resource = uri"https://example.com/pub/player/userName/games/2022/10",
            last_game_played = None,
            downloaded = 0,
            status = ArchiveStatus.NotDownloaded,
            till = LocalDate.of(2022, 11, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
          )

          val archive2 = ArchiveRecord(
            user_id = userId,
            archive_id = ArchiveId("https://example.com/pub/player/userName/games/2022/11"),
            resource = uri"https://example.com/pub/player/userName/games/2022/11",
            last_game_played = None,
            downloaded = 0,
            status = ArchiveStatus.NotDownloaded,
            till = LocalDate.of(2022, 12, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
          )

          val archiveResult1 =
            ArchiveResult(
              userId = userId,
              archiveId = ArchiveId("https://example.com/pub/player/userName/games/2022/10"),
              resource = uri"https://example.com/pub/player/userName/games/2022/10",
              lastGamePlayed = None,
              downloaded = 0,
              status = ArchiveStatus.NotDownloaded,
              till = LocalDate.of(2022, 11, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
            )

          val archiveResult2 =
            ArchiveResult(
              userId = userId,
              archiveId = ArchiveId("https://example.com/pub/player/userName/games/2022/11"),
              resource = uri"https://example.com/pub/player/userName/games/2022/11",
              lastGamePlayed = None,
              downloaded = 0,
              status = ArchiveStatus.NotDownloaded,
              till = LocalDate.of(2022, 12, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
            )

          val expectedResult = Set(archiveResult1, archiveResult2)

          for
            archiveRepo  <- repo
            _            <- ArchiveRecord.Table.putMany(archive1, archive2)
            actualResult <- archiveRepo.getAll(userId)
            expectedResult = Set(archiveResult1, archiveResult2)
            result <- assertTrue(actualResult.toSet == expectedResult)
          yield result
        }
      ),
      suite("update")(
        test("should update existing archive") {

          val userId    = UserId(RandomReadableString())
          val till      = Instant.now()
          val gameId    = GameId(RandomReadableString())
          val archiveId = ArchiveId("https://example.com/pub/player/userName/games/2022/11")

          val initialArchive = ArchiveRecord(
            user_id = userId,
            archive_id = archiveId,
            resource = uri"https://example.com/pub/player/userName/games/2022/11",
            till = till,
            last_game_played = None,
            downloaded = 0,
            status = ArchiveStatus.NotDownloaded
          )

          val updatedArchive = ArchiveResult(
            userId = userId,
            archiveId = archiveId,
            resource = uri"https://example.com/pub/player/userName/games/2022/11",
            till = till,
            lastGamePlayed = Some(gameId),
            downloaded = 10,
            status = ArchiveStatus.FullyDownloaded
          )

          val expectedResult = ArchiveRecord(
            user_id = userId,
            archive_id = archiveId,
            resource = uri"https://example.com/pub/player/userName/games/2022/11",
            till = till,
            last_game_played = Some(gameId),
            downloaded = 10,
            status = ArchiveStatus.FullyDownloaded
          )

          for
            archiveRepo  <- repo
            _            <- ArchiveRecord.Table.put(initialArchive)
            _            <- archiveRepo.update(updatedArchive)
            actualResult <- ArchiveRecord.Table.get[ArchiveRecord](userId, archiveId)
            result       <- assertTrue(actualResult == Right(expectedResult))
          yield result
        }
      )
    ).provideLayer(dynamodbLayer >+> ArchiveRepo.Impl.layer) @@ TestAspect.sequential
