package sfenv
package rules

import fabric.*
import fabric.define.DefType
import fabric.rw.*

import scala.collection.immutable.ListMap

import envr.ObjMeta

case class Schema(
    transient: Option[Boolean],
    managed: Option[Boolean],
    acc_roles: Option[AccRoles],
    tags: Option[Tags],
    comment: Option[SqlLiteral],
    props: Props,
):
  def resolve(dbName: String, schName: String)(using n: NameResolver) =
    (
      n.sch(dbName, schName),
      envr.Schema.Value(
        transient  = transient.getOrElse(false),
        managed    = managed.getOrElse(false),
        meta       = ObjMeta(props, tags, comment),
        accRoleMap = acc_roles.map(_.resolve(dbName, schName)).getOrElse(ListMap.empty)
      )
    )

object Schema:
  given RW[Schema] = RW.from(
    r = _ => throw UnsupportedOperationException("Schema serialization not supported"),
    w = json => Schema(
      transient = json.attr("transient").as[Option[Boolean]],
      managed   = json.attr("managed").as[Option[Boolean]],
      acc_roles = json.attr("acc_roles").as[Option[AccRoles]],
      tags      = json.attr("tags").as[Option[Tags]],
      comment   = json.attr("comment").as[Option[SqlLiteral]],
      props     = json.props[Schema],
    ),
    d = DefType.Json
  )
