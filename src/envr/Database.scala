package sfenv
package envr

import cats.data.Chain

import SqlOperable.given

case class Database(name: String, transient: Boolean, meta: ObjMeta, schemas: List[Schema]):
  def kind = if transient then "TRANSIENT DATABASE" else "DATABASE"

object Database:
  extension (x: String) def sql = SqlStmt(Admin.Sys, x)

  given CDA[Database]:
    extension (db: Database)
      override def create: Chain[SqlStmt] =
        val ddl = List(
          Some("CREATE"),
          Option.when(db.transient)("TRANSIENT"),
          Some(s"DATABASE IF NOT EXISTS ${db.name}"),
          db.meta.toText,
        ).flatten.mkString(" ")

        Chain(
          ddl.sql,
          SqlStmt(Admin.Sys, (_: String, sec: String) => s"GRANT USAGE, CREATE DATABASE ROLE ON DATABASE ${db.name} TO ROLE $sec")
        )
      override def update(old: Database): Chain[SqlStmt] = Chain("ALTER DATABASE IF EXISTS".sql)
      override def updatable(old: Database): Boolean = db.transient == old.transient
      override def drop: Chain[SqlStmt] = Chain(s"DROP DATABASE IF EXISTS ${db.name}".sql)
      override def sameId(other: Database): Boolean = db.name == other.name

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
