package sfenv
package envr

import cats.data.Chain
import cats.syntax.show.*

import scala.collection.immutable.SortedSet

type UserRole   = (user: Ident, role: Ident)
type UserGrants = SortedSet[UserRole]

object UserGrants:
  def empty: UserGrants = SortedSet.empty[UserRole]

  extension (ug: UserGrants)
    private def permit(f: Permit[String] => SqlStmt) =
      Chain
        .fromSeq(ug.toSeq)
        .map(ur => Permit(show"ROLE ${ur.role}", Grantee.User(ur.user), grantor = Admin.Sec))
        .map(f)

    def grant: Chain[SqlStmt]  = ug.permit(_.grant)
    def revoke: Chain[SqlStmt] = ug.permit(_.revoke)
