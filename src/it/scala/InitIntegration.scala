package chessfinder

import com.github.tomakehurst.wiremock.client.WireMock
import com.typesafe.config.ConfigFactory

object InitIntegration:
  def setupMock() = WireMock.configureFor("localhost", 18443)

  lazy val run =
    setupMock()
    System.setProperty("config.file", "src/it/resources/local.conf")
    ConfigFactory.invalidateCaches()
    scala.concurrent.Future(Main.main(Array.empty[String]))(scala.concurrent.ExecutionContext.global)
