package sfenv
package rules

import io.circe.*

case class Config(
    secadm: Option[String] = None,
    dbadm: Option[String] = None,
    database: Option[String] = None,
    schema: Option[String] = None,
    warehouse: Option[String] = None,
    acc_role: Option[String] = None,
    wacc_role: Option[String] = None,
    fn_role: Option[String] = None,
    app_id: Option[String] = None,
    cpool: Option[String] = None
) derives Decoder:

  def resolver(envName: String): NameResolver =
    Config.Resolver(
      env = envName,
      secadm = secadm.getOrElse("USERADMIN"),
      dbadm = dbadm.getOrElse("SYSADMIN"),
      database = database.getOrElse("{db}"),
      schema = schema.getOrElse("{sch}"),
      warehouse = warehouse.getOrElse("{wh}"),
      acc_role = acc_role.getOrElse("{sch}_{acc}"),
      wacc_role = wacc_role.getOrElse("_{wh}_{acc}"),
      fn_role = fn_role.getOrElse("{role}"),
      app_id = app_id.getOrElse("{app}"),
      cpool = cpool.getOrElse("{cp}")
    )

object Config:
  case class Resolver(
      env: String,
      secadm: String,
      dbadm: String,
      database: String,
      schema: String,
      warehouse: String,
      acc_role: String,
      wacc_role: String,
      fn_role: String,
      app_id: String,
      cpool: String,
  ) extends NameResolver:
    private def sub(x: String, subs: (String, String)*): Ident =
      def sub1(y: String, pat: String, value: String): String = y.replace(s"{$pat}", value)
      Ident(subs.foldLeft(sub1(x, "env", env))((s, pv) => sub1(s, pv._1, pv._2)))

    override val secAdmin: Ident = sub(secadm)
    override val dbAdmin: Ident  = sub(dbadm)

    override def db(db: String): Ident                            = sub(database, "db" -> db)
    override def sch(db: String, sch: String): Ident              = sub(schema, "db" -> db, "sch" -> sch)
    override def wh(wh: String): Ident                            = sub(warehouse, "wh" -> wh)
    override def acc(db: String, sch: String, acc: String): Ident = sub(acc_role, "db" -> db, "sch" -> sch, "acc" -> acc)
    override def wacc(wh: String, acc: String): Ident             = sub(wacc_role, "wh" -> wh, "acc" -> acc)
    override def fn(rl: String): Ident                            = sub(fn_role, "role" -> rl)
    override def app(app: String): Ident                          = sub(app_id, "app" -> app)
    override def cp(cp: String): Ident                            = sub(cpool, "cp" -> cp)
