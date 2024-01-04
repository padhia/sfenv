import mill._
import mill.scalalib._
import mill.scalalib.publish._

object Ver {
  val circeParser = "0.14.6"
  val circeYaml   = "0.15.1"
  val decline     = "2.4.1"
  val fs2         = "3.9.3"
  val munit       = "1.0.0-M10"
}

object sfenv extends RootModule with ScalaModule with PublishModule {
  def scalaVersion   = "3.3.1"
  def publishVersion = "0.1.0"
  def scalacOptions  = Seq("-deprecation", "-Wunused:all", "-release", "11")
  def artifactName   = "sfenv"
  def mainClass      = Some("sfenv.Main")

  def ivyDeps = Agg(
    ivy"com.monovore::decline-effect:${Ver.decline}",
    ivy"io.circe::circe-parser:${Ver.circeParser}",
    ivy"io.circe::circe-yaml:${Ver.circeYaml}",
    ivy"co.fs2::fs2-io:${Ver.fs2}"
  )

  def pomSettings = PomSettings(
    description = "Manage Snowflake environments declaratively",
    organization = "org.padhia",
    url = "https://github.com/padhia/sfenv",
    licenses = Seq(License.MIT),
    versionControl = VersionControl.github("padhia", "sfenv"),
    developers = Seq(Developer("padhia", "Paresh Adhia", "https://github.com/padhia"))
  )

  object test extends ScalaTests with TestModule.Munit {
    def ivyDeps = Agg(ivy"org.scalameta::munit:${Ver.munit}")
  }
}
