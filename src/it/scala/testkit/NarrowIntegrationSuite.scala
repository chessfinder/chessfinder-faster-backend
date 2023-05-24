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
import zio.aws.sqs.Sqs
import pubsub.core.*

abstract class NarrowIntegrationSuite extends ZIOSpecDefault:

  InitIntegrationEnv.Narrow.run

  val configLayer   = InitIntegrationEnv.Narrow.configLayer
  val loggingLayer  = InitIntegrationEnv.Narrow.loggingLayer
  val dynamodbLayer = InitIntegrationEnv.Narrow.dynamodbLayer
  val sqsLayer      = InitIntegrationEnv.Narrow.sqsLayer

  override val bootstrap: ZLayer[Any, Any, TestEnvironment] =
    configLayer >+> loggingLayer ++ testEnvironment
