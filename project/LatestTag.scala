import scala.util.matching.Regex

import com.github.sbt.git.SbtGit.GitKeys

import sbt._
import Keys._
import sys.process.Process

object LatestTag {

  def apply(describeResult: String, fullHash: String): Option[String] = {
    val pattern     = "(?<version>.*)\\-[\\d]\\-g(?<shortHash>[0-9a-f]*)".r
    val matchOption = pattern.findFirstMatchIn(describeResult)
    val version     = matchOption.map(_.group("version"))
    val shortHash   = matchOption.map(_.group("shortHash"))
    (version, shortHash) match {
      case (Some(version), Some(shortHash)) if fullHash.contains(shortHash) => Some(version)
      case (Some(_), _)                                                     => Some(describeResult)
      case _ if describeResult.nonEmpty                                     => Some(describeResult)
      case _                                                                => None
    }

  }

    val gitLatestTag = SettingKey[Option[String]]("git-latest-tag", "Version as returned by `git describe --tags`.")

}
