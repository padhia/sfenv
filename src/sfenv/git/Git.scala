package sfenv
package git

import cats.effect.{IO, Resource}
import cats.syntax.all.*

import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters.*

import rules.Rules

/** Access to rules files as they exist at different states of a git repository.
  *
  * @param cwd
  *   invocation directory; relative paths are resolved against it
  * @param top
  *   repository root (real path)
  */
final class Git private (cwd: Path, top: Path):
  import Git.run

  /** Converts a relative (to cwd) or an absolute path to a repo-root relative path using `/` separators */
  def relPath(path: Path): IO[String] = IO
    .blocking:
      val abs    = cwd.resolve(path).toAbsolutePath.normalize
      val parent = Option(abs.getParent).filter(Files.exists(_)).map(_.toRealPath())
      val real   = parent.fold(abs)(_.resolve(abs.getFileName))
      if real.startsWith(top) then Right(top.relativize(real).iterator.asScala.mkString("/"))
      else Left(AppError.PathOutsideRepo(path))
    .rethrow

  private def objName(rev: Rev, rel: String) = rev match
    case Rev.Commit(spec) => s"$spec:$rel"
    case _                => s":$rel"

  def exists(rev: Rev, rel: String): IO[Boolean] = rev match
    case Rev.WorkDir      => IO.blocking(Files.isRegularFile(top.resolve(rel)))
    case Rev.Commit(spec) =>
      run(top, "rev-parse", "--verify", "--quiet", s"$spec^{commit}")
        .adaptError { case AppError.GitError(cmd, _) => AppError.GitError(cmd, s"'$spec' is not a valid commit") } >>
        run(top, "cat-file", "-e", objName(rev, rel)).as(true).recover { case _: AppError.GitError => false }
    case Rev.Index =>
      run(top, "cat-file", "-e", objName(rev, rel)).as(true).recover { case _: AppError.GitError => false }

  /** Full tree of the repository at a given state, extracted to a temporary directory */
  def snapshot(rev: Rev): Resource[IO, Path] = rev match
    case Rev.WorkDir => Resource.pure(top)
    case _           =>
      Git.tempDir.evalMap: tmp =>
        val tree = tmp.resolve("tree")
        val pfx  = s"${tree.toString.replace('\\', '/')}/"
        val env  = rev match
          case Rev.Commit(spec) =>
            val idx = tmp.resolve("index").toString
            run(top, "read-tree", s"--index-output=$idx", spec).as(Map("GIT_INDEX_FILE" -> idx))
          case _ => IO.pure(Map.empty[String, String])
        // parallel checkout using all cores; skip LFS downloads (rules files are not expected to be in LFS)
        val fastEnv = Map("GIT_LFS_SKIP_SMUDGE" -> "1")
        env
          .flatMap(e => run(top, e ++ fastEnv, "-c", "checkout.workers=0", "checkout-index", "--all", s"--prefix=$pfx"))
          .as(tree)

  /** Loads rules from a file at a given state; `None` if the file does not exist in that state */
  def load(rev: Rev, rel: String): IO[Option[Rules]] =
    exists(rev, rel).flatMap:
      case false                        => IO.none
      case true if rev == Rev.WorkDir   => Rules(top.resolve(rel)).map(_.some)
      case true if rel.endsWith(".pkl") || rel.endsWith(".k") =>
        snapshot(rev).use(dir => Rules(dir.resolve(rel))).map(_.some)
      case true => run(top, "show", objName(rev, rel)).flatMap(Rules.apply).map(_.some)

object Git:
  def apply(cwd: Path = Path.of("").toAbsolutePath): IO[Git] =
    run(cwd, "rev-parse", "--show-toplevel").flatMap: out =>
      IO.blocking(new Git(cwd.toAbsolutePath, Path.of(out.trim).toRealPath()))

  private[git] def run(dir: Path, args: String*): IO[String] = run(dir, Map.empty, args*)

  private[git] def run(dir: Path, env: Map[String, String], args: String*): IO[String] =
    val cmd = "git" :: "-C" :: dir.toString :: args.toList
    for
      proc <- IO.blocking:
        val pb = ProcessBuilder(cmd*)
        env.foreach((k, v) => pb.environment().put(k, v))
        pb.start()
      stdout <- IO.blocking(String(proc.getInputStream.readAllBytes()))
      stderr <- IO.blocking(String(proc.getErrorStream.readAllBytes()))
      exit   <- IO.blocking(proc.waitFor())
      _      <- IO.raiseWhen(exit != 0)(AppError.GitError(s"git ${args.mkString(" ")}", stderr.trim))
    yield stdout

  private def tempDir: Resource[IO, Path] =
    Resource.make(IO.blocking(Files.createTempDirectory("sfenv-")))(deleteTree)

  private[sfenv] def deleteTree(dir: Path): IO[Unit] = IO.blocking:
    val s = Files.walk(dir)
    try s.iterator.asScala.toList.reverse.foreach(Files.deleteIfExists)
    finally s.close()
