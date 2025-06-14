package sfenv
package rules

import io.circe.*
import io.circe.yaml.parser.parse

import scala.collection.immutable.ListMap
import scala.collection.immutable.ListSet

import envr.SfEnv
import sfenv.envr.UserGrants

case class Rules(
    config: Option[Config],
    options: Option[Options],
    imports: Option[ListMap[String, Import]],
    databases: Option[ListMap[String, Database]],
    warehouses: Option[ListMap[String, Warehouse]],
    roles: Option[ListMap[String, Role]],
    apps: Option[ListMap[String, User]],
    users: Option[ListMap[String, User]],
    compute_pools: Option[ListMap[String, ComputePool]]
) derives Decoder:

  def resolve(envName: String): SfEnv =
    given nr: NameResolver = config.getOrElse(Config()).resolver(envName)

    val userGrants: UserGrants =
      def ug(xs: Option[ListMap[String, User]], fu: String => Ident) =
        for
          (u, o) <- xs.map(_.toList).getOrElse(List.empty)
          r      <- o.roles.getOrElse(ListSet.empty)
        yield (fu(u), nr.fn(r))

      val rg =
        for
          (r, o) <- roles.getOrElse(ListMap.empty).toList
          us = o.users.getOrElse(ListSet.empty).map(Ident.apply)
          as = o.apps.getOrElse(ListSet.empty).map(nr.app)
          u <- us ++ as
        yield (u, nr.fn(r))

      ListSet.from(ug(users, Ident.apply) ++ ug(apps, nr.app) ++ rg)

    def objMap[T: ObjMap](xm: Option[ListMap[String, T]]) = xm.map(_.map((n, o) => o.keyVal(n))).getOrElse(ListMap.empty)

    SfEnv(
      secAdm = nr.secAdmin,
      sysAdm = nr.dbAdmin,
      imports = objMap(imports),
      databases = objMap(databases),
      warehouses = objMap(warehouses),
      computePools = objMap(compute_pools),
      roles = objMap(roles),
      users = objMap(apps)(using User.objMap(nr.app)) ++ objMap(users)(using User.objMap(Ident.apply)),
      userGrants = userGrants,
    )

object Rules:
  def apply(x: String) = parse(x).flatMap(Decoder[rules.Rules].decodeJson(_))
