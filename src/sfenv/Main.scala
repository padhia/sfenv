package sfenv

import cats.effect.*
import cats.effect.std.Console
import cats.syntax.all.*

import com.monovore.decline.*
import com.monovore.decline.effect.CommandIOApp

import java.io.IOException
import java.nio.file.Path

import git.{Git, Rev}
import rules.Rules

/** A place to read rules from */
enum RulesSource:
  case Stdin
  case NativePath(path: Path)
  case GitPath(path: Path, rev: Rev)

  /** Loads rules; `None` only when a git path does not exist at the given revision */
  def load: IO[Option[Rules]] = this match
    case Stdin              => Rules(None).map(_.some)
    case NativePath(path)   => Rules(path).map(_.some)
    case GitPath(path, rev) => Git().flatMap(g => g.relPath(path).flatMap(g.load(rev, _)))

  /** Loads rules that must exist */
  def loadRequired: IO[Rules] = this match
    case GitPath(path, _) => load.flatMap(IO.fromOption(_)(AppError.FileNotFound(path)))
    case _                => load.map(_.get)

object Main
    extends CommandIOApp(
      name = "sfenv",
      header = "Generate Snowflake DDL and DCL statements from Rule file",
      version = "0.4.0-M1"
    ):
  def genAdminSqls(env: EnvName, rules: Rules): IO[Unit] =
    IO.println(rules.resolve(env).adminRoleSqls)

  private def handled(io: IO[Unit]): IO[ExitCode] =
    def showError(s: String) = Console[IO].errorln(s).as(ExitCode.Error)
    io.as(ExitCode.Success)
      .handleErrorWith:
        case e: IOException => showError(s"IO Error: ${e.getMessage()}")
        case e: AppError    => showError(e.getMessage())

  private def setQueryTag(tag: Option[String]): IO[Unit] =
    tag.traverse_(t => IO.println(show"ALTER SESSION SET QUERY_TAG = ${SqlLiteral(t)};\n"))

  /** Generates RBAC SQLs for the target rules, as differences from the base rules when given */
  def rbacSqls(
      env: EnvName,
      target: RulesSource,
      base: Option[RulesSource],
      genDropCli: Option[GenDrop],
      genGrantCli: Option[GenGrant],
      queryTag: Option[String],
  ): IO[ExitCode] =
    handled:
      for
        cRules <- target.loadRequired
        pRules <- base.flatTraverse(_.load)
        cEnv     = cRules.resolve(env)
        pEnv     = pRules.map(_.resolve(env))
        genDrop  = genDropCli.orElse(cRules.options.map(_.drop)).getOrElse(GenDrop.Local)
        genGrant = genGrantCli.orElse(cRules.options.map(_.genGrant)).getOrElse(GenGrant.All)
        sqls     = cEnv.genSqls(pEnv)(using genDrop, genGrant)
        _ <- (setQueryTag(queryTag) >> sqls.traverse_(x => IO.println(x))).whenA(!sqls.isEmpty)
      yield ()

  def adminSqls(env: EnvName, file: Option[Path], queryTag: Option[String]): IO[ExitCode] =
    handled(Rules(file).flatMap(rules => setQueryTag(queryTag) >> genAdminSqls(env, rules)))

  def opts: Opts[IO[ExitCode]] =
    val env = Opts
      .option[String]("env", short = "e", help = "Environment name (default DEV)")
      .orElse(Opts.env[String]("SFENV", help = "Environment name (default DEV)"))
      .map(_.toUpperCase)
      .withDefault("DEV")

    val genDrop = Opts.option[GenDrop]("gen-drop", help = "DROP statements to comment out").orNone

    val onlyNew = Opts
      .flag("only-new", short = "N", help = "Generate grants for only FUTURE objects (skip ALL)")
      .map(_ => GenGrant.Future)
      .orNone

    val queryTag = Opts
      .option[String]("query-tag", short = "T", help = "Set QUERY_TAG value for the session")
      .orElse(Opts.env[String]("SFENV_QUERY_TAG", help = "Set $QUERY_TAG unless --query-tag option is specified"))
      .orNone

    val currRules = Opts.argument[Path]("rules-file").orNone

    val baseRules = Opts.option[Path]("diff", short = "d", help = "Previous rules file to diff against (source)").orNone

    val envCmd =
      val target = currRules.map(_.fold(RulesSource.Stdin)(RulesSource.NativePath(_)))
      val base   = baseRules.map(_.map(RulesSource.NativePath(_)))
      (env, target, base, genDrop, onlyNew, queryTag).mapN(rbacSqls)

    val diffCmd = Opts.subcommand("diff", "Generate SQLs for differences between git states"):
      val commit   = Opts.argument[String]("commit")
      val fileName = Opts.argument[Path]("path")

      val case1 = (commit, commit, fileName).mapN((x, y, f) => (f, Rev.Commit(x), Rev.Commit(y)))
      val case2 = (commit, fileName).mapN((x, f) => (f, Rev.Commit(x), Rev.WorkDir))
      val case3 = fileName.map((_, Rev.Index, Rev.WorkDir))

      (env, case1 orElse case2 orElse case3, genDrop, onlyNew, queryTag).mapN:
        case (e, (f, from, to), d, g, q) =>
          rbacSqls(e, RulesSource.GitPath(f, to), Some(RulesSource.GitPath(f, from)), d, g, q)

    val adminCmd = Opts.subcommand("admin", "Generate SQLs to create environment admin roles", helpFlag = false):
      (env, currRules, queryTag).mapN(adminSqls)

    diffCmd orElse adminCmd orElse envCmd

  override def main: Opts[IO[ExitCode]] = opts
