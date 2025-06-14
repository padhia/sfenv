package sfenv
package rules

import io.circe.*

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
  given Decoder[Namespace] = Decoder.decodeString.emapTry: x =>
    x.split("\\.") match
      case Array(db, sch) => Success(Schema(db, sch))
      case Array(wh)      => Success(Database(wh))
      case _              => Failure(new Throwable(show"Invalid namespace '$x'; must be either <db> or <db>.<sch>"))
