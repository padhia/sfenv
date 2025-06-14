package sfenv
package envr

import cats.Show
import cats.data.Chain
import cats.syntax.all.*

type UserName = Ident
type SchName  = (db: Ident, sch: Ident)

def concat(xs: (String | Option[String])*): String =
  xs.collect:
    case x: String => x
    case Some(x)   => x
  .mkString(" ")
    .replace(" \n", "\n")

extension [T](xs: Seq[T]) def --(ys: Seq[T]): Seq[T] = xs.filterNot(ys.contains(_))

extension [V: Show](xm: Map[Ident, V])
  def --(ym: Map[Ident, V]): (Chain[String], Chain[String]) =
    (
      xm.filterNot((k, v) => ym.get(k).map(_ == v).getOrElse(false)).words,
      Chain.fromSeq((ym.keySet -- xm.keySet).toSeq.map(_.show))
    )

  def words: Chain[String] = Chain.fromSeq(xm.map((k, v) => show"${k.show} = ${v.show}").toList)

extension (xs: Chain[String])
  def wrapped(pfx: Option[String] = None, sep: String = " ", width: Int = 80): Option[String] =
    Option.unless(xs.isEmpty):
      val unWrapped = pfx.foldLeft(xs.mkString_(sep))((x, y) => show"$y $x")
      if unWrapped.length <= width then unWrapped
      else pfx.getOrElse("") + xs.mkString_("\n    ", show"\n   $sep", "")

  def wrapped(pfx: String): Option[String]              = xs.wrapped(Some(pfx))
  def wrapped(pfx: String, sep: String): Option[String] = xs.wrapped(Some(pfx), sep)

extension (x: String) def plural = if x.endsWith("Y") then x.stripSuffix("Y") + "IES" else x + "S"

extension [T](xs: Chain[T]) def through[U](f: Chain[T] => Chain[U]): Chain[U] = f(xs)
