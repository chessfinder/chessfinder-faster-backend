package com.typesafe.sbt
package packager
package graalvmnativeimage

import sbt._

/**
  * GraalVM settings
  */
trait GraalVMSharedLibKeys {
  val graalVMSharedLibOptions =
    settingKey[Seq[String]]("GraalVM shared library options")

  val graalVMSharedLibGraalVersion = settingKey[Option[String]](
    "Version of GraalVM to build with. Setting this has the effect of generating a container build image to build the native image with this version of GraalVM."
  )
}

trait GraalVMSharedLibKeysEx extends GraalVMSharedLibKeys {
  val graalVMSharedLibCommand = settingKey[String]("GraalVM shared library executable command")
}
