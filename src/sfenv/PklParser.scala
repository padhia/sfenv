package sfenv

import cats.effect.IO

import java.nio.file.Path

import fabric.Json

object PklParser:
  def apply(path: Path): IO[Json] =
    for
      proc   <- IO.blocking(ProcessBuilder("pkl", "eval", "--format", "json", path.toString).start())
      stdout <- IO.blocking(String(proc.getInputStream.readAllBytes()))
      json   <- YamlParser(stdout)
    yield json
