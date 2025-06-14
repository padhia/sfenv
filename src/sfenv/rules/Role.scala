package sfenv
package rules

import io.circe.*

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
) derives Decoder:

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
          .flatMap(_.get(n.env))     // get environment specific roles
          .orElse(r.acc_roles)       // if no environment, fall back to general roles
          .map(_.toList.map(mkRole)) // map schwh roles to role names
          .getOrElse(List.empty)

        (
          n.fn(k),
          envr.Role.Value(
            accRoles,
            envr.ObjMeta(Props.empty, r.tags, comment = r.comment),
            createObj = r.create.getOrElse(true)
          )
        )
