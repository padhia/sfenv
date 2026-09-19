package sfenv
package envr

import cats.data.Chain
import cats.syntax.all.*

import SqlStmt.*

case class User(name: Ident, meta: ObjMeta = ObjMeta.empty, createObj: Boolean = true, defaultNamespace: Option[String] = None)

object User:
  val kind = "USER"

  given Ordering[User] = Ordering.by(_.name)

  given CDA[User]:
    extension (obj: User)
      def sameId(other: User): Boolean  = obj.name == other.name
      def updatable(old: User): Boolean = true

      def create: Chain[SqlStmt] =
        if obj.createObj then
          val namespace = obj.defaultNamespace.toList.map(value => "DEFAULT_NAMESPACE" -> value)
          Chain(obj.meta.sql(show"${kind.cr} ${obj.name}", namespace).dcl)
        else Chain.empty

      def drop: Chain[SqlStmt] =
        (if obj.createObj then Chain(show"${kind.dr} ${obj.name}".dcl) else Chain.empty)

      def update(old: User): Chain[SqlStmt] =
        val prefix          = show"${kind.alt} ${obj.name}"
        val namespaceChange =
          if obj.defaultNamespace == old.defaultNamespace then Chain.empty
          else
            obj.defaultNamespace match
              case Some(value) => Chain(s"$prefix SET DEFAULT_NAMESPACE = $value".dcl)
              case None        => Chain(s"$prefix UNSET DEFAULT_NAMESPACE".dcl)
        obj.meta.dcl(prefix, old.meta) ++ namespaceChange
