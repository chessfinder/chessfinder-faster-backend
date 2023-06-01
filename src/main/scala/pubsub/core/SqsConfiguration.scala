package chessfinder
package pubsub.core

import software.amazon.awssdk.regions.Region
import zio.Config
import zio.config.*
import zio.config.magnolia.deriveConfig

import java.net.URI

case class SqsConfiguration(
    region: String,
    uri: String
):
  val uriValidated: URI       = URI.create(uri)
  val regionValidated: Region = Region.of(region)

object SqsConfiguration:

  given config: Config[SqsConfiguration] =
    deriveConfig[SqsConfiguration].nested("sqs-config")
