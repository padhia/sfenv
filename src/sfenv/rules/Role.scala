package sfenv
package rules

import cats.syntax.all.*

import scala.collection.immutable.SortedMap
import scala.collection.immutable.SortedSet

import fabric.rw.*
import envr.{RoleName, SchWh, SysRole, UserGrants, UserRole}

type SchWhRoles = SortedMap[SchWh, String]

case class Role(
    name: String,
    acc_roles: SchWhRoles = SortedMap.empty,
    env_acc_roles: SortedMap[EnvName, SchWhRoles] = SortedMap.empty,
    sys_roles: SortedSet[SysRole] = SortedSet.empty,
    users: SortedSet[String] = SortedSet.empty,
    apps: SortedSet[String] = SortedSet.empty,
    tags: Tags = SortedMap.empty,
    comment: Option[SqlLiteral],
    create: Boolean = true,
) derives RW:

  def roleUsers(name: String)(using n: NameResolver): UserGrants =
    def toUserRole(x: String): UserRole = (Ident(name), n.fn(x.show))
    users.map(toUserRole)

  def asEnvr(using resolver: NameResolver): envr.Role =
    def mkRole(schWh: SchWh, access: String) =
      schWh match
        case SchWh.Schema(database, schema) =>
          RoleName.Access(
            resolver.db(database.show),
            resolver.sch(database.show, schema.show),
            resolver.acc(database.show, schema.show, access)
          )
        case SchWh.Warehouse(warehouse) => RoleName.Account(resolver.wacc(warehouse.show, access))

    val accRoles = env_acc_roles.getOrElse(resolver.env, acc_roles).toList.map(mkRole)

    envr.Role(
      resolver.fn(name),
      accRoles,
      sys_roles,
      envr.ObjMeta(Props.empty, tags, comment = comment),
      createObj = create,
    )

object Role:
  given Ordering[Role] = Ordering.by(_.name)
