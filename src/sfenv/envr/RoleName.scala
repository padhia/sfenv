package sfenv
package envr

import io.circe.*

import cats.Show
import cats.kernel.Eq
import cats.syntax.all.*

import SqlStmt.*

enum RoleName:
  case Database(db: Ident, roleName: Ident)
  case Account(roleName: Ident)

  def kind: String = this match
    case _: RoleName.Database => "DATABASE ROLE"
    case _: Account           => "ROLE"

  def name: String = this match
    case RoleName.Database(d, r) => show"$d.$r"
    case Account(r)              => r.show

  def create: SqlStmt = show"${kind.cr} $name".dcl

  def drop: SqlStmt = show"${kind.dr} $name".dcl

object RoleName:
  def apply(x: String): Either[String, RoleName] = x.split("\\.") match
    case Array(db, role) => Right(Database(Ident(db), Ident(role)))
    case Array(role)     => Right(Account(Ident(role)))
    case _               => Left(show"Invalid role name: '$x'")

  def db(db: String, name: String): RoleName = Database(Ident(db), Ident(name))
  def acc(name: String): RoleName            = Account(Ident(name))

  given Show[RoleName]:
    override def show(x: RoleName): String = x match
      case Database(d, r) => show"DATABASE ROLE $d.$r"
      case Account(r)     => show"ROLE $r"

  given Eq[RoleName]:
    override def eqv(x: RoleName, y: RoleName) = (x, y) match
      case (RoleName.Database(d1, r1), RoleName.Database(d2, r2)) => d1 == d2 && r1 == r2
      case (RoleName.Account(r1), RoleName.Account(r2))           => r1 == r2
      case _                                                      => false

  given Decoder[RoleName] = summon[Decoder[String]].emap(x => apply(x))

  given KeyDecoder[RoleName]:
    def apply(x: String) = RoleName(x).toOption
