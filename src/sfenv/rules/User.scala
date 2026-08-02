package sfenv
package rules

import scala.collection.immutable.{SortedMap, SortedSet}

import fabric.*
import fabric.define.DefType
import fabric.rw.*
import envr.{ObjMeta, UserGrants, UserRole}

case class User(
    roles: Option[SortedSet[String]],
    default_warehouse: Option[String],
    default_namespace: Option[Namespace],
    default_role: Option[String],
    tags: Option[Tags],
    comment: Option[SqlLiteral],
    create: Option[Boolean],
    props: Props,
):
  def userRoles(name: String)(using n: NameResolver): UserGrants =
    def toUserRole(r: String): UserRole = (Ident(name), n.fn(r))
    roles.map(_.map(toUserRole)).getOrElse(SortedSet.empty)

object User:
  given RW[User] = RW.from(
    r = _ => throw UnsupportedOperationException("User serialization not supported"),
    w = json =>
      User(
        roles = json.attr("roles").as[Option[SortedSet[String]]],
        default_warehouse = json.attr("default_warehouse").as[Option[String]],
        default_namespace = json.attr("default_namespace").as[Option[Namespace]],
        default_role = json.attr("default_role").as[Option[String]],
        tags = json.attr("tags").as[Option[Tags]],
        comment = json.attr("comment").as[Option[SqlLiteral]],
        create = json.attr("create").as[Option[Boolean]],
        props = json.props[User],
      ),
    d = DefType.Json
  )

  def objMap(f: String => Ident) =
    new ObjMap[User]:
      type Key   = Ident
      type Value = envr.User.Value

      extension (r: User)
        def keyVal(k: String)(using n: NameResolver) =
          val defaults =
            SortedMap(
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
