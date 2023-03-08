import sbt.*
import sbt.Keys.*

object EndToEndSettings {

  lazy val EndToEndTest = config("E2e").extend(Test)

  lazy val e2eSettings: Seq[Def.Setting[_]] =
    inConfig(EndToEndTest)(Defaults.testSettings) ++
      Seq(
        EndToEndTest / fork := false,
        EndToEndTest / parallelExecution := false,
        EndToEndTest / scalaSource := baseDirectory.value / "src/e2e/scala")


}