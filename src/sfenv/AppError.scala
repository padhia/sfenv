package sfenv

import java.nio.file.Path

import scala.util.control.NoStackTrace

enum AppError(msg: String) extends Exception(msg) with NoStackTrace:
  case FileNotFound(path: Path) extends AppError(s"File '$path' does not exists")
  case RulesParsingError(msg: String, path: Option[Path] = None)
      extends AppError(s"Error parsing ${path.map(_.toString()).getOrElse("stdin")}, $msg")
