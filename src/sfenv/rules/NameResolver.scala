package sfenv
package rules

trait NameResolver:
  def env: String
  def secAdmin: Ident
  def dbAdmin: Ident
  def db(db: String): Ident
  def sch(db: String, sch: String): Ident
  def wh(wh: String): Ident
  def acc(db: String, sch: String, acc: String): Ident
  def wacc(wh: String, acc: String): Ident
  def fn(rl: String): Ident
  def app(app: String): Ident
  def cp(cp: String): Ident
