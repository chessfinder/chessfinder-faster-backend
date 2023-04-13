package chessfinder

import com.github.tomakehurst.wiremock.client.WireMock

object Init:
  def setupMock() =
    WireMock.configureFor("localhost", 18443)
    WireMock.removeAllMappings()

  lazy val run =
    setupMock()

trait InitFirst:
  Init.run
