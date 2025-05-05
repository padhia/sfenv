package sfenv
package rules

import io.circe.*

import cats.syntax.all.*

import envr.SfEnv

type Tags    = Option[Map[String, String]]
type Comment = Option[String]

case class Rules(
    config: Config,
    options: Option[Options],
    imports: Option[Map[String, Import]],
    databases: Option[Map[String, Database]],
    warehouses: Option[Map[String, Warehouse]],
    roles: Option[Map[String, Role]],
    apps: Option[Map[String, UserId]],
    users: Option[Map[String, UserId]],
    compute_pools: Option[Map[String, ComputePool]]
) derives Decoder:

  def resolve(envName: String, onlyFuture: Option[Boolean], drops: Option[ProcessDrops]): SfEnv =
    extension [A](mo: Option[Map[String, A]])
      def asList                          = mo.map(_.toList).getOrElse(List.empty)
      def mapRbac[B](f: (String, A) => B) = mo.asList.map(f(_, _))

    given nr: NameResolver = NameResolver.makeUsing(config, envName)

    SfEnv(
      secAdm = envr.RoleName.Account(nr.secAdmin),
      sysAdm = envr.RoleName.Account(nr.dbAdmin),
      imports = imports.mapRbac((n, o) => o.resolve(n)),
      databases = databases.mapRbac((n, o) => o.resolve(n)),
      warehouses = warehouses.mapRbac((n, o) => o.resolve(n)),
      computePools = compute_pools.mapRbac((n, o) => o.resolve(n)),
      roles = roles.mapRbac((n, o) => o.resolve(n)),
      users = apps.mapRbac(((n, o) => o.resolve(nr.app(n)))) ++ users.mapRbac((n, o) => o.resolve(n)),
      createUsers = options.flatMap(_.create_users).getOrElse(true),
      createRoles = options.flatMap(_.create_roles).getOrElse(true),
      drops = drops.orElse(options.flatMap(_.drop)).getOrElse(ProcessDrops.NonLocal),
      onlyFutures = onlyFuture.orElse(options.flatMap(_.only_futures)).getOrElse(false)
    )
