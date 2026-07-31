package sfenv
package rules

import fabric.rw.*

case class Options(
    drop: Option[ProcessDrops] = None,
    only_futures: Option[Boolean] = None
) derives RW
