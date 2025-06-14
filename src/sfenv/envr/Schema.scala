package sfenv
package envr

import cats.Show
import cats.data.Chain
import cats.syntax.all.*

import scala.collection.immutable.ListMap

import SqlStmt.*

case class Schema(name: SchName, value: Schema.Value):
  export value.*

  def accRoles = accRoleMap.toList.map(AccRole(_, _))

object Schema:
  import CDA.given
  case class Value(transient: Boolean, managed: Boolean, meta: ObjMeta, accRoleMap: ListMap[RoleName, AccRole.Value])

  def apply(
      db: String,
      name: String,
      transient: Boolean = false,
      managed: Boolean = false,
      meta: ObjMeta = ObjMeta.empty,
      accRoles: ListMap[String, ListMap[String, List[String]]] = ListMap.empty
  ): Either[String, Schema] =
    AccRole(accRoles, show"$db.$name").map(ar => Schema((Ident(db), Ident(name)), Value(transient, managed, meta, ar)))

  val kind = "SCHEMA"

  given Show[SchName] = Show.show(x => show"${x.db}.${x.sch}")

  given CDA[Schema]:
    extension (sch: Schema)
      private def arTc       = AccRole.cda(SchWh.Schema(sch.name.db, sch.name.sch))
      private def isReserved = sch.name.sch == Ident("public") || sch.name.sch == Ident("information_schema")

      override def sameId(other: Schema): Boolean  = sch.name == other.name
      override def updatable(old: Schema): Boolean = sch.transient == old.transient

      override def create: Chain[SqlStmt] =
        given CDA[AccRole] = arTc
        (
          if isReserved then Chain.empty
          else
            val kind = if sch.transient then "TRANSIENT SCHEMA" else "SCHEMA"
            Chain(sch.meta.ddl(concat(show"${kind.cr} ${sch.name}", Option.when(sch.managed)("WITH MANAGED ACCESS"))))
        ) ++ sch.accRoles.create

      override def drop: Chain[SqlStmt] =
        given CDA[AccRole] = arTc
        summon[CDA[List[AccRole]]].drop(sch.accRoles)
          ++ (if sch.isReserved then Chain.empty else Chain(show"${kind.dr} ${sch.name}".ddl))

      override def update(old: Schema): Chain[SqlStmt] =
        given CDA[AccRole] = arTc

        val managed =
          if sch.managed != old.managed then
            Chain(show"${kind.alt} ${sch.name} ${if sch.managed then "ENABLE" else "DISABLE"} MANAGED ACCESS".ddl)
          else Chain.empty

        (managed ++ sch.meta.ddl(show"${kind.alt} ${sch.name}", old.meta))
          ++ sch.accRoles.update(old.accRoles)
