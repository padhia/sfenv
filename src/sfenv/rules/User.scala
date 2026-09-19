package sfenv
package rules

import scala.collection.immutable.{SortedMap, SortedSet}

import fabric.*
import fabric.rw.*
import envr.{ObjMeta, UserGrants, UserRole}
import User.UserType

case class User(
    name: String,
    roles: SortedSet[String] = SortedSet.empty,
    default_warehouse: Option[String] = None,
    default_namespace: Option[Namespace] = None,
    default_role: Option[String] = None,
    tags: Tags = SortedMap.empty,
    comment: Option[SqlLiteral] = None,
    `type`: Option[String] = None,
    create: Boolean = true,
    props: Props,
):
  def userRoles(name: String)(using n: NameResolver): UserGrants =
    def toUserRole(r: String): UserRole = (Ident(name), n.fn(r))
    roles.map(toUserRole)

  def asEnvr(ut: UserType)(using n: NameResolver): envr.User =
    val defaults = Props.fromJson(
      Obj(
        Map(
          "TYPE"              -> `type`.orElse(Some(ut.typeName)).map(_.json),
          "DEFAULT_WAREHOUSE" -> default_warehouse.map(value => n.wh(value).json),
          "DEFAULT_ROLE"      -> default_role.map(value => n.fn(value).json),
        ).collect { case (key, Some(value)) => key -> value }
      )
    )

    envr.User(
      ut.userID(name),
      meta = ObjMeta(defaults ++ props, tags, comment),
      createObj = create,
      defaultNamespace = default_namespace.map(_.resolve)
    )

object User:
  given Ordering[User] = Ordering.by(_.name)

  given RW[User] = propsRW

  enum UserType:
    case Person, Service

    def userID(name: String)(using n: NameResolver): Ident =
      this match
        case Person  => Ident(name)
        case Service => n.app(name)

    def typeName: String = this match
      case Person  => "PERSON"
      case Service => "SERVICE"
