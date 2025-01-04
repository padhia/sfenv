package sfenv
package envr

import cats.data.Chain

import Sql.*

case class Schema(name: String, transient: Boolean, managed: Boolean, meta: ObjMeta, accRoles: AccRoles):
  def kind = if transient then "TRANSIENT SCHEMA" else "SCHEMA"
  def fullName(db: String) = s"$db.$name"

object Schema:
  def sqlOp(dbName: String) =
    given SfObj[Schema] with
      extension (obj: Schema) def id: SfObjId = SfObjId(obj.name)
      def genSql(obj: SqlOp[Schema]): Chain[SqlStmt] = obj match
        case SqlOp.Create(sch)   => Chain(SqlStmt.createObj(sch.kind, sch.fullName(dbName), sch.meta))
        case SqlOp.Drop(sch)     => Chain(SqlStmt.dropObj("SCHEMA", sch.fullName(dbName)))
        case SqlOp.Alter(sch, _) => Chain(SqlStmt.createObj(sch.kind, sch.fullName(dbName), sch.meta))

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
