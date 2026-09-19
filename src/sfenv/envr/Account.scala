package sfenv
package envr

import cats.data.Chain
import cats.syntax.all.*

import fabric.*
import fabric.rw.*

case class Account(params: Props) derives RW

object Account:
  val kind = "ROLE"

  type Prop = (String, Json)

  given CDA[Prop]:
    extension (property: Prop)
      def sameId(other: Prop): Boolean  = property._1 == other._1
      def updatable(old: Prop): Boolean = true

      def create: Chain[SqlStmt] =
        val rendered = property._2 match
          case Str(value, _) => s"'$value'"
          case value         => Props.renderValue(value)
        Chain(SqlStmt(Admin.AccAdm, Sql.Txt(show"ALTER ACCOUNT SET ${Ident(property._1)} = $rendered")))

      def drop: Chain[SqlStmt] =
        Chain(SqlStmt(Admin.AccAdm, Sql.Txt(show"ALTER ACCOUNT UNSET ${Ident(property._1)}")))

      def update(old: Prop): Chain[SqlStmt] = create

  given CDA[Props]:
    extension (props: Props)
      def sameId(other: Props): Boolean      = true
      def updatable(old: Props): Boolean     = true
      def create: Chain[SqlStmt]             = summon[CDA[List[Prop]]].create(props.entries)
      def drop: Chain[SqlStmt]               = summon[CDA[List[Prop]]].drop(props.entries)
      def update(old: Props): Chain[SqlStmt] = summon[CDA[List[Prop]]].update(props.entries)(old.entries)

  given CDA[Account]:
    extension (x: Account)
      def sameId(y: Account): Boolean        = true
      def updatable(y: Account): Boolean     = true
      def create: Chain[SqlStmt]             = x.params.create
      def drop: Chain[SqlStmt]               = summon[CDA[Props]].drop(x.params)
      def update(y: Account): Chain[SqlStmt] = x.params.update(y.params)
