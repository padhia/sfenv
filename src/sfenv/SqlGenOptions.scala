package sfenv

import rules.Options

case class SqlGenOptions(drop: ProcessDrops, onlyFutures: Boolean)

object SqlGenOptions:
  def coalesce(opts: Options*): SqlGenOptions =
    SqlGenOptions(
      drop = opts.find(_.drop.isDefined).flatMap(_.drop).getOrElse(ProcessDrops.NonLocal),
      onlyFutures = opts.find(_.only_futures.isDefined).flatMap(_.only_futures).getOrElse(false)
    )
