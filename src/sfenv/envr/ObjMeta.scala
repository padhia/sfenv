package sfenv
package envr

import cats.Show
import cats.data.Chain
import cats.syntax.all.*

case class ObjMeta(props: Props, tags: Tags, comment: Option[SqlLiteral]):
  import SqlStmt.*

  def extProps = comment.map(x => props.updated(Ident("COMMENT"), PropVal.Str(x.show))).getOrElse(props)

  def ddl(prefix: String, old: ObjMeta): Chain[SqlStmt] = sql(prefix, old).map(_.ddl)
  def dcl(prefix: String, old: ObjMeta): Chain[SqlStmt] = sql(prefix, old).map(_.dcl)

  def sql(prefix: String, old: ObjMeta): Chain[String] =
    val (setProps, unsetProps) = extProps -- old.extProps
    val (setTags, unsetTags)   = tags -- old.tags

    Chain(
      setProps.wrapped(show"$prefix SET"),
      unsetProps.wrapped(show"$prefix UNSET"),
      setTags.wrapped(show"$prefix SET TAG"),
      unsetTags.wrapped(show"$prefix UNSET TAG"),
    ).collect { case Some(x) => x }

  def ddl(prefix: String): SqlStmt = sql(prefix).ddl
  def dcl(prefix: String): SqlStmt = sql(prefix).dcl

  def sql(prefix: String): String =
    val tagsText = Chain.fromOption(tags.words.wrapped("WITH TAG", ", "))
    (extProps.words ++ tagsText).wrapped(prefix).getOrElse(prefix)

object ObjMeta:
  def empty = apply()

  def apply(props: Props = Props.empty, tags: Option[Tags] = None, comment: Option[SqlLiteral] = None): ObjMeta =
    apply(props, tags.getOrElse(Tags.empty), comment)
