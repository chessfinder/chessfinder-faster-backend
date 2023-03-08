import Dependencies.*

lazy val `testkit` = project
  .in(file("."))
  .settings(
    libraryDependencies ++= wiremock ++ zio
  )
