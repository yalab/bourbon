import sbt._

import Keys._
import AndroidKeys._
import java.io.File
import scala.xml.XML

object General {
  val androidManifest = XML.loadFile((new File("")).getAbsoluteFile + "/src/main/AndroidManifest.xml")
  val versionCode = androidManifest(0) \ "@{http://schemas.android.com/apk/res/android}versionCode"
  val settings = Defaults.defaultSettings ++ Seq (
    name := "Bourbon",
    version := versionCode.toString,
    scalaVersion := "2.9.1",
    platformName in Android := "android-8"
  )

  lazy val fullAndroidSettings =
    General.settings ++
    AndroidProject.androidSettings ++
    TypedResources.settings ++
    AndroidMarketPublish.settings ++ Seq (
      keyalias in Android := "bourbon",
      libraryDependencies += "org.scalatest" %% "scalatest" % "1.6.1" % "test",
      libraryDependencies += "com.google.android" % "support-v4" % "r6"
    )
}

object AndroidBuild extends Build {
  lazy val main = Project (
    "Bourbon",
    file("."),
    settings = General.fullAndroidSettings
  )

  lazy val tests = Project (
    "tests",
    file("tests"),
    settings = General.settings ++ AndroidTest.androidSettings
  ) dependsOn main
}
