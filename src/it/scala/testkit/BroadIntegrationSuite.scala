package chessfinder
package testkit

import munit.FunSuite
import zio.{ Runtime, ZLayer }
import zio.config.typesafe.TypesafeConfigProvider
import zio.test.{ testEnvironment, TestEnvironment, ZIOSpecDefault }

abstract class BroadIntegrationSuite extends ZIOSpecDefault:

  InitIntegrationEnv.Broad.run

  val configLayer   = InitIntegrationEnv.Broad.configLayer
  val loggingLayer  = InitIntegrationEnv.Broad.loggingLayer
  val dynamodbLayer = InitIntegrationEnv.Narrow.dynamodbLayer
  val sqsLayer      = InitIntegrationEnv.Narrow.sqsLayer

  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    configLayer >+> loggingLayer ++ testEnvironment
