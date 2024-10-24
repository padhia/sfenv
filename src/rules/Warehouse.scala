package sfenv
package rules

import fabric.*
import fabric.define.DefType
import fabric.rw.*

import envr.{ObjMeta, Props}

case class Warehouse(x: Warehouse.Aux, props: Props):
  export x.*
  def resolve(whName: String)(using n: NameResolver) =
    envr.Warehouse(
      name = n.wh(whName),
      meta = ObjMeta(props, tags, comment),
      accRoles = acc_roles.map(_.resolve(whName)).getOrElse(Map.empty)
    )

object Warehouse:
  case class Aux(acc_roles: Option[AccRoles], tags: Tags, comment: Comment) derives RW

  def fromJson(x: Json) = Warehouse(summon[RW[Aux]].write(x), props = Util.extraElems[Aux](x))

  given RW[Warehouse] = RW.from(x => summon[RW[Aux]].read(x.x), fromJson, DefType.Json)
