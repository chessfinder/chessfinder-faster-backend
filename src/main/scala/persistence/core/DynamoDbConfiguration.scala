package chessfinder
package persistence.config

import software.amazon.awssdk.regions.Region
import io.circe.{ Decoder, Encoder }
import io.circe.generic.semiauto.*
import java.net.URI
import zio.Config
import zio.config.*
import zio.config.magnolia.deriveConfig

case class DynamoDbConfiguration(
    region: String,
    uri: String
):
  val uriValidated: URI       = URI.create(uri)
  val regionValidated: Region = Region.of(region)

object DynamoDbConfiguration:
  given Decoder[DynamoDbConfiguration] = deriveDecoder[DynamoDbConfiguration]
  given config: Config[DynamoDbConfiguration] =
    deriveConfig[DynamoDbConfiguration].nested("database-dynamodb-config")
