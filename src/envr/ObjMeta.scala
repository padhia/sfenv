package sfenv
package envr

import cats.data.Chain
import cats.syntax.all.*

case class ObjMeta(props: Props, tags: Map[String, String], comment: Option[String]):
  import ObjMeta.*
  import Props.propsToStrSeq

  override def toString(): String =
    val p = props.propsToStrSeq
    val t = tags.asTagList
    val c = comment.commentToStrSeq

    val ddl = (p ++ t ++ c).map(x => s" $x").mkString_("")
    if ddl.length <= 80 then ddl else (p ++ t ++ c).map(x => s"\n    $x").mkString_("") // try to avoid long DDL texts

  def alter(prev: ObjMeta): Chain[String] =
    val propChgs =
      import Props.toChain
      val set =
        val xs = props.filterNot((k, v) => prev.props.get(k) == Some(v)).toChain
        if xs.isEmpty then xs else Chain("SET " + xs.mkString_(", "))
      val unset =
        val xs = prev.props.keySet -- props.keySet
        if xs.isEmpty then Chain.empty else Chain("UNSET " + xs.mkString(", "))
      set ++ unset

    val commentChgs = (comment, prev.comment) match
      case (Some(x), Some(y)) => if x == y then Chain.empty else Chain(s"SET COMMENT = '$x'")
      case (Some(x), None)    => Chain(s"SET COMMENT = '$x'")
      case (None, Some(_))    => Chain(s"UNSET COMMENT")
      case (None, None)       => Chain.empty

    propChgs ++ commentChgs

  def alter(objType: String, objName: String, old: ObjMeta): Chain[Sql] =
    def emit(xs: Chain[String], verb: String) =
      if xs.isEmpty then Chain.empty
      else Chain(Sql.AlterObj(objType, objName, s" $verb ${xs.mkString_(", ")}"))

    val setProps   = (props.filterNot((k, v) => old.props.get(k) == Some(v))).propsToStrSeq
    val unsetProps = Chain.fromSeq((old.props -- props.keys).keys.toSeq)
    val setTags    = tagsToStrSeq(tags -- old.tags.keys)
    val unsetTags  = Chain.fromSeq((old.tags -- tags.keys).keys.toSeq)

    val setComment   = if comment == old.comment then Chain.empty else comment.commentToStrSeq
    val unsetComment = if comment.isEmpty && old.comment.isDefined then Chain("COMMENT") else Chain.empty

    emit(setProps ++ setComment, "SET") ++
      emit(unsetProps ++ unsetComment, "UNSET") ++
      emit(setTags, "SET TAG") ++
      emit(unsetTags, "UNSET TAG")

object ObjMeta:
  def apply(props: Props = Props.empty, tags: Map[String, String] = Map.empty, comment: Option[String] = None): ObjMeta =
    new ObjMeta(props.map((k, v) => (k.toUpperCase, v)), tags.map((k, v) => (k.toUpperCase, v)), comment)

  def apply(props: Props, tags: Option[Map[String, String]], comment: Option[String]): ObjMeta =
    apply(props, tags.getOrElse(Map.empty), comment)

  def empty = apply()

  extension (x: Option[String]) def commentToStrSeq = Chain.fromOption(x).map(y => s"COMMENT = '$y'")
  extension (xs: Map[String, String])
    def tagsToStrSeq: Chain[String] = Chain.fromSeq(xs.toSeq).map((k, v) => s"$k = ${v.asSqlLiteral}")

    def asTagList: Chain[String] =
      if xs.isEmpty then Chain.empty
      else Chain(s"""WITH TAG ${xs.tagsToStrSeq.mkString_(", ")}""")
