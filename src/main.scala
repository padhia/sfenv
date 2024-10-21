package sfenv

import fs2.Stream
import fs2.io.file.{Files, Path}
import fs2.io.stdinUtf8

import cats.effect.*
import cats.effect.std.Console
import cats.syntax.all.*

import com.monovore.decline.*
import com.monovore.decline.effect.*

import java.io.IOException
import java.nio.file.FileSystemException

import envr.SfEnv

object Main
    extends CommandIOApp(
      name = "sfenv",
      version = "0.1.0",
      header = "Generate SQLs for declaratively managed Snowflake environments"
    ):

  extension (s: String)
    def toRbac(env: String): Either[Error, SfEnv] = parse(s).flatMap(Decoder[rules.Rules].decodeJson(_)).map(_.resolve(env))

  def makeRbacPair(curr: String, prev: Option[String], env: EnvName = "DEV"): Either[Error, (SfEnv, Option[SfEnv])] =
    for
      c <- curr.toRbac(env)
      p <- prev.traverse(_.toRbac(env))
    yield (c, p)

  def genEnvSqls(
      env: EnvName,
      curr: Stream[IO, String],
      prev: Option[Stream[IO, String]],
      onlyFuture: Boolean,
      drops: ProcessDrops
  ): IO[ExitCode] =
    val rbacs: IO[(SfEnv, Option[SfEnv])] =
      for
        c  <- curr.compile.string
        p  <- prev.traverse(_.compile.string)
        cp <- IO.fromEither(makeRbacPair(c, p, env))
      yield cp

    Stream
      .eval(rbacs)
      .flatMap((curr, prev) => curr.genSqls(prev, onlyFuture, drops))
      .map(println)
      .compile
      .drain
      .as(ExitCode.Success)

  def genAdminSqls(env: EnvName, rulesText: Stream[IO, String]) =
    for
      rules <- rulesText.compile.string
      rbac  <- IO.fromEither(rules.toRbac(env))
      _     <- IO.println(rbac.adminRoleSqls)
    yield ExitCode.Success

  def handleErrors(x: IO[ExitCode]) =
    def showError(s: String) = Console[IO].errorln(s).as(ExitCode.Error)

    x.handleErrorWith {
      case e: FileSystemException => showError(s"Error Opening File: ${e.getFile()}")         // input file couldn't be opened
      case e: IOException         => showError(s"IO Error: ${e.getMessage()}")                // other generic IO errors
      case e: Error               => showError(s"YAML/JSON Parsing Error: ${e.getMessage()}") // Circe errors
    }

  def main: Opts[IO[ExitCode]] =
    given Argument[Stream[IO, String]] = Argument.from("rules-file")(s => Files[IO].readUtf8(Path(s)).validNel)

    val env = Opts
      .option[String]("env", short = "e", help = "Environment name (default DEV)")
      .orElse(Opts.env[String]("SFENV", help = "Environment name (default DEV)"))
      .map(_.toUpperCase)
      .withDefault("DEV")
    val currRules = Opts.argument[Stream[IO, String]]("rules-file").withDefault(stdinUtf8[IO](64 * 1024))
    val prevRules = Opts
      .option[Stream[IO, String]](
        "diff",
        short = "d",
        help = "generate SQLs for only the differences when compared to this ruleset"
      )
      .orNone

    val adminRoles = Opts.flag("admin-roles", help = "Generate SQLs to create environment admin roles")

    val onlyFuture = Opts.flag("only-future", short = "F", help = "Generate grants for only FUTURE objects (no ALL)").orFalse
    val drops = Opts
      .option[ProcessDrops]("drop", short = "D", help = "process DROP statements (default: non-local)")
      .withDefault(ProcessDrops.NonLocal)

    val adminCmd = (env, currRules, adminRoles).mapN((e, c, _) => genAdminSqls(e, c))
    val envCmd   = (env, currRules, prevRules, onlyFuture, drops).mapN(genEnvSqls)

    adminCmd.orElse(envCmd).map(handleErrors(_))
