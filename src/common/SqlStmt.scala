package sfenv
import envr.ObjMeta

case class SqlStmt(use: Admin, text: String)

object SqlStmt:
  def createObj(kind: String, name: String, meta: ObjMeta, options: String*): SqlStmt =
    val opts = options.map(x => " " + x).mkString
    SqlStmt(Admin.Sys, s"CREATE OR ALTER $kind $name$opts $meta")

  def dropObj(kind: String, name: String): SqlStmt =
    SqlStmt(Admin.Sys, s"DROP $kind IF EXISTS $name")
