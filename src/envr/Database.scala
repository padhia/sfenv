package sfenv
package envr

import cats.data.Chain

import SqlOperable.given

case class Database(name: String, transient: Boolean, meta: ObjMeta, schemas: List[Schema]):
  def kind = if transient then "TRANSIENT DATABASE" else "DATABASE"

object Database:
  given SfObj[Database] with
    extension (obj: Database) def id: SfObjId = SfObjId(obj.name)
    def genSql(obj: SqlOp[Database]): Chain[SqlStmt] = obj match
      case SqlOp.Create(db)   => Chain(SqlStmt.createObj(db.kind, db.name, db.meta))
      case SqlOp.Drop(db)     => Chain(SqlStmt.dropObj("DATABASE", db.name))
      case SqlOp.Alter(db, _) => Chain(SqlStmt.createObj(db.kind, db.name, db.meta))

  def sqlObj(secAdm: RoleName) =
    new SqlObj[Database]:
      type Key = String

      extension (db: Database)
        override def id = db.name

        override def create =
          given SqlObj[Schema] = Schema.sqlObj(db.name)
          import db.*

          Chain(
            Sql.CreateObj(if transient then "TRANSIENT DATABASE" else "DATABASE", name, meta.toString()),
            Sql.ObjGrant("DATABASE", name, secAdm, List("CREATE DATABASE ROLE", "USAGE"))
          ) ++
            Chain.fromSeq(schemas).flatMap(_.create)

        override def unCreate =
          given SqlObj[Schema] = Schema.sqlObj(db.name)
          Chain.fromSeq(db.schemas).flatMap(_.unCreate) :+ Sql.DropObj("DATABASE", db.name)

        override def alter(old: Database) =
          if db.transient != old.transient then old.unCreate ++ create
          else
            given SqlObj[Schema] = Schema.sqlObj(db.name)
            db.meta.alter("DATABASE", db.name, old.meta) ++
              Chain.fromSeq(db.schemas).alter(Chain.fromSeq(old.schemas))
