package sfenv

import cats.effect.IO

import java.nio.file.Path

import fabric.Json

object PklParser:
  // Registered as the search root for `modulepath:` imports, letting Pkl rules files resolve
  // shared imports (e.g. `import "modulepath:/defaults.pkl"`) by the invocation's working
  // directory rather than the location of the (possibly relocated, e.g. by `git difftool`) file
  // being evaluated. Has no effect on files that don't use the `modulepath:` scheme.
  private val cwd = Path.of("").toAbsolutePath.toString

  def apply(path: Path): IO[Json] =
    for
      proc <- IO.blocking(
                ProcessBuilder("pkl", "eval", "--format", "json", "--module-path", cwd, path.toString).start()
              )
      stdout <- IO.blocking(String(proc.getInputStream.readAllBytes()))
      stderr <- IO.blocking(String(proc.getErrorStream.readAllBytes()))
      exit   <- IO.blocking(proc.waitFor())
      _      <- IO.raiseWhen(exit != 0)(AppError.RulesParsingError(stderr.trim, Some(path)))
      json   <- YamlParser(stdout)
    yield json
