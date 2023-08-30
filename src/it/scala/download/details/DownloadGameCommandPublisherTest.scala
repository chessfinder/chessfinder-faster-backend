package chessfinder
package download.details

import pubsub.core.PubSub
import pubsub.{ DownloadGameCommand, Platform }
import testkit.NarrowIntegrationSuite
import util.RandomReadableString

import sttp.model.UriInterpolator.*
import zio.*
import zio.stream.ZSink
import zio.test.*

import java.util.UUID

object DownloadGameCommandPublisherTest extends NarrowIntegrationSuite:

  val queue = ZIO.service[DownloadGameCommandPublisher]

  def spec =
    suite("GameDownloadingProducer")(
      suite("publish")(
        test("should writes commands into the queue") {

          val userName     = UserName(RandomReadableString())
          val platformType = Platform.CHESS_DOT_COM
          val userId       = UserId(RandomReadableString())
          val user         = UserIdentified(platformType.toPlatform, userName, userId)
          val taskId       = TaskId(UUID.randomUUID())

          val archiveId1 = ArchiveId("http://example.com/1")
          val archiveId2 = ArchiveId("http://example.com/2")
          val archives   = Seq(archiveId1, archiveId2)

          val expectedCommand1 = DownloadGameCommand(
            userName = userName.value,
            userId = userId.value,
            platform = Platform.CHESS_DOT_COM,
            archiveId = archiveId1.value,
            taskId = taskId.value
          )

          val expectedCommand2 = DownloadGameCommand(
            userName = userName.value,
            userId = userId.value,
            platform = Platform.CHESS_DOT_COM,
            archiveId = archiveId2.value,
            taskId = taskId.value
          )

          val expectedResult = Set(expectedCommand1, expectedCommand2)

          val readingFromQueue =
            for
              subscriber             <- ZIO.service[PubSub[DownloadGameCommand]]
              actualCommandsOrErrors <- subscriber.subscribe.run(ZSink.collectAllToSet)
              actualCommands = actualCommandsOrErrors.collect { case Right(command) => command }
            yield actualCommands
          for
            gameDownloadingProducer <- queue
            _                       <- gameDownloadingProducer.publish(user, archives, taskId)
            actualResult            <- readingFromQueue
            result                  <- assertTrue(actualResult == expectedResult)
          yield result
        }
      )
    ).provideLayer(
      (configLayer >+> sqsLayer >+> DownloadGameCommand.Queue.layer) >+> DownloadGameCommandPublisher.Impl.layer
    ) @@ TestAspect.sequential
