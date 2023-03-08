package com.nclh.oneair.ticketsaver.e2e

import com.nclh.oneair.ticketsaver.Main
import com.nclh.oneair.ticketsaver.e2e.backdoor.{AmadeusBackdoor, MysqlBackdoor, SeawareBackdoor}
import com.typesafe.config.ConfigFactory
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.HttpRequest
import akka.stream.Materializer
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.client.{WireMock, MappingBuilder => WireMockMappingBuilder}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.matching.{EqualToPattern, RequestPatternBuilder}
import org.scalatest.{BeforeAndAfterAll, TestSuite, TestSuiteMixin}

import java.net.ServerSocket
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Using

object Init {

  System.setProperty("config.file", "src/e2e/resources/local.conf")
  ConfigFactory.invalidateCaches()
  private val config = ConfigFactory.load()

  lazy val mysqlBackdoor = MysqlBackdoor.fromConfig(config)
  lazy val setupMock = WireMock.configureFor("localhost", 18443)

  lazy val run = {

    mysqlBackdoor
    setupMock
    Main.main(Array())
  }

}
