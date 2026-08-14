package sfenv
package rules

import cats.syntax.all.*

import scala.collection.immutable.SortedMap
import scala.collection.immutable.SortedSet

import fabric.rw.*
import envr.{RoleName, SchWh, UserGrants, UserRole}

type SchWhRoles = SortedMap[SchWh, String]

case class Role(
    acc_roles: Option[SchWhRoles],
    env_acc_roles: Option[SortedMap[EnvName, SchWhRoles]],
    users: Option[SortedSet[String]],
    apps: Option[SortedSet[String]],
    tags: Option[Tags],
    comment: Option[SqlLiteral],
    create: Option[Boolean],
) derives RW:

  def roleUsers(name: String)(using n: NameResolver): UserGrants =
    def toUserRole(x: String): UserRole = (Ident(name), n.fn(x.show))
    users.map(_.map(toUserRole)).getOrElse(SortedSet.empty)

object Role:
  given ObjMap[Role]:
    type Key   = Ident
    type Value = envr.Role.Value

    extension (r: Role)
      def keyVal(k: String)(using n: NameResolver) =
        def mkRole(schWh: SchWh, acc: String) =
          schWh match
            case SchWh.Schema(db, sch) => RoleName.Access(n.db(db.show), n.sch(db.show, sch.show), n.acc(db.show, sch.show, acc))
            case SchWh.Warehouse(wh)   => RoleName.Account(n.wacc(wh.show, acc))

        val accRoles: List[RoleName] = r.env_acc_roles
          .flatMap(_.get(n.env))
          .orElse(r.acc_roles)
          .map(_.toList.map(mkRole))
          .getOrElse(List.empty)

        (
          n.fn(k),
          envr.Role.Value(
            accRoles,
            envr.ObjMeta(Props.empty, r.tags, comment = r.comment),
            createObj = r.create.getOrElse(true)
          )
        )
