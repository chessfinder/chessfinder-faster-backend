

import com.typesafe.config.ConfigFactory

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.client.{WireMock, MappingBuilder => WireMockMappingBuilder}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.matching.{EqualToPattern, RequestPatternBuilder}

import java.net.ServerSocket
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Using

object Init:

  System.setProperty("config.file", "src/it/resources/local.conf")
  ConfigFactory.invalidateCaches()
  private val config = ConfigFactory.load()

  lazy val setupMock = WireMock.configureFor("localhost", 18443)

  lazy val run = 
    setupMock
    Main.main(Array())
  


