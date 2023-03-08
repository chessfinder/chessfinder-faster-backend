import sbt.*
import scala.language.dynamics

object modules extends Dynamic {
  
  def selectDynamic(name: String): ProjectRef = ProjectRef(file("."), name)

}
