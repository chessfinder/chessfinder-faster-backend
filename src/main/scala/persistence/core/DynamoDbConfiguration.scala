package chessfinder
package persistence.core

import software.amazon.awssdk.regions.Region
import zio.Config
import zio.config.*
import zio.config.magnolia.deriveConfig

import java.net.URI

case class DynamoDbConfiguration(
    region: String,
    uri: String
):
  val uriValidated: URI       = URI.create(uri)
  val regionValidated: Region = Region.of(region)

object DynamoDbConfiguration:

  given config: Config[DynamoDbConfiguration] =
    deriveConfig[DynamoDbConfiguration].nested("database-dynamodb-config")
