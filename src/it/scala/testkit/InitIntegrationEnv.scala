package chessfinder
package testkit

import persistence.*
import persistence.core.*
import pubsub.core.*

import com.github.tomakehurst.wiremock.client.WireMock
import com.typesafe.config.{ Config, ConfigFactory }
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.services.sqs.model.QueueAttributeName as JQueueAttributeName
import zio.aws.core.config.AwsConfig
import zio.aws.dynamodb.DynamoDb
import zio.aws.netty
import zio.aws.sqs.Sqs
import zio.aws.sqs.model.QueueAttributeName
import zio.config.typesafe.TypesafeConfigProvider
import zio.dynamodb.*
import zio.sqs.{ SqsStream, SqsStreamSettings, Utils }
import zio.{ Cause, Clock, IO, Runtime, TaskLayer, ULayer, Unsafe, ZIO, ZLayer }

import scala.concurrent.Await
import scala.concurrent.duration.*
import scala.util.Try

open class InitIntegrationEnv:

  val configLayer =
    Runtime.setConfigProvider(TypesafeConfigProvider.fromHoconFilePath("src/it/resources/local.conf")).debug
  val loggingLayer = Runtime.removeDefaultLoggers >>> zio.logging.consoleJsonLogger()

  val dynamodbLayer: TaskLayer[DynamoDBExecutor] =
    val in = ((netty.NettyHttpClient.default >+> AwsConfig.default) ++ configLayer)
    in >>> DefaultDynamoDBExecutor.layer

  val sqsLayer: TaskLayer[Sqs] =
    val in = ((netty.NettyHttpClient.default >+> AwsConfig.default) ++ configLayer)
    in >>> DefaultSqsExecutor.layer

  def run =
    System.setProperty("aws.accessKeyId", "aKey")
    System.setProperty("aws.secretAccessKey", "sSecret")

    setupMock()
    System.setProperty("config.file", "src/it/resources/local.conf")
    setupDynamoDb()
    ConfigFactory.invalidateCaches()

  private def setupMock() =
    WireMock.configureFor("localhost", 18443)
    WireMock.removeAllMappings()

  private def setupDynamoDb() =
    Try {
      val io: IO[Throwable, Unit] =
        val dependentIo =
          for
            _ <- createSortedSetTable(UserRecord.Table).ignore
            _ <- createSortedSetTable(GameRecord.Table).ignore
            _ <- createUniqueTable(TaskRecord.Table).ignore
            _ <- createSortedSetTable(ArchiveRecord.Table).ignore
            _ <- createUniqueTable(SearchResultRecord.Table).ignore

            _ <- Utils.createQueue(
              "download-games.fifo",
              Map(QueueAttributeName.wrap(JQueueAttributeName.FIFO_QUEUE) -> "true")
            )
            _ <- Utils.createQueue(
              "search-board.fifo",
              Map(QueueAttributeName.wrap(JQueueAttributeName.FIFO_QUEUE) -> "true")
            )
          yield ()
        dependentIo.provide(dynamodbLayer ++ sqsLayer)

      Await.result(
        Unsafe.unsafe { implicit unsafe =>
          val runtime = Runtime.unsafe.fromLayer(configLayer >+> loggingLayer)
          runtime.unsafe.runToFuture(io)
        }.future,
        10.seconds
      )
    }

  private def createUniqueTable(
      table: DynamoTable.Unique[?, ?]
  ): ZIO[DynamoDBExecutor, Throwable, Unit] =
    DynamoDBQuery
      .createTable(
        tableName = table.name,
        keySchema = KeySchema(table.partitionKeyName),
        billingMode = BillingMode.PayPerRequest
      )(AttributeDefinition.attrDefnString(table.partitionKeyName))
      .execute

  private def createSortedSetTable(
      table: DynamoTable.SortedSeq[?, ?, ?]
  ): ZIO[DynamoDBExecutor, Throwable, Unit] =
    DynamoDBQuery
      .createTable(
        tableName = table.name,
        keySchema = KeySchema(table.partitionKeyName, table.sortKeyName),
        billingMode = BillingMode.PayPerRequest
      )(
        AttributeDefinition.attrDefnString(table.partitionKeyName),
        AttributeDefinition.attrDefnString(table.sortKeyName)
      )
      .execute

object InitIntegrationEnv:

  object Narrow extends InitIntegrationEnv:

    override lazy val run =
      super.run

  object Broad extends InitIntegrationEnv:

    override lazy val run =
      super.run
      // scala.concurrent.Future(Main.main(Array.empty[String]))(scala.concurrent.ExecutionContext.global)
      Thread.sleep(3000)
