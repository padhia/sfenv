package sfenv
package envr

import fabric.rw.*
import fabric.*
import rules.Util.*

enum RoleName:
  case Database(db: String, name: String)
  case Account(name: String)

  def kind: String = this match
    case _: RoleName.Database => "DATABASE ROLE"
    case _: Account           => "ROLE"

  def roleName = this match
    case RoleName.Database(d, r) => s"$d.$r"
    case Account(r)              => r

  def role = s"$kind $roleName"

object RoleName:
  def apply(x: String): Either[String, RoleName] = x.split("\\.") match
    case Array(db, role) => Right(Database(db, role))
    case Array(role)     => Right(Account(role))
    case _               => Left(s"$x is not valid RoleName")

  given RW[RoleName] = RW.string(_.roleName, RoleName(_).value)

  extension (xs: List[RoleName])
    def regrant(ys: List[RoleName], grantee: UserName | RoleName) =
      xs.merge(ys).collect {
        case (Some(n), None) => Sql.RoleGrant(n, grantee)
        case (None, Some(o)) => Sql.RoleGrant(o, grantee, revoke = true)
      }
