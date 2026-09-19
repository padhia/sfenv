package sfenv
package envr

import cats.Show
import cats.data.Chain
import cats.syntax.show.*

import scala.util.*

import fabric.*
import fabric.define.{Definition, DefType}
import fabric.rw.{RW, RWException}

enum SysRole:
  case Database(db: Ident, name: Ident)
  case Account(name: Ident)

object SysRole:
  def apply(x: String): Try[SysRole] = x.split("\\.") match
    case Array(name)   => Success(Account(Ident(name)))
    case Array(db, nm) => Success(Database(Ident(db), Ident(nm)))
    case _             => Failure(RuntimeException(s"Invalid Snowflake Role '$x'; must be either <name> or <db>.<name>"))

  given Show[SysRole] = Show.show(sr =>
    sr match
      case Database(db, name) => show"DATABASE ROLE ${db}.${name}"
      case Account(name)      => show"ROLE ${name}"
  )

  given Ordering[SysRole] = Ordering.by(_.show)

  given RW[SysRole] = RW.from(
    r = sr => str(sr.show),
    w = j =>
      apply(j.asString)
        .fold(e => throw RWException(e.getMessage), identity),
    d = Definition(DefType.Str)
  )

  def cda(grantee: String): CDA[SysRole] =
    new CDA[SysRole]:
      extension (x: SysRole)
        override def sameId(y: SysRole): Boolean        = Ordering[SysRole].equiv(x, y)
        override def updatable(y: SysRole): Boolean     = false
        override def create: Chain[SqlStmt]             = Chain(SqlStmt(Admin.SecAdm, Sql.Txt(show"GRANT $x TO $grantee")))
        override def drop: Chain[SqlStmt]               = Chain(SqlStmt(Admin.SecAdm, Sql.Txt(show"REVOKE $x FROM $grantee")))
        override def update(y: SysRole): Chain[SqlStmt] = ???
