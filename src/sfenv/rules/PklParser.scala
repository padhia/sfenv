package sfenv
package rules

import cats.effect.IO

import java.nio.file.Path

import scala.util.Try

import fabric.Json
import fabric.io.*

object PklParser:
  def apply(path: Path): IO[Json] =
    for
      proc   <- IO.blocking(ProcessBuilder("pkl", "eval", "--format", "json", path.toString).start())
      stdout <- IO.blocking(String(proc.getInputStream.readAllBytes()))
      json   <- IO.fromTry(Try(JsonParser(stdout, Format.Json)))
    yield json
