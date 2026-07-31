package sfenv
package rules

import fabric.*
import fabric.define.DefType
import fabric.rw.{RW, RWException}

import cats.syntax.all.*

import scala.util.*

enum Namespace:
  case Schema(db: String, sch: String)
  case Database(db: String)

  def resolve(using n: NameResolver): PropVal =
    this match
      case Schema(db, sch) => PropVal((n.db(db), n.sch(db, sch)))
      case Database(db)    => PropVal(n.db(db))

object Namespace:
  given RW[Namespace] = RW.from(
    r = ns => str(ns match
      case Schema(db, sch) => s"$db.$sch"
      case Database(db)    => db
    ),
    w = j => {
      val x = j.asString
      (x.split("\\.") match
        case Array(db, sch) => Success(Schema(db, sch))
        case Array(wh)      => Success(Database(wh))
        case _              => Failure(RuntimeException(show"Invalid namespace '$x'; must be either <db> or <db>.<sch>"))
      ) match
        case Success(v) => v
        case Failure(e) => throw RWException(e.getMessage)
    },
    d = DefType.Str
  )
