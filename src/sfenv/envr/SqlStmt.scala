package sfenv
package envr

import cats.Show
import cats.data.Chain
import cats.syntax.all.*

type FromRole = (Ident, Ident) => String

enum Sql:
  case Txt(text: String)              extends Sql
  case Sys(template: Ident => String) extends Sql
  case Sec(template: Ident => String) extends Sql

case class SqlStmt(use: Admin, text: Sql, forAll: Boolean = false, isForeign: Boolean = false):
  def resolve(sysAdmin: Ident, secAdmin: Ident): (Ident, String) =
    val useId = use match
      case Admin.Sys => sysAdmin
      case Admin.Sec => secAdmin

    val textId = text match
      case Sql.Txt(x) => x
      case Sql.Sys(f) => f(sysAdmin)
      case Sql.Sec(f) => f(secAdmin)

    (useId, textId)

object SqlStmt:
  extension (sql: String)
    def ddl: SqlStmt = SqlStmt(Admin.Sys, Sql.Txt(sql))
    def dcl: SqlStmt = SqlStmt(Admin.Sec, Sql.Txt(sql))
    def cr: String   = show"CREATE $sql IF NOT EXISTS"
    def dr: String   = show"DROP $sql IF EXISTS"
    def alt: String  = show"ALTER $sql IF EXISTS"

  extension (stmts: Chain[SqlStmt])
    def resolve(sysAdm: Ident, secAdm: Ident): Chain[(Ident, String)] = stmts.map(_.resolve(sysAdm, secAdm))

  def sqlStream(sysAdmin: Ident, secAdmin: Ident)(xs: Chain[SqlStmt]): Chain[String] =
    xs.map(_.resolve(sysAdmin, secAdmin))
      .foldLeft((Option.empty[Ident], Chain.empty[String])): (acc, x) =>
        val (prev, ys) = acc
        val (curr, y)  = x

        if prev.map(_ == curr).getOrElse(false)
        then (prev, ys :+ (y + ";"))
        else (Some(curr), ys ++ Chain(show"USE ROLE $curr;", y + ";"))
      ._2
