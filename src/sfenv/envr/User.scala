package sfenv
package envr

import cats.data.Chain
import cats.syntax.all.*

import SqlStmt.*

case class User(name: Ident, value: User.Value):
  export value.*

object User:
  case class Value(meta: ObjMeta = ObjMeta.empty, createObj: Boolean = true)

  val kind = "USER"

  given CDA[User]:
    extension (obj: User)
      def sameId(other: User): Boolean  = obj.name == other.name
      def updatable(old: User): Boolean = true

      def create: Chain[SqlStmt] =
        (if obj.createObj then Chain(obj.meta.dcl(show"${kind.cr} ${obj.name}")) else Chain.empty)

      def drop: Chain[SqlStmt] =
        (if obj.createObj then Chain(show"${kind.dr} ${obj.name}".dcl) else Chain.empty)

      def update(old: User): Chain[SqlStmt] = obj.meta.dcl(show"${kind.alt} ${obj.name}", old.meta)
