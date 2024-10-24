package sfenv
package rules

import envr.{ObjMeta, Props}
import fabric.*
import fabric.define.DefType
import fabric.rw.*

case class Database(x: Database.Aux, props: Props):
  export x.*

  def resolve(dbName: String)(using n: NameResolver) =
    envr.Database(
      name = n.db(dbName),
      transient = transient.getOrElse(false),
      meta = ObjMeta(props, tags, comment),
      schemas = schemas.getOrElse(Map.empty).map((schName, x) => x.resolve(dbName, schName)).toList
    )

object Database:
  case class Aux(transient: Option[Boolean], schemas: Option[Map[String, Schema]], tags: Tags, comment: Comment) derives RW

  def fromJson(doc: Json) = Database(summon[RW[Aux]].write(doc), props = Util.extraElems[Aux](doc))

  given RW[Database] = RW.from(x => summon[RW[Aux]].read(x.x), fromJson, DefType.Json)
