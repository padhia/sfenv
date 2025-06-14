package sfenv

import io.circe.Error

import cats.effect.*
import cats.effect.std.Console
import cats.syntax.all.*

import com.monovore.decline.*
import com.monovore.decline.effect.*

import java.io.IOException
import java.nio.file.{Files, Path}
import java.nio.file.FileSystemException

import scala.io.Source

import rules.{Options, Rules}

object Main
    extends CommandIOApp(
      name = "sfenv",
      version = "0.2.0-rc.1",
      header = "Generate SQLs for declaratively managed Snowflake environments"
    ):

  def genEnvSqls(
      env: EnvName,
      currIO: IO[String],
      prevIO: IO[Option[String]],
      opts: Options,
  ): IO[ExitCode] =
    for
      cText  <- currIO
      cRules <- IO.fromEither(Rules(cText))
      pText  <- prevIO
      pRules <- IO.fromEither(pText.traverse(Rules(_)))
      cEnv    = cRules.resolve(env)
      pEnv    = pRules.map(_.resolve(env))
      genOpts = SqlGenOptions.coalesce(opts, cRules.options.getOrElse(Options()))
      sqls    = cEnv.genSqls(pEnv)(using genOpts)
      _ <- sqls.traverse_(x => IO.println(x))
    yield ExitCode.Success

  def genAdminSqls(env: EnvName, rulesIO: IO[String]): IO[ExitCode] =
    for
      rules <- rulesIO
      rbac  <- IO.fromEither(Rules(rules).map(_.resolve(env)))
      _     <- IO.println(rbac.adminRoleSqls)
    yield ExitCode.Success

  def main: Opts[IO[ExitCode]] =
    val env = Opts
      .option[String]("env", short = "e", help = "Environment name (default DEV)")
      .orElse(Opts.env[String]("SFENV", help = "Environment name (default DEV)"))
      .map(_.toUpperCase)
      .withDefault("DEV")

    val currRules = Opts
      .argument[Path]("rules-file")
      .map(p => IO.delay(Files.readString(p)))
      .withDefault(IO.delay(Source.stdin.getLines().mkString("\n")))

    val prevRules = Opts
      .option[Path](
        "diff",
        short = "d",
        help = "generate SQLs for only the differences when compared to this ruleset"
      )
      .orNone
      .map(_.traverse(p => IO.delay(Files.readString(p))))

    val adminRoles = Opts.flag("admin-roles", help = "Generate SQLs to create environment admin roles")

    val options =
      val processDrops =
        val dropAll      = Opts.flag("drop-all", help = "do not comment any DROP statements").map(_ => ProcessDrops.All)
        val dropNone     = Opts.flag("drop-none", help = "comment all DROP statements").map(_ => ProcessDrops.Never)
        val dropNonLocal =
          Opts.flag("drop-none", help = "comment only local DROP statements (default)").map(_ => ProcessDrops.NonLocal)
        dropAll.orElse(dropNone).orElse(dropNonLocal).orNone
      val onlyFuture = Opts
        .flag("only-future", short = "F", help = "Generate grants for only FUTURE objects (no ALL)")
        .map(_ => true)
        .orNone
      (processDrops, onlyFuture).mapN(Options.apply)

    val adminCmd = (env, currRules, adminRoles).mapN((e, c, _) => genAdminSqls(e, c))
    val envCmd   = (env, currRules, prevRules, options).mapN(genEnvSqls)

    adminCmd
      .orElse(envCmd)
      .map: prog =>
        def showError(s: String) = Console[IO].errorln(s).as(ExitCode.Error)
        prog.handleErrorWith:
          case e: FileSystemException => showError(s"Error Opening File: ${e.getFile()}")         // input file couldn't be opened
          case e: IOException         => showError(s"IO Error: ${e.getMessage()}")                // other generic IO errors
          case e: Error               => showError(s"YAML/JSON Parsing Error: ${e.getMessage()}") // Circe errors
