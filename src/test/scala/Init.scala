package chessfinder

import com.github.tomakehurst.wiremock.client.WireMock

object Init:
  def setupMock() = WireMock.configureFor("localhost", 18443)

  lazy val run =
    setupMock()

trait InitFirst:
  Init.run
