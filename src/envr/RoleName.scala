package sfenv
package envr

import io.circe.*

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
  def apply(x: String) = x.split("\\.") match
    case Array(db, role) => Some(Database(db, role))
    case Array(role)     => Some(Account(role))
    case _               => None

  given Decoder[RoleName] = summon[Decoder[String]].emap(x => apply(x).toRight(s"$x is an invalid role name"))

  given KeyDecoder[RoleName] with
    def apply(x: String) = RoleName.apply(x)

  extension (xs: List[RoleName])
    def regrant(ys: List[RoleName], grantee: UserName | RoleName) =
      xs.merge(ys)
        .collect:
          case (Some(n), None) => Sql.RoleGrant(n, grantee)
          case (None, Some(o)) => Sql.RoleGrant(o, grantee, revoke = true)
