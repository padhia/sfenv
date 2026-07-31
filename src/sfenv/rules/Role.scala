package sfenv
package rules

import fabric.rw.*

import cats.syntax.all.*

import scala.collection.immutable.ListMap
import scala.collection.immutable.ListSet

import envr.{RoleName, SchWh, UserGrants}

type SchWhRoles = ListMap[SchWh, String]

case class Role(
    acc_roles: Option[SchWhRoles],
    env_acc_roles: Option[ListMap[EnvName, SchWhRoles]],
    users: Option[ListSet[String]],
    apps: Option[ListSet[String]],
    tags: Option[Tags],
    comment: Option[SqlLiteral],
    create: Option[Boolean],
) derives RW:

  def roleUsers(name: String)(using n: NameResolver): UserGrants =
    users.map(_.map(r => (Ident(name), n.fn(r.show)))).getOrElse(ListSet.empty)

object Role:
  given ObjMap[Role]:
    type Key   = Ident
    type Value = envr.Role.Value

    extension (r: Role)
      def keyVal(k: String)(using n: NameResolver) =
        def mkRole(schWh: SchWh, acc: String) =
          schWh match
            case SchWh.Schema(db, sch) => RoleName.Database(n.db(db.show), n.acc(db.show, sch.show, acc))
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
