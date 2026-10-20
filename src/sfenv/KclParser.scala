package sfenv

import cats.effect.IO

import java.nio.file.Path

import fabric.Json

object KclParser:
  def apply(path: Path): IO[Json] =
    for
      proc <- IO.blocking(
        ProcessBuilder("kcl", "run", "--format", "json", path.toString).start()
      )
      stdout <- IO.blocking(String(proc.getInputStream.readAllBytes()))
      stderr <- IO.blocking(String(proc.getErrorStream.readAllBytes()))
      exit   <- IO.blocking(proc.waitFor())
      _      <- IO.raiseWhen(exit != 0)(AppError.RulesParsingError(stderr.trim, Some(path)))
      json   <- YamlParser(stdout)
    yield json
