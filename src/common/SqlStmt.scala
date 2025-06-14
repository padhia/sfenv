package sfenv

type FromRole = (String, String) => String

case class SqlStmt(use: Admin, text: String | FromRole, hasPast: Boolean = false)

// object SqlStmt:
//   def createObj(kind: String, name: String, meta: ObjMeta, options: String*): SqlStmt =
//     val opts = options.map(x => " " + x).mkString
//     SqlStmt(Admin.Sys, s"CREATE OR ALTER $kind $name$opts $meta")

//   def dropObj(kind: String, name: String): SqlStmt =
//     SqlStmt(Admin.Sys, s"DROP $kind IF EXISTS $name")
