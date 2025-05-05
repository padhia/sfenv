package sfenv
package envr

import cats.data.Chain

import Sql.*

case class ComputePool(name: String, meta: ObjMeta)

object ComputePool:
  given SqlObj[ComputePool] with
    type Key = String

    extension (cp: ComputePool)
      override def id = cp.name

      override def create =
        Chain(Sql.CreateObj("COMPUTE POOL", cp.name, cp.meta.toString()))

      override def unCreate = Chain(Sql.DropObj("COMPUTE POOL", cp.name))

      override def alter(old: ComputePool) =
        cp.meta.alter("COMPUTE POOL", cp.name, old.meta)
