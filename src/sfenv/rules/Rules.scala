package sfenv
package rules

import cats.effect.IO
import cats.syntax.all.*

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import scala.collection.immutable.SortedSet
import scala.util.Try

import fabric.Json
import fabric.rw.*
import envr.SfEnv
import sfenv.envr.UserGrants
import Rules.given

case class Rules(
    config: Option[Config],
    options: Option[Options],
    account: Account = Account(),
    imports: SortedSet[Import] = SortedSet.empty,
    databases: SortedSet[Database] = SortedSet.empty,
    warehouses: SortedSet[Warehouse] = SortedSet.empty,
    roles: SortedSet[Role] = SortedSet.empty,
    apps: SortedSet[User] = SortedSet.empty,
    users: SortedSet[User] = SortedSet.empty,
    compute_pools: SortedSet[ComputePool] = SortedSet.empty
) derives RW:

  def resolve(envName: String): SfEnv =
    given nr: NameResolver = config.getOrElse(Config()).resolver(envName)

    val userGrants: UserGrants =
      def grants(members: SortedSet[User], userId: String => Ident) =
        for
          user <- members.toList
          role <- user.roles
        yield (userId(user.name), nr.fn(role))

      val roleGrants =
        for
          role <- roles.toList
          user <- role.users.map(Ident.apply).toList ++ role.apps.map(nr.app).toList
        yield (user, nr.fn(role.name))

      SortedSet.from(grants(users, Ident.apply) ++ grants(apps, nr.app) ++ roleGrants)

    SfEnv(
      secAdm = nr.secAdmin,
      sysAdm = nr.dbAdmin,
      account = envr.Account(account.params),
      imports = imports.map(_.asEnvr),
      databases = databases.map(_.asEnvr),
      warehouses = warehouses.map(_.asEnvr),
      computePools = compute_pools.map(_.asEnvr),
      roles = roles.map(_.asEnvr),
      users = users.map(_.asEnvr(User.UserType.Person)) ++ apps.map(_.asEnvr(User.UserType.Service)),
      userGrants = userGrants,
    )

object Rules:
  given importsRW: RW[SortedSet[Import]] = keyedSortedSetRW[Import](_.name)
  given databasesRW: RW[SortedSet[Database]] = keyedSortedSetRW[Database](_.name)
  given warehousesRW: RW[SortedSet[Warehouse]] = keyedSortedSetRW[Warehouse](_.name)
  given rolesRW: RW[SortedSet[Role]] = keyedSortedSetRW[Role](_.name)
  given usersRW: RW[SortedSet[User]] = keyedSortedSetRW[User](_.name)
  given computePoolsRW: RW[SortedSet[ComputePool]] = keyedSortedSetRW[ComputePool](_.name)

  private def parse(x: Json, path: Option[Path] = None): IO[Rules] =
    IO.fromEither(Try(x.as[Rules]).toEither.leftMap(e => AppError.RulesParsingError(e.getMessage(), path)))

  def apply(doc: String): IO[Rules] =
    YamlParser(doc).flatMap(parse(_))

  def apply(path: Option[Path]): IO[Rules] =
    path
      .map(apply)
      .getOrElse(apply(String(System.in.readAllBytes(), StandardCharsets.UTF_8)))

  def apply(path: Path): IO[Rules] =
    for
      exists <- IO.blocking(Files.exists(path))
      _      <- IO.raiseUnless(exists)(AppError.FileNotFound(path))
      rules  <-
        if path.toString.endsWith(".pkl") then PklParser(path).flatMap(json => parse(json, Some(path)))
        else apply(Files.readString(path))
    yield rules
