package sfenv
package rules

import io.circe.*

case class Options(
    drop: Option[ProcessDrops] = None,
    only_futures: Option[Boolean] = None
) derives Decoder
