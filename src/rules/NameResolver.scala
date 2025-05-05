package sfenv
package rules

trait NameResolver:
  def env: String
  def secAdmin: String
  def dbAdmin: String
  def db(db: String): String
  def sch(db: String, sch: String): String
  def wh(wh: String): String
  def acc(db: String, sch: String, acc: String): String
  def wacc(wh: String, acc: String): String
  def fn(rl: String): String
  def app(app: String): String
  def cp(cp: String): String

object NameResolver:
  def makeUsing(cfg: Config, envName: String): NameResolver =
    import cfg.*

    def sub(template: String, subs: (String, String)*) =
      subs.foldLeft(template.replace("{env}", envName)) { case (s, (f, t)) => s.replace(s"{$f}", t) }

    new NameResolver:
      override val env: String                                       = envName
      override val secAdmin: String                                  = sub(secadm)
      override val dbAdmin: String                                   = sub(dbadm)
      override def db(db: String): String                            = sub(database, "db" -> db)
      override def sch(db: String, sch: String): String              = sub(schema, "sch" -> sch)
      override def wh(wh: String): String                            = sub(warehouse, "wh" -> wh)
      override def acc(db: String, sch: String, acc: String): String = sub(acc_role, "db" -> db, "sch" -> sch, "acc" -> acc)
      override def wacc(wh: String, acc: String): String             = sub(wacc_role, "wh" -> wh, "acc" -> acc)
      override def fn(rl: String): String                            = sub(fn_role, "role" -> rl)
      override def app(app: String): String                          = sub(app_id, "app" -> app)
      override def cp(cp: String): String                            = sub(cpool_, "cp" -> cp)
