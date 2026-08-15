package sfenv
package rules

import cats.effect.IO
import cats.syntax.all.*

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import scala.collection.immutable.{SortedMap, SortedSet}
import scala.util.Try

import fabric.Json
import fabric.rw.*
import envr.SfEnv
import sfenv.envr.UserGrants

case class Rules(
    config: Option[Config],
    options: Option[Options],
    imports: Option[SortedMap[String, Import]],
    databases: Option[SortedMap[String, Database]],
    warehouses: Option[SortedMap[String, Warehouse]],
    roles: Option[SortedMap[String, Role]],
    apps: Option[SortedMap[String, User]],
    users: Option[SortedMap[String, User]],
    compute_pools: Option[SortedMap[String, ComputePool]]
) derives RW:

  def resolve(envName: String): SfEnv =
    given nr: NameResolver = config.getOrElse(Config()).resolver(envName)

    val userGrants: UserGrants =
      def ug(xs: Option[SortedMap[String, User]], fu: String => Ident) =
        for
          (u, o) <- xs.map(_.toList).getOrElse(List.empty)
          r      <- o.roles.getOrElse(SortedSet.empty[String])
        yield (fu(u), nr.fn(r))

      val rg =
        for
          (r, o) <- roles.getOrElse(SortedMap.empty[String, Role]).toList
          us = o.users.getOrElse(SortedSet.empty[String]).map(Ident.apply)
          as = o.apps.getOrElse(SortedSet.empty[String]).map(nr.app)
          u <- us ++ as
        yield (u, nr.fn(r))

      SortedSet.from(ug(users, Ident.apply) ++ ug(apps, nr.app) ++ rg)

    def objMap[T: ObjMap](xm: Option[SortedMap[String, T]]) =
      xm.getOrElse(SortedMap.empty[String, T]).map((n, o) => o.keyVal(n))

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
  private def parse(x: => Json): IO[Rules] =
    IO.fromEither(Try(x.as[Rules]).toEither.leftMap(e => AppError.RulesParsingError(e.getMessage())))

  def apply(doc: String): IO[Rules] = parse(YamlParser(doc))

  def apply(path: Option[Path]): IO[Rules] =
    path
      .map(apply)
      .getOrElse(apply(String(System.in.readAllBytes(), StandardCharsets.UTF_8)))

  def apply(path: Path): IO[Rules] =
    for
      exists <- IO.blocking(Files.exists(path))
      _      <- IO.raiseUnless(exists)(AppError.FileNotFound(path))
      rules  <-
        if path.toString.endsWith(".pkl") then PklParser(path).flatMap(json => parse(json)) else apply(Files.readString(path))
    yield rules
