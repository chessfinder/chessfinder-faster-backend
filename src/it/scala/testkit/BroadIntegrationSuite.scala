package chessfinder
package testkit

import zio.test.ZIOSpecDefault
import munit.FunSuite
import zio.Runtime
import zio.config.typesafe.TypesafeConfigProvider
import zio.test.TestEnvironment
import zio.ZLayer
import zio.test.testEnvironment

abstract class BroadIntegrationSuite extends ZIOSpecDefault:

  InitIntegrationEnv.Broad.run

  val configLayer =
    Runtime.setConfigProvider(TypesafeConfigProvider.fromHoconFilePath("src/it/resources/local.conf"))
  val loggingLayer = Runtime.removeDefaultLoggers >>> zio.logging.consoleJsonLogger()

  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    configLayer >+> loggingLayer ++ testEnvironment
