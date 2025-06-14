package sfenv
package rules

trait ObjMap[R]:
  type Key
  type Value
  extension (r: R) def keyVal(k: String)(using n: NameResolver): (Key, Value)
