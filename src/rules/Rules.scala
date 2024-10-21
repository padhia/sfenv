package sfenv
package rules

import fabric.rw.RW

import cats.syntax.all.*

import envr.SfEnv

type Tags    = Option[Map[String, String]]
type Comment = Option[String]

case class Rules(
    config: Config,
    imports: Option[Map[String, Import]],
    databases: Option[Map[String, Database]],
    warehouses: Option[Map[String, Warehouse]],
    roles: Option[Map[String, Role]],
    apps: Option[Map[String, UserId]],
    users: Option[Map[String, UserId]]
) derives RW

object Rules:
  extension (r: Rules)
    def resolve(envName: String): SfEnv =
      extension [A](mo: Option[Map[String, A]])
        def asList                          = mo.map(_.toList).getOrElse(List.empty)
        def mapRbac[B](f: (String, A) => B) = mo.asList.map(f(_, _))

      given nr: NameResolver = NameResolver.makeUsing(r.config, envName)

      SfEnv(
        secAdm = envr.RoleName.Account(nr.secAdmin),
        sysAdm = envr.RoleName.Account(nr.dbAdmin),
        imports = r.imports.mapRbac((n, o) => o.resolve(n)),
        databases = r.databases.mapRbac((n, o) => o.resolve(n)),
        warehouses = r.warehouses.mapRbac((n, o) => o.resolve(n)),
        roles = r.roles.mapRbac((n, o) => o.resolve(n)),
        users = r.apps.mapRbac(((n, o) => o.resolve(nr.app(n)))) ++
          r.users.mapRbac((n, o) => o.resolve(n))
      )
