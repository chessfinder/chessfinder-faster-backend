package chessfinder
package testkit

import com.github.tomakehurst.wiremock.client.WireMock

import com.github.tomakehurst.wiremock.client.WireMock
import com.typesafe.config.ConfigFactory
import chessfinder.persistence.core.*
import chessfinder.persistence.config.*
import chessfinder.persistence.*
import zio.dynamodb.*
import zio.{ Clock, IO, TaskLayer, ULayer, Unsafe, ZIO, ZLayer }
import scala.util.Try
import scala.concurrent.Await
import scala.concurrent.duration.*
import zio.aws.dynamodb.DynamoDb
import zio.aws.netty
import zio.aws.core.config.AwsConfig
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import com.typesafe.config.{ Config, ConfigFactory }
import zio.Runtime
import zio.config.typesafe.TypesafeConfigProvider
import zio.Cause

open class InitIntegrationEnv:

  private val configLayer  = Runtime.setConfigProvider(TypesafeConfigProvider.fromResourcePath())
  private val loggingLayer = Runtime.removeDefaultLoggers >>> zio.logging.consoleJsonLogger()

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
    val dynamodbLayer: TaskLayer[DynamoDBExecutor] =
      val in = (configLayer >+> loggingLayer) >+> ((netty.NettyHttpClient.default >+> AwsConfig.default))
      in >>> DefaultDynamoDBExecutor.layer

    Try {
      val io: IO[Throwable, Unit] =
        val dependentIo =
          for
            _ <- createSortedSetTableWithSingleKey(UserRecord.Table).catchNonFatalOrDie(e =>
              ZIO.logErrorCause(e.getMessage, Cause.fail(e))
            )
            _ <- createSortedSetTableWithSingleKey(GameRecord.Table).catchNonFatalOrDie(e =>
              ZIO.logErrorCause(e.getMessage, Cause.fail(e))
            )
            _ <- createUniqueTableWithSingleKey(TaskRecord.Table).catchNonFatalOrDie(e =>
              ZIO.logErrorCause(e.getMessage, Cause.fail(e))
            )
          yield ()
        dependentIo.provide(dynamodbLayer)

      Await.result(
        Unsafe.unsafe { implicit unsafe =>
          val runtime = Runtime.unsafe.fromLayer(configLayer >+> loggingLayer)
          runtime.unsafe.runToFuture(io)
        }.future,
        10.seconds
      )
    }

  private def createUniqueTableWithSingleKey(
      table: DynamoTable.Unique[?, ?]
  ): ZIO[DynamoDBExecutor, Throwable, Unit] =
    DynamoDBQuery
      .createTable(
        tableName = table.name,
        keySchema = KeySchema(table.partitionKeyName),
        billingMode = BillingMode.PayPerRequest
      )(AttributeDefinition.attrDefnString(table.partitionKeyName))
      .execute

  private def createSortedSetTableWithSingleKey(
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
      scala.concurrent.Future(Main.main(Array.empty[String]))(scala.concurrent.ExecutionContext.global)
      Thread.sleep(3000)
