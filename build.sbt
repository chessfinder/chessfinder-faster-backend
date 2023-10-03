import sbt.*
import sbt.{ Def, _ }
import sbt.Keys._
import com.typesafe.sbt.packager.graalvmnativeimage.GraalVMSharedLibPlugin
import com.typesafe.sbt.packager.graalvmnativeimage.GraalVMSharedLibPlugin.autoImport.GraalVMSharedLib

// enablePlugins(GraalVMNativeImagePlugin)
enablePlugins(GraalVMSharedLibPlugin)
enablePlugins(GitPlugin)
enablePlugins(GitVersioning)
// ThisBuild / enablePlugins(GitVersioning)

ThisBuild / idePackagePrefix := Some("chessfinder")
ThisBuild / organization := "eudemonia"
// ThisBuild / version           := "0.1"
ThisBuild / git.useGitDescribe := true
ThisBuild / scalaVersion       := "3.3.0"
ThisBuild / semanticdbEnabled  := true // enable SemanticDB
ThisBuild / testFrameworks ++= List(
  new TestFramework("munit.Framework"),
  new TestFramework("zio.test.sbt.ZTestFramework")
)

fork := true

val lilaMaven = "lila-maven" at "https://raw.githubusercontent.com/lichess-org/lila-maven/master"
val sonashots = "sonashots" at "https://oss.sonatype.org/content/repositories/snapshots"
val commonScalaOptions = Seq(
  "-encoding",
  "utf-8",
  // "-rewrite",
  "-source:future-migration",
  "-indent",
  "-explaintypes",
  "-feature",
  "-language:postfixOps",
  "-deprecation",
  "-Ykind-projector"
  // "-Xfatal-warnings" // Warnings as errors!
  // "-Ywarn-unused-import"
  // "-Wunused:imports",
  // "-Ywarn-unused"
)

val graalVMSharedLibOptions = Seq(
  "--verbose",
  "--no-fallback",
  "--install-exit-handlers",
  "--enable-http",
  "--allow-incomplete-classpath",
  "--report-unsupported-elements-at-runtime",
  "--initialize-at-run-time=io.netty",
  "--trace-class-initialization=io.netty.util.AbstracferenceCounted",
  "-H:+StaticExecutableWithDynamicLibC",
  "-H:+RemoveSaturatedTypeFlows",
  "-H:+ReportExceptionStackTraces",
  "-H:+PrintClassInitialization",
  "-H:Name=chessfinder-core"
)

lazy val core = project
  .in(file("src_core"))
  .settings(
    name := "chess-finder-core",
    libraryDependencies ++= Dependencies.scalachess ++ Dependencies.`chessfinder-core-tests`,
    libraryDependencies += "org.graalvm.sdk" % "graal-sdk" % "23.1.0" % "provided",
    scalacOptions := commonScalaOptions,
    resolvers ++= Seq(lilaMaven, sonashots),
    testFrameworks ++= List(
      new TestFramework("munit.Framework"),
      new TestFramework("zio.test.sbt.ZTestFramework")
    )
  )
  .settings(
    GraalVMSharedLib / graalVMNativeImageOptions := graalVMSharedLibOptions,
    // GraalVMSharedLib / containerBuildImage := Some("ghcr.io/graalvm/native-image-community:17-ol8")
  )
  .enablePlugins(GraalVMSharedLibPlugin)

lazy val root = (project in file("."))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name := "chess-finder",
    libraryDependencies ++= Dependencies.prod ++ Dependencies.tests,
    resolvers ++= Seq(lilaMaven, sonashots),
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "chessfinder",
    buildInfoObject  := "ChessfinderBuildInfo",
    git.useGitDescribe := true,
    scalacOptions := commonScalaOptions,
  )
  .settings(
    IntegrationTest / fork := true,
    IntegrationTest / javaOptions += "-Dconfig.file=src/it/resources/local.conf"
  )
  .dependsOn(testkit % Test)
  .dependsOn(core)

lazy val testkit = project
  .in(file("src_testkit"))
