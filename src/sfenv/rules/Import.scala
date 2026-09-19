package sfenv
package rules

import cats.syntax.all.*

import fabric.rw.*

case class Import(name: String, provider: Ident, share: Ident, roles: List[Ident] = Nil) derives RW:
  def asEnvr(using resolver: NameResolver) =
    envr.Import(resolver.db(name), provider, share, roles.map(role => resolver.fn(role.show)))

object Import:
  given Ordering[Import] = Ordering.by(_.name)
