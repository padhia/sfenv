package sfenv
package envr

import cats.data.Chain
import cats.syntax.all.*

case class ObjMeta(props: Props, tags: Map[String, String], comment: Option[String]):
  import ObjMeta.*
  import Props.*

  def toText: Option[String] = (props.propsToStrSeq ++ tags.asTagList ++ comment.commentToStrSeq).wrap

  def toText(old: ObjMeta): Chain[String] =
    val (setProps, unsetProps) = props.diff(old.props)

    val setTags   = tagsToStrSeq(tags -- old.tags.keys).wrap("SET TAG")
    val unsetTags = Chain.fromSeq((old.tags -- tags.keys).keys.toSeq).wrap("UNSET TAG")

    val setComment   = Chain.fromOption(Option.unless(this.comment == old.comment)(this.comment).flatten)
    val unsetComment = Chain.fromOption(Option.when(this.comment != old.comment && this.comment.isEmpty)("COMMENT"))

    Chain(
      (setProps ++ setComment).wrap("SET"),
      (unsetProps ++ unsetComment).wrap("UNSET"),
      setTags,
      unsetTags
    ).collect { case Some(x) => x }

  override def toString(): String = toText.getOrElse("")

  def alter(objType: String, objName: String, old: ObjMeta): Chain[Sql] =
    def emit(xs: Chain[String], verb: String) =
      if xs.isEmpty then Chain.empty
      else Chain(Sql.AlterObj(objType, objName, s" $verb ${xs.mkString_(", ")}"))

    val (setProps, unsetProps) = props.diff(old.props)
    val setTags                = tagsToStrSeq(tags -- old.tags.keys)
    val unsetTags              = Chain.fromSeq((old.tags -- tags.keys).keys.toSeq)

    val setComment   = if comment == old.comment then Chain.empty else comment.commentToStrSeq
    val unsetComment = if comment.isEmpty && old.comment.isDefined then Chain("COMMENT") else Chain.empty

    emit(setProps ++ setComment, "SET") ++
      emit(unsetProps ++ unsetComment, "UNSET") ++
      emit(setTags, "SET TAG") ++
      emit(unsetTags, "UNSET TAG")

object ObjMeta:
  extension (xs: Chain[String])
    def wrap: Option[String] = Option.unless(xs.isEmpty)(xs.mkString_(if xs.mkString_(" ").length() <= 80 then " " else "\n    "))
    def wrap(pfx: String): Option[String] = xs.wrap.map(x => s"$pfx $x")

  def apply(props: Props = Props.empty, tags: Map[String, String] = Map.empty, comment: Option[String] = None): ObjMeta =
    new ObjMeta(props.map((k, v) => (k.toUpperCase, v)), tags.map((k, v) => (k.toUpperCase, v)), comment)

  def apply(props: Props, tags: Option[Map[String, String]], comment: Option[String]): ObjMeta =
    apply(props, tags.getOrElse(Map.empty), comment)

  extension (x: Option[String]) def commentToStrSeq = Chain.fromOption(x).map(y => s"COMMENT = '$y'")
  extension (xs: Map[String, String])
    def tagsToStrSeq: Chain[String] = Chain.fromSeq(xs.toSeq).map((k, v) => s"$k = ${v.asSqlLiteral}")

    def asTagList: Chain[String] =
      if xs.isEmpty then Chain.empty
      else Chain(s"""WITH TAG ${xs.tagsToStrSeq.mkString_(", ")}""")
