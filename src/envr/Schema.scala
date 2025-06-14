package sfenv
package envr

import cats.data.Chain

import Sql.*

case class Schema(db: String, name: String, transient: Boolean, managed: Boolean, meta: ObjMeta, accRoles: AccRoles):
  def kind       = if transient then "TRANSIENT SCHEMA" else "SCHEMA"
  def isReserved = name.toLowerCase == "public" || name.toLowerCase == "information_schema"
  def fullName   = s"$db.$name".toUpperCase()

object Schema:
  import AccRoles.given

  extension (x: String) def sql = SqlStmt(Admin.Sys, x)

  given CDA[Schema]:
    extension (sch: Schema)
      override def create: Chain[SqlStmt] =
        val createDdl =
          if sch.isReserved then Chain.empty
          else
            val ddl = List(
              Some("CREATE"),
              Option.when(sch.transient)("TRANSIENT"),
              Some(s"SCHEMA ${sch.fullName}"),
              Option.when(sch.managed)("WITH MANAGED ACCESS"),
              sch.meta.toText
            ).flatten.mkString(" ").sql
            Chain(ddl)

        createDdl ++ sch.accRoles.create

      override def drop: Chain[SqlStmt] =
        summon[CDA[AccRoles]].drop(sch.accRoles)
          ++ (if sch.isReserved then Chain.empty else Chain(s"DROP SCHEMA ${sch.fullName}".sql))
      override def sameId(other: Schema): Boolean      = sch.fullName == other.fullName
      override def updatable(old: Schema): Boolean     = sch.transient == old.transient
      override def update(old: Schema): Chain[SqlStmt] =
        val managed =
          if sch.managed != old.managed then Chain(s"${if sch.managed then "ENABLE" else "DISABLE"} MANAGED ACCESS")
          else Chain.empty

        (managed ++ sch.meta.toText(old.meta)).map(x => s"ALTER SCHEMA IF EXISTS ${sch.fullName} $x".sql)
          ++ sch.accRoles.update(old.accRoles)

  def sqlObj(dbName: String) =
    new SqlObj[Schema]:
      type Key = String

      extension (sch: Schema)
        private def schName = s"$dbName.${sch.name}"

        override def id = sch.name

        override def create: Chain[Sql] =
          given SqlOperable[AccRoles] = AccRoles.sqlOperable(sch.schName, dbName)

          val createDdl =
            if sch.name.toLowerCase == "public" || sch.name.toLowerCase == "information_schema" then Chain.empty
            else
              val kind    = if sch.transient then "TRANSIENT SCHEMA" else "SCHEMA"
              val managed = if sch.managed then " WITH MANAGED ACCESS" else ""
              Chain(CreateObj(kind, sch.schName, s"$managed${sch.meta}"))

          createDdl ++ sch.accRoles.create

        override def unCreate: Chain[Sql] =
          given SqlOperable[AccRoles] = AccRoles.sqlOperable(sch.schName, dbName)

          sch.accRoles.unCreate ++ (
            if sch.name.toLowerCase == "public" || sch.name.toLowerCase == "information_schema" then Chain.empty
            else Chain(DropObj("SCHEMA", sch.schName))
          )

        override def alter(old: Schema): Chain[Sql] =
          if sch.transient != old.transient then old.unCreate ++ create
          else
            given SqlOperable[AccRoles] = AccRoles.sqlOperable(sch.schName, dbName)

            def alter_managed =
              if sch.managed != old.managed then
                val state = if sch.managed then "ENABLE" else "DISABLE"
                Chain(Sql.AlterObj("SCHEMA", sch.schName, s" $state MANAGED ACCESS"))
              else Chain.empty

            alter_managed ++
              sch.meta.alter("SCHEMA", sch.schName, old.meta) ++
              sch.accRoles.alter(old.accRoles)
