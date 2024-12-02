package sfenv
package rules

import io.circe.*

case class Options(
    create_users: Option[Boolean] = None,
    create_roles: Option[Boolean] = None,
    drop: Option[ProcessDrops] = None,
    only_futures: Option[Boolean] = None
) derives Decoder
