package sfenv
package envr

import cats.data.Chain
import cats.syntax.all.*

import SqlStmt.*

case class Import(name: Ident, value: Import.Value):
  export value.*

object Import:
  val kind = "DATABASE"

  case class Value(provider: Ident, share: Ident, roles: List[Ident])

  given CDA[Import]:
    extension (obj: Import)
      private def permit(roles: Seq[Ident], f: Permit[String] => SqlStmt) =
        Chain.fromSeq(roles).map(r => Permit(show"IMPORTED PRIVILEGES ON DATABASE ${obj.name}", RoleName.Account(r))).map(f)

      def sameId(other: Import): Boolean  = obj.name == other.name
      def updatable(old: Import): Boolean = obj.provider == old.provider && obj.share == old.share

      def create: Chain[SqlStmt] =
        show"${kind.cr} ${obj.name} FROM SHARE ${obj.provider}.${obj.share}".ddl.copy(isForeign = true)
          +: obj.permit(obj.roles, _.grant)

      def drop: Chain[SqlStmt] =
        obj.permit(obj.roles, _.revoke).reverse
          :+ show"${kind.dr} ${obj.name}".ddl.copy(isForeign = true)

      def update(old: Import): Chain[SqlStmt] =
        obj.permit(old.roles -- obj.roles, _.revoke).reverse
          ++ obj.permit(obj.roles -- old.roles, _.grant)
