package chessfinder

import com.github.tomakehurst.wiremock.client.WireMock

object InitIntegration:
  def setupMock() = WireMock.configureFor("localhost", 18443)

  lazy val run =
    setupMock()
    scala.concurrent.Future(Main.main(Array.empty[String]))(scala.concurrent.ExecutionContext.global)



