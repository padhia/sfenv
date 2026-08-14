package sfenv
package envr

import cats.Show
import cats.kernel.Eq
import cats.syntax.all.*

import SqlStmt.*

enum RoleName:
  case Access(db: Ident, sch: Ident, acc: Ident)
  case Account(roleName: Ident)

  def kind: String = this match
    case _: RoleName.Access => "DATABASE ROLE"
    case _: Account         => "ROLE"

  def name: String = this match
    case RoleName.Access(d, _, a) => show"$d.$a"
    case Account(r)               => r.show

  def create: SqlStmt = show"${kind.cr} $name".dcl

  def drop: SqlStmt = show"${kind.dr} $name".dcl

object RoleName:
  def apply(x: String): Either[String, RoleName] = x.split("\\.") match
    case Array(db, role) =>
      val ix = role.lastIndexOf('_')
      Either.cond(ix > 0, Access(Ident(db), Ident(role.substring(0, ix)), Ident(role)), s"Invalid Access Role: $x")
    case Array(role) => Right(Account(Ident(role)))
    case _           => Left(show"Invalid role name: '$x'")

  def db(db: String, sch: String, acc: String): RoleName = Access(Ident(db), Ident(sch), Ident(acc))
  def acc(name: String): RoleName                        = Account(Ident(name))

  given Show[RoleName]:
    override def show(x: RoleName): String = x match
      case _: Access  => show"DATABASE ROLE ${x.name}"
      case _: Account => show"ROLE ${x.name}"

  given Eq[RoleName]:
    override def eqv(x: RoleName, y: RoleName) = (x, y) match
      case (RoleName.Access(d1, s1, a1), RoleName.Access(d2, s2, a2)) => d1 == d2 && s1 == s2 && a1 == a2
      case (RoleName.Account(r1), RoleName.Account(r2))               => r1 == r2
      case _                                                          => false

  given Ordering[RoleName] = Ordering.by(_.name)
