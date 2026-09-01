package sfenv

import cats.effect.*
import cats.effect.std.Console
import cats.syntax.all.*

import com.monovore.decline.*

import java.io.IOException
import java.nio.file.Path

import rules.Rules

object Main extends IOApp:

  def genEnvSqls(
      env: EnvName,
      rulesFile: Option[Path],
      diffFile: Option[Path],
      genDropCli: Option[GenDrop],
      genGrantCli: Option[GenGrant],
  ): IO[ExitCode] =
    for
      cRules <- Rules(rulesFile)
      pRules <- diffFile.traverse(Rules.apply)
      cEnv     = cRules.resolve(env)
      pEnv     = pRules.map(_.resolve(env))
      genDrop  = genDropCli.orElse(cRules.options.map(_.drop)).getOrElse(GenDrop.Local)
      genGrant = genGrantCli.orElse(cRules.options.map(_.genGrant)).getOrElse(GenGrant.All)
      sqls     = cEnv.genSqls(pEnv)(using genDrop, genGrant)
      _ <- sqls.traverse_(x => IO.println(x))
    yield ExitCode.Success

  def genAdminSqls(env: EnvName, rulesIO: IO[Rules]): IO[ExitCode] =
    for
      rules <- rulesIO
      _     <- IO.println(rules.resolve(env).adminRoleSqls)
    yield ExitCode.Success

  private val version = "0.3.0-RC4"

  private val helpText =
    val dropChoices = GenDrop.values.map(_.toString().toLowerCase()).mkString("|")

    s"""|Usage:
        |  sfenv [--env <string>] --admin-roles [<rules>]
        |  sfenv [--env <string>] [--diff <path>] [--gen-drop <choice>] [--only-future] [<rules-file>]
        |
        |Generate Snowflake DDL and DCL statements from Rule file
        |
        |Options:
        |  --help               Print help message and exit
        |  --version            Print application version ($version) and exit
        |
        |  -e, --env <string>   Set environment name (default $$SFENV or DEV)
        |  --admin-roles        Generate SQLs to create environment admin roles
        |  -d, --diff <rules>   Generate SQLs only for the differences when compared to this ruleset
        |  --gen-drop <choice>  Which DROP statements to generate without commenting: $dropChoices
        |  -F, --only-future    Generate grants for only FUTURE objects (skip GRANTS TO ALL)""".stripMargin

  def opts: Opts[IO[ExitCode]] =
    val env = Opts
      .option[String]("env", short = "e", help = "Environment name (default DEV)")
      .orElse(Opts.env[String]("SFENV", help = "Environment name (default DEV)"))
      .map(_.toUpperCase)
      .withDefault("DEV")

    val currRules = Opts.argument[Path]("rules-file").orNone

    val prevRules = Opts
      .option[Path](
        "diff",
        short = "d",
        help = "generate SQLs for only the differences when compared to this ruleset"
      )
      .orNone

    val adminCmd =
      val adminRoles = Opts.flag("admin-roles", help = "Generate SQLs to create environment admin roles")
      (env, currRules, adminRoles).mapN((e, c, _) => genAdminSqls(e, Rules(c)))

    val genDrop = Opts.option[GenDrop]("gen-drop", help = "DROP statements to comment out").orNone

    val getGrant = Opts
      .flag("futures-only", short = "F", help = "Generate grants for only FUTURE objects (skip ALL)")
      .map(_ => GenGrant.Future)
      .orNone

    val envCmd = (env, currRules, prevRules, genDrop, getGrant).mapN(genEnvSqls)

    val helpCmd = Opts
      .flag("help", help = "Print help message and exit")
      .map(_ => IO.println(colorizeHelp(helpText)).as(ExitCode.Success))

    val versionCmd = Opts
      .flag("version", help = "Print application version and exit")
      .map(_ => IO.println(version).as(ExitCode.Success))

    def showError(s: String) = Console[IO].errorln(s).as(ExitCode.Error)

    (helpCmd orElse versionCmd orElse adminCmd orElse envCmd).map:
      _.handleErrorWith:
        case e: IOException => showError(s"IO Error: ${e.getMessage()}")
        case e: AppError    => showError(e.getMessage())

  def run(args: List[String]): IO[ExitCode] =
    Command("sfenv", header = "", helpFlag = false)(opts).parse(args, sys.env) match
      case Right(fa) => fa
      case Left(_)   => Console[IO].errorln(colorizeHelp(helpText)).as(ExitCode.Error)
