package sfenv
package envr

import cats.Show
import cats.syntax.show.*

enum Grantee:
  case User(id: Ident)
  case SysAdm
  case SecAdm
  case Role(name: RoleName)

case class Permit[P: Show](permission: P, grantee: Grantee, grantor: Admin = Admin.Sys, allObjects: Boolean = false):
  private def sqlStmt(grant: String, to: String): SqlStmt =
    import Grantee.*

    val sql = grantee match
      case User(id)   => Sql.Txt(show"$grant $permission $to USER ${id}")
      case SysAdm     => Sql.Sys(sys => show"$grant $permission $to ROLE $sys")
      case SecAdm     => Sql.Sec(sec => show"$grant $permission $to ROLE $sec")
      case Role(name) => Sql.Txt(show"$grant $permission $to $name")

    SqlStmt(grantor, sql, allObjects)

  def grant  = sqlStmt("GRANT", "TO")
  def revoke = sqlStmt("REVOKE", "FROM")

object Permit:
  def apply[P: Show](permission: P, grantee: RoleName): Permit[P] = Permit(permission, Grantee.Role(grantee))

  def apply[P: Show](permission: P, grantee: RoleName, grantor: Admin): Permit[P] =
    Permit(permission, Grantee.Role(grantee), grantor)
