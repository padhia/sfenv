package sfenv
package git

import cats.effect.unsafe.implicits.global

import java.nio.file.{Files, Path}

import munit.FunSuite
import rules.Rules

class GitSuite extends FunSuite:
  private def sh(dir: Path, args: String*) =
    val cmd = Seq("git", "-c", "user.name=t", "-c", "user.email=t@t", "-c", "commit.gpgsign=false") ++ args
    val p   = ProcessBuilder(cmd*).directory(dir.toFile).redirectErrorStream(true).start()
    val out = String(p.getInputStream.readAllBytes())
    assertEquals(p.waitFor(), 0, out)

  private def yaml(db: String) = s"databases:\n  $db:\n    schemas: {}\n"

  private def write(dir: Path, rel: String, content: String) =
    val f = dir.resolve(rel)
    Files.createDirectories(f.getParent)
    Files.writeString(f, content)

  // repo layout: HEAD~1 -> DB1, HEAD -> DB2, index -> DB3, working dir -> DB4
  private val repo = FunFixture[Path](
    setup = _ =>
      val dir = Files.createTempDirectory("sfenv-gitsuite-")
      sh(dir, "init", "-q")
      write(dir, "sub/rules.yaml", yaml("DB1"))
      write(dir, "sub/defaults.pkl", "comment = \"v1\"\n")
      write(dir, "sub/rules.pkl", "import \"defaults.pkl\"\ndatabases { DBP { comment = defaults.comment; schemas {} } }\n")
      sh(dir, "add", ".")
      sh(dir, "commit", "-q", "-m", "c1")
      write(dir, "sub/rules.yaml", yaml("DB2"))
      write(dir, "sub/defaults.pkl", "comment = \"v2\"\n")
      sh(dir, "commit", "-q", "-am", "c2")
      write(dir, "sub/rules.yaml", yaml("DB3"))
      sh(dir, "add", ".")
      write(dir, "sub/rules.yaml", yaml("DB4"))
      write(dir, "sub/new.yaml", yaml("NEW"))
      dir
    ,
    teardown = dir => Git.deleteTree(dir).unsafeRunSync()
  )

  private def dbs(r: Option[Rules]) = r.map(_.databases.toList.map(_.name))

  repo.test("loads the file at each state"): dir =>
    val git          = Git(dir.resolve("sub")).unsafeRunSync()
    def at(rev: Rev) = dbs(git.load(rev, "sub/rules.yaml").unsafeRunSync())
    assertEquals(at(Rev.Commit("HEAD~1")), Some(List("DB1")))
    assertEquals(at(Rev.Commit("HEAD")), Some(List("DB2")))
    assertEquals(at(Rev.Index), Some(List("DB3")))
    assertEquals(at(Rev.WorkDir), Some(List("DB4")))

  repo.test("file missing at a state loads as None"): dir =>
    val git = Git(dir).unsafeRunSync()
    assertEquals(git.load(Rev.Commit("HEAD"), "sub/new.yaml").unsafeRunSync(), None)
    assertEquals(git.load(Rev.Index, "sub/new.yaml").unsafeRunSync(), None)
    assertEquals(dbs(git.load(Rev.WorkDir, "sub/new.yaml").unsafeRunSync()), Some(List("NEW")))

  repo.test("invalid commit is an error"): dir =>
    val git = Git(dir).unsafeRunSync()
    intercept[AppError.GitError](git.load(Rev.Commit("no-such-rev"), "sub/rules.yaml").unsafeRunSync())

  repo.test("relative and absolute paths map to the same repo-relative path"): dir =>
    val git = Git(dir.resolve("sub")).unsafeRunSync()
    assertEquals(git.relPath(Path.of("rules.yaml")).unsafeRunSync(), "sub/rules.yaml")
    assertEquals(git.relPath(dir.resolve("sub/rules.yaml")).unsafeRunSync(), "sub/rules.yaml")
    assertEquals(git.relPath(Path.of("../sub/x/../rules.yaml")).unsafeRunSync(), "sub/rules.yaml")

  repo.test("path outside the repository is an error"): dir =>
    val git = Git(dir).unsafeRunSync()
    intercept[AppError.PathOutsideRepo](git.relPath(dir.resolveSibling("elsewhere.yaml")).unsafeRunSync())

  repo.test("pkl relative imports resolve at the same commit"): dir =>
    val git               = Git(dir.resolve("sub")).unsafeRunSync()
    def comment(rev: Rev) =
      git.load(rev, "sub/rules.pkl").unsafeRunSync().flatMap(_.databases.headOption).flatMap(_.comment)
    assertEquals(comment(Rev.Commit("HEAD~1")), Some(SqlLiteral("v1")))
    assertEquals(comment(Rev.Index), Some(SqlLiteral("v2")))

  repo.test("snapshot temp dir is removed and repo metadata untouched"): dir =>
    val git  = Git(dir).unsafeRunSync()
    val tree = git.snapshot(Rev.Commit("HEAD~1")).use(p => cats.effect.IO(p)).unsafeRunSync()
    assert(!Files.exists(tree.getParent), s"$tree not removed")
    assert(!Files.exists(dir.resolve(".git/worktrees")))
