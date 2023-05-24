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

  val configLayer   = InitIntegrationEnv.Broad.configLayer
  val loggingLayer  = InitIntegrationEnv.Broad.loggingLayer
  val dynamodbLayer = InitIntegrationEnv.Narrow.dynamodbLayer
  val sqsLayer      = InitIntegrationEnv.Narrow.sqsLayer

  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    configLayer >+> loggingLayer ++ testEnvironment
