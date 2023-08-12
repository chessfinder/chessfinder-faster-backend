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
    Runtime.setConfigProvider(TypesafeConfigProvider.fromResourcePath()).debug

  val loggingLayer = Runtime.removeDefaultLoggers >>> zio.logging.consoleJsonLogger()

  val dynamodbLayer: TaskLayer[DynamoDBExecutor] =
    val in = ((netty.NettyHttpClient.default >+> AwsConfig.default) ++ configLayer)
    in >>> DefaultDynamoDBExecutor.layer

  val sqsLayer: TaskLayer[Sqs] =
    val in = ((netty.NettyHttpClient.default >+> AwsConfig.default) ++ configLayer)
    in >>> DefaultSqsExecutor.layer

  def run = ()
  // System.setProperty("aws.accessKeyId", "aKey")
  // System.setProperty("aws.secretAccessKey", "sSecret")

  setupMock()

  private def setupMock() =
    WireMock.configureFor("localhost", 18443)
    WireMock.removeAllMappings()

object InitIntegrationEnv:

  object Narrow extends InitIntegrationEnv:

    override lazy val run =
      super.run

  object Broad extends InitIntegrationEnv:

    override lazy val run =
      super.run
      // scala.concurrent.Future(Main.main(Array.empty[String]))(scala.concurrent.ExecutionContext.global)
      Thread.sleep(3000)
