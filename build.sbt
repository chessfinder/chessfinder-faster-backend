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

// ThisProject / LatestTag.gitLatestTag =

// ThisBuild / licenses += "AGPL-3.0" -> url("https://opensource.org/licenses/AGPL-3.0")

// ThisBuild / versionScheme := Some("semver-spec")

val lilaMaven = "lila-maven" at "https://raw.githubusercontent.com/lichess-org/lila-maven/master"
val sonashots = "sonashots" at "https://oss.sonatype.org/content/repositories/snapshots"

lazy val DeepIntegrationTest = IntegrationTest.extend(Test)

lazy val DeepIntegrationSettings: Seq[Def.Setting[_]] =
  inConfig(DeepIntegrationTest)(Defaults.testSettings) ++
    Seq(
      DeepIntegrationTest / fork              := false,
      DeepIntegrationTest / parallelExecution := false,
      DeepIntegrationTest / scalaSource       := baseDirectory.value / "src/it/scala"
    )


lazy val core = project
  .in(file("src_core"))
  .settings(
    name := "chess-finder-core",
    libraryDependencies ++= Dependencies.scalachess ++ Dependencies.`chessfinder-core-tests`,
    libraryDependencies += "org.graalvm.sdk" % "graal-sdk" % "23.1.0" % "provided",
    scalacOptions := Seq(
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
    ),
    resolvers ++= Seq(lilaMaven, sonashots),
    testFrameworks ++= List(
      new TestFramework("munit.Framework"),
      new TestFramework("zio.test.sbt.ZTestFramework")
    )
  )
  .settings(
    // GraalVMSharedLib / containerBuildImage := Some("ghcr.io/graalvm/native-image-community:17-ol8"),
    GraalVMSharedLib / containerBuildImage := GraalVMNativeImagePlugin.generateContainerBuildImage("goodforgod/amazonlinux-graalvm:23.0.0-java17-amd64").value,

    GraalVMSharedLib /
      graalVMNativeImageOptions := Seq(
//      "--shared",
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
  )
  .enablePlugins(GraalVMSharedLibPlugin)

lazy val root = (project in file("."))
  .configs(DeepIntegrationTest)
  .settings(DeepIntegrationSettings)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "chessfinder",
    buildInfoObject  := "ChessfinderBuildInfo",
  )
  // .settings(version := "v11.3.3")
  .settings(git.useGitDescribe := true)
  .settings(
  )
  .settings(
    name := "chess-finder",
    libraryDependencies ++= Dependencies.prod ++ Dependencies.tests,
    scalacOptions := Seq(
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
    ),
    resolvers ++= Seq(lilaMaven, sonashots)
  )
  .settings(
    IntegrationTest / fork := true,
    IntegrationTest / javaOptions += "-Dconfig.file=src/it/resources/local.conf"
  )
  // .settings(
  //   GraalVMNativeImage / mainClass := Some("chessfinder.LambdaMain"),
  //   GraalVMNativeImage / containerBuildImage := GraalVMNativeImagePlugin
  //     .generateContainerBuildImage(
  //       "hseeberger/scala-sbt:graalvm-ce-21.3.0-java17_1.6.2_3.1.1"
  //     )
  //     .value,
  //   // GraalVMNativeImage / containerBuildImage := Some("ghcr.io/graalvm/graalvm-ce:ol7-java11-22.3.1"),
  //   graalVMNativeImageOptions := Seq(
  //     "--verbose",
  //     "--no-fallback",
  //     "--install-exit-handlers",
  //     "--enable-http",
  //     "--allow-incomplete-classpath",
  //     "--report-unsupported-elements-at-runtime",
  //     "--initialize-at-run-time=io.netty",
  //     "--trace-class-initialization=io.netty.util.AbstracferenceCounted",
  //     "-H:+StaticExecutableWithDynamicLibC",
  //     "-H:+RemoveSaturatedTypeFlows",
  //     "-H:+ReportExceptionStackTraces",
  //     "-H:TraceClassInitialization=io.netty.util.AbstracferenceCounted",
  //     "-H:+PrintClassInitialization"
  //   )
  // )
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
  .dependsOn(testkit % Test)
  .dependsOn(core)

lazy val testkit = project
  .in(file("src_testkit"))
