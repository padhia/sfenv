package sfenv
package rules

import io.circe.*

import envr.{ObjMeta, Props, PropVal}

case class UserId(x: UserId.Aux, props: Props):
  export x.*

  def resolve(name: String)(using n: NameResolver) =
    val defaults =
      List(
        "DEFAULT_WAREHOUSE" -> default_warehouse.map(x => PropVal.Str(n.wh(x))),
        "DEFAULT_NAMESPACE" -> default_namespace.map(_.resolve),
        "DEFAULT_ROLE"      -> default_role.map(x => PropVal.Str(n.fn(x)))
      )
        .collect:
          case (p, Some(v)) => p -> v
        .toMap

    envr.UserId(
      name = name,
      roles = roles.getOrElse(List.empty).map(r => envr.RoleName.Account(n.fn(r))),
      meta = ObjMeta(defaults ++ props, tags, comment),
      createObj = create.getOrElse(true)
    )

object UserId:
  case class Aux(
      roles: Option[List[String]],
      default_warehouse: Option[String],
      default_namespace: Option[Namespace],
      default_role: Option[String],
      tags: Tags,
      comment: Comment,
      create: Option[Boolean],
  ) derives Decoder

  given Decoder[UserId] with
    def apply(c: HCursor) = summon[Decoder[Aux]].apply(c).map(UserId(_, Util.fromCursor[Aux](c)))
