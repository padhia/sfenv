package sfenv
package rules

import io.circe.*

import envr.{Props, RoleName, SchWh}

type SchWhRoles = Map[SchWh, String]

case class Role(
    acc_roles: Option[SchWhRoles],
    env_acc_roles: Option[Map[EnvName, SchWhRoles]],
    tags: Tags,
    comment: Comment,
    create: Option[Boolean],
) derives Decoder:

  def resolve(name: String)(using n: NameResolver) =
    def mkRole(schWh: SchWh, acc: String) =
      schWh match
        case SchWh.Schema(db, sch) => RoleName.Database(n.db(db), n.acc(db, sch, acc))
        case SchWh.Warehouse(wh)   => RoleName.Account(n.wacc(wh, acc))

    val accRoles = env_acc_roles
      .flatMap(_.get(n.env))     // get environment specific roles
      .orElse(acc_roles)         // if no environment, fall back to general roles
      .map(_.toList.map(mkRole)) // map schwh roles to role names
      .getOrElse(List.empty)

    envr.Role(
      RoleName.Account(n.fn(name)),
      accRoles,
      envr.ObjMeta(Props.empty, tags, comment = comment),
      createObj = create.getOrElse(true)
    )
