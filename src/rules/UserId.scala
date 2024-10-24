package sfenv
package rules

import envr.{ObjMeta, Props}
import fabric.*
import fabric.define.DefType
import fabric.rw.*

case class UserId(x: UserId.Aux, props: Props):
  export x.*

  def resolve(name: String)(using n: NameResolver) =
    envr.UserId(
      name,
      roles.getOrElse(List.empty).map(r => envr.RoleName.Account(n.fn(r))),
      ObjMeta(props, tags, comment)
    )

object UserId:
  case class Aux(roles: Option[List[String]], tags: Tags, comment: Comment) derives RW

  def fromJson(x: Json) = UserId(summon[RW[Aux]].write(x), props = Util.extraElems[Aux](x))

  given RW[UserId] = RW.from(x => summon[RW[Aux]].read(x.x), fromJson, DefType.Json)
