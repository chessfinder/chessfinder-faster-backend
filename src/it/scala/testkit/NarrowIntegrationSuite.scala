package chessfinder
package testkit

import zio.test.ZIOSpecDefault
import munit.FunSuite
import zio.Runtime
import zio.config.typesafe.TypesafeConfigProvider
import zio.test.TestEnvironment
import zio.ZLayer
import zio.test.testEnvironment
import zio.test.*
import zio.*
import zio.ZLayer
import search.entity.*
import testkit.NarrowIntegrationSuite
import zio.aws.netty
import zio.aws.core.config.AwsConfig
import persistence.core.DefaultDynamoDBExecutor
import zio.dynamodb.*

abstract class NarrowIntegrationSuite extends ZIOSpecDefault:

  InitIntegrationEnv.Narrow.run

  val configLayer =
    Runtime.setConfigProvider(TypesafeConfigProvider.fromHoconFilePath("src/it/resources/local.conf")).debug
  val loggingLayer = Runtime.removeDefaultLoggers >>> zio.logging.consoleJsonLogger()
  val dynamodbLayer: TaskLayer[DynamoDBExecutor] =
    val in = ((netty.NettyHttpClient.default >+> AwsConfig.default) ++ configLayer)
    in >>> DefaultDynamoDBExecutor.layer

  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    configLayer >+> loggingLayer ++ testEnvironment
