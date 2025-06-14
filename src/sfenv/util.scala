package sfenv

import scala.collection.immutable.ListMap

extension [T](xs: List[T]) def --(ys: List[T]) = xs.filterNot(ys.contains(_))

enum Admin:
  case Sec, Sys

type EnvName = String

type Props = ListMap[Ident, PropVal]
object Props:
  def apply(values: (String, PropVal.PropType)*): Props = ListMap.from(values.map((x, y) => (Ident(x), PropVal(y))))
  def empty                                             = ListMap.empty[Ident, PropVal]

type Tags = ListMap[Ident, SqlLiteral]
object Tags:
  def apply(values: (String, String)*): Tags = ListMap.from(values.map((x, y) => (Ident(x), SqlLiteral(y))))
  def empty: Tags                            = ListMap.empty
