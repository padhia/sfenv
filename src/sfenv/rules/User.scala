package sfenv
package rules

import io.circe.*

import scala.collection.immutable.{ListMap, ListSet}

import envr.{ObjMeta, UserGrants}

case class User(x: User.Aux, props: Props):
  export x.*

  def userRoles(name: String)(using n: NameResolver): UserGrants =
    roles.map(_.map(r => (Ident(name), n.fn(r)))).getOrElse(ListSet.empty)

object User:
  case class Aux(
      roles: Option[ListSet[String]],
      default_warehouse: Option[String],
      default_namespace: Option[Namespace],
      default_role: Option[String],
      tags: Option[Tags],
      comment: Option[SqlLiteral],
      create: Option[Boolean],
  ) derives Decoder

  given Decoder[User] with
    def apply(c: HCursor) = summon[Decoder[Aux]].apply(c).map(User(_, Util.fromCursor[Aux](c)))

  def objMap(f: String => Ident) =
    new ObjMap[User]:
      type Key   = Ident
      type Value = envr.User.Value

      extension (r: User)
        def keyVal(k: String)(using n: NameResolver) =
          val defaults =
            ListMap(
              "DEFAULT_WAREHOUSE" -> r.default_warehouse.map(x => PropVal(n.wh(x))),
              "DEFAULT_NAMESPACE" -> r.default_namespace.map(_.resolve),
              "DEFAULT_ROLE"      -> r.default_role.map(x => PropVal(n.fn(x)))
            ).collect { case (p, Some(v)) => Ident(p) -> v }

          (
            f(k),
            envr.User.Value(
              meta = ObjMeta(defaults ++ r.props, r.tags, r.comment),
              createObj = r.create.getOrElse(true)
            )
          )
