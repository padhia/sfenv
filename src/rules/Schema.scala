package sfenv
package rules

import envr.{ObjMeta, Props}
import fabric.*
import fabric.define.DefType
import fabric.rw.*

case class Schema(x: Schema.Aux, props: Props):
  export x.*
  def resolve(dbName: String, schName: String)(using n: NameResolver) =
    envr.Schema(
      name = n.sch(dbName, schName),
      transient = transient.getOrElse(false),
      managed = managed.getOrElse(false),
      meta = ObjMeta(props, tags, comment),
      accRoles = acc_roles.map(_.resolve(dbName, schName)).getOrElse(Map.empty)
    )

object Schema:
  case class Aux(
      transient: Option[Boolean],
      managed: Option[Boolean],
      acc_roles: Option[AccRoles],
      tags: Tags,
      comment: Comment
  ) derives RW

  def fromJson(x: Json) = Schema(summon[RW[Aux]].write(x), props = Util.extraElems[Aux](x))

  given RW[Schema] = RW.from(x => summon[RW[Aux]].read(x.x), fromJson, DefType.Json)
