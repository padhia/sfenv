package sfenv
package envr

import cats.data.Chain
import cats.syntax.all.*

import scala.collection.immutable.SortedSet

import CDA.given
import SqlStmt.*

case class Database(name: Ident, transient: Boolean, meta: ObjMeta, schemas: SortedSet[Schema])

object Database:
  val kind = "DATABASE"

  given Ordering[Database] = Ordering.by(_.name)

  def apply(
      name: String,
      transient: Boolean = false,
      props: Props = Props.empty,
      tags: Tags = Tags.empty,
      comment: Option[String] = None,
      schemas: SortedSet[Schema] = SortedSet.empty,
  ): Database =
    Database(Ident(name), transient, ObjMeta(props, tags, comment.map(SqlLiteral.apply)), schemas)

  given CDA[Database]:
    extension (db: Database)
      private def permit: Permit[String] = Permit(show"USAGE, CREATE DATABASE ROLE ON DATABASE ${db.name}", Grantee.SecAdm)

      override def sameId(other: Database): Boolean  = db.name == other.name
      override def updatable(old: Database): Boolean = db.transient == old.transient

      override def create: Chain[SqlStmt] =
        val kind = if db.transient then "TRANSIENT DATABASE" else "DATABASE"
        Chain(db.meta.ddl(show"${kind.cr} ${db.name}"), permit.grant) ++ db.schemas.create

      override def drop: Chain[SqlStmt] =
        summon[CDA[SortedSet[Schema]]].drop(db.schemas) ++ Chain(permit.revoke, show"${Database.kind.dr} ${db.name}".ddl)

      override def update(old: Database): Chain[SqlStmt] =
        db.meta.ddl(show"${Database.kind.alt} ${db.name}", old.meta) ++ db.schemas.update(old.schemas)
