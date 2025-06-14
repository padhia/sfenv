package sfenv
package envr

import cats.data.Chain
import cats.syntax.all.*

import scala.collection.immutable.ListMap

import CDA.given
import SqlStmt.*

case class Database(name: Ident, value: Database.Value):
  export value.*
  def schList = schemas.toList.map((sch, value) => Schema((name, sch), value))

object Database:
  val kind = "DATABASE"

  case class Value(transient: Boolean, meta: ObjMeta, schemas: ListMap[Ident, Schema.Value])

  def apply(
      name: String,
      transient: Boolean = false,
      props: Props = Props.empty,
      tags: Tags = Tags.empty,
      comment: Option[String] = None,
      schemas: ListMap[String, Schema.Value] = ListMap.empty,
  ): Database =
    Database(
      Ident(name),
      Value(transient, ObjMeta(props, tags, comment.map(SqlLiteral.apply)), schemas = schemas.map((k, v) => Ident(k) -> v))
    )

given CDA[Database]:
  extension (db: Database)
    private def permit: Permit[String] = Permit(show"USAGE, CREATE DATABASE ROLE ON DATABASE ${db.name}", Grantee.SecAdm)

    override def sameId(other: Database): Boolean  = db.name == other.name
    override def updatable(old: Database): Boolean = db.transient == old.transient

    override def create: Chain[SqlStmt] =
      val kind = if db.transient then "TRANSIENT DATABASE" else "DATABASE"
      Chain(db.meta.ddl(show"${kind.cr} ${db.name}"), permit.grant) ++ db.schList.create

    override def drop: Chain[SqlStmt] =
      summon[CDA[List[Schema]]].drop(db.schList) ++ Chain(permit.revoke, show"${Database.kind.dr} ${db.name}".ddl)

    override def update(old: Database): Chain[SqlStmt] =
      db.meta.ddl(show"${Database.kind.alt} ${db.name}", old.meta) ++ db.schList.update(old.schList)
