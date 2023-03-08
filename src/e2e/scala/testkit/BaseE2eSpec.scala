package com.nclh.oneair.ticketsaver.e2e.spec

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.nclh.oneair.ticketsaver.e2e.Init
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._

class BaseE2eSpec
  extends AnyWordSpecLike
    with ScalatestRouteTest
    with Matchers
    with ScalaFutures {

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 120.seconds)

  override def testConfig = ConfigFactory.empty()

  implicit val typedSystem: ActorSystem[Nothing] = system.toTyped

  override def beforeAll(): Unit = {
    super.beforeAll()
    Init.run
  }
}
