package chessfinder
package util

import zio.{ Config, ConfigProvider, IO }
import zio.config.magnolia.DeriveConfig

object ConfigExtension:

  // extension [A](a: A)(using Config[A] | DeriveConfig[A])
  //   def fromZConfig(c: ConfigProvider): IO[Config.Error, A] =
  //     val con = summon[Config[A] | DeriveConfig[A]] match
  //       case c: Config[A] => c
  //       case c: DeriveConfig[A] => c.desc
  //     c.load(con)

  // extension [A](a: A)(using Config[A])
  //   def fromZConfig(c: ConfigProvider): IO[Config.Error, A] =
  //     c.load[A](summon[Config[A]])

  extension (conf: ConfigProvider)
    // def loadTo[A](using decoder: DeriveConfig[A]): IO[Config.Error, A] =
    //   conf.load[A](decoder.desc)

    def loadTo[A](using decoder: Config[A]): IO[Config.Error, A] =
      conf.load[A](decoder)
