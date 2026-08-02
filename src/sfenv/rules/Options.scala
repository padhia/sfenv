package sfenv
package rules

import fabric.rw.*

case class Options(drop: GenDrop = GenDrop.Local, only_futures: Boolean = false) derives RW:
  def genGrant = if only_futures then GenGrant.Future else GenGrant.All
