import sbt.*
import sbt.{ Def, _ }
import sbt.Keys._

enablePlugins(GitPlugin)
enablePlugins(GitVersioning)

ThisBuild / idePackagePrefix := Some("chessfinder")
ThisBuild / organization := "eudemonia"
// ThisBuild / version           := "0.1"
ThisBuild / git.useGitDescribe := true
ThisBuild / scalaVersion       := "3.3.0"
ThisBuild / semanticdbEnabled  := true // enable SemanticDB

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

lazy val core = project
  .in(file("src_core"))
  .settings(
    name := "chess-finder-core",
    libraryDependencies ++= Dependencies.scalachess ++ Dependencies.`chessfinder-core-tests`,
    scalacOptions := commonScalaOptions,
    resolvers ++= Seq(lilaMaven, sonashots),
    testFrameworks ++= List(
      new TestFramework("munit.Framework")
    )
  )

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
    testFrameworks ++= List(
      new TestFramework("munit.Framework")
    )
  )
  .settings(
    assembly / assemblyJarName := "chessfinder-lambda.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "maven", _*)                                       => MergeStrategy.concat
      case PathList("META-INF", "io.netty.versions.properties", _*)                => MergeStrategy.first
      case PathList(ps @ _*) if ps.last contains "FlowAdapters"                    => MergeStrategy.first
      case PathList(ps @ _*) if ps.last == "module-info.class"                     => MergeStrategy.first
      case _ @("scala/annotation/nowarn.class" | "scala/annotation/nowarn$.class") => MergeStrategy.first
      case PathList("deriving.conf") => MergeStrategy.concat // FIXME get rid of zio.json
      case PathList(path @ _*) if path.exists(_.contains("module-info.class")) => MergeStrategy.first
      case x =>
        val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
        oldStrategy(x)
    }
  )
  .dependsOn(core)
