package sfenv
package rules

import io.circe.*

case class Config(
    secadm: String,
    dbadm: String,
    database: String,
    schema: String,
    warehouse: String,
    acc_role: String,
    wacc_role: String,
    fn_role: String,
    app_id: String,
    cpool: Option[String]
) derives Decoder:
  def cpool_ = cpool.getOrElse("CP_{cp}")
