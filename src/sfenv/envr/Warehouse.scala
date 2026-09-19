package sfenv
package envr

import cats.data.Chain
import cats.syntax.all.*

import scala.collection.immutable.SortedSet

import SqlStmt.*

case class Warehouse(name: Ident, meta: ObjMeta, accRoles: SortedSet[AccRole])

object Warehouse:
  import CDA.given

  val kind = "WAREHOUSE"

  given Ordering[Warehouse] = Ordering.by(_.name)

  given CDA[Warehouse]:
    extension (wh: Warehouse)
      private def arTC = AccRole.cda(SchWh.Warehouse(wh.name))

      override def sameId(other: Warehouse): Boolean  = wh.name == other.name
      override def updatable(old: Warehouse): Boolean = true

      override def create: Chain[SqlStmt] =
        given CDA[AccRole] = wh.arTC
        wh.meta.ddl(show"${kind.cr} ${wh.name}") +: wh.accRoles.create

      override def drop: Chain[SqlStmt] =
        given CDA[AccRole] = wh.arTC
        summon[CDA[SortedSet[AccRole]]].drop(wh.accRoles) :+ show"${kind.dr} ${wh.name}".ddl

      override def update(old: Warehouse): Chain[SqlStmt] =
        given CDA[AccRole] = wh.arTC
        wh.meta.ddl(show"${kind.alt} ${wh.name}", old.meta)
          ++ wh.accRoles.update(old.accRoles)
