ThisBuild / organization := "unknown"
ThisBuild / version      := "0.1"
ThisBuild / scalaVersion := "3.2.1"
// ThisBuild / licenses += "AGPL-3.0" -> url("https://opensource.org/licenses/AGPL-3.0")

ThisBuild / resolvers += "lila-maven" at "https://raw.githubusercontent.com/ornicar/lila-maven/master"
ThisBuild / resolvers += "jitpack" at "https://jitpack.io"

val lilaMaven = "lila-maven" at "https://raw.githubusercontent.com/lichess-org/lila-maven/master"
val sonashots = "sonashots" at "https://oss.sonatype.org/content/repositories/snapshots"

lazy val root = (project in file("."))
  .settings(
    name := "chess-finder",
    libraryDependencies ++= List(
      "org.lichess"    %% "scalachess"       % "14.5.4",
      "org.lichess"    %% "compression"      % "1.8",
      "com.github.ornicar"  %% "scalalib"          % "9.1.2",
      "joda-time"            % "joda-time"         % "2.12.2",
      "org.typelevel"       %% "cats-core"         % "2.9.0",
      "org.typelevel"       %% "alleycats-core"    % "2.9.0",
      "org.typelevel"       %% "cats-parse"        % "0.3.9",
      "org.specs2"     %% "specs2-core"      % "4.19.0"   % Test,
      "org.specs2"     %% "specs2-cats"      % "4.19.0"   % Test,
      "org.scalameta"  %% "munit"            % "1.0.0-M7" % Test,
      "org.scalacheck" %% "scalacheck"       % "1.17.0"   % Test,
      "org.scalameta"  %% "munit-scalacheck" % "1.0.0-M7" % Test,
      "com.disneystreaming" %% "weaver-cats"       % "0.8.1" % Test,
      "com.disneystreaming" %% "weaver-scalacheck" % "0.8.1" % Test
    ),
    testFrameworks ++= List(
      new TestFramework("weaver.framework.CatsEffect"),
      new TestFramework("munit.Framework")
    ),
    scalacOptions := Seq(
      "-encoding",
      "utf-8",
      // "-rewrite",
      "-source:future-migration",
      "-indent",
      "-explaintypes",
      "-feature",
      "-language:postfixOps"
      // Warnings as errors!
      /* "-Xfatal-warnings" */
    ),
      resolvers ++= Seq(lilaMaven, sonashots)
  )
