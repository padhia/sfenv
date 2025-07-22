package sfenv
package envr
import cats.data.Chain

case class Warehouse(name: String, meta: ObjMeta, accRoles: AccRoles)

object Warehouse:
  given SqlObj[Warehouse] with
    type Key = String

    extension (wh: Warehouse)
      override def id = wh.name

      override def create =
        given SqlOperable[AccRoles] = AccRoles.sqlOperable(wh.name)
        Sql.CreateObj("WAREHOUSE", wh.name, wh.meta.toString()) +: wh.accRoles.create

      override def unCreate =
        given SqlOperable[AccRoles] = AccRoles.sqlOperable(wh.name)
        wh.accRoles.unCreate :+ Sql.DropObj("WAREHOUSE", wh.name)

      override def alter(old: Warehouse) =
        given SqlOperable[AccRoles] = AccRoles.sqlOperable(wh.name)
        wh.meta.alter("WAREHOUSE", wh.name, old.meta) ++
          wh.accRoles.alter(old.accRoles)
