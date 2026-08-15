package sfenv
package rules

import cats.syntax.all.*

import fabric.rw.*

case class Import(provider: Ident, share: Ident, roles: Option[List[Ident]]) derives RW

object Import:
  given ObjMap[Import]:
    type Key   = Ident
    type Value = envr.Import.Value

    extension (r: Import)
      def keyVal(k: String)(using n: NameResolver) =
        (
          n.db(k),
          envr.Import.Value(r.provider, r.share, r.roles.map(_.map(r => n.fn(r.show))).getOrElse(List.empty))
        )
