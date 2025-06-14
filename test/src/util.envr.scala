package sfenv
package envr
package test

import cats.data.Chain

extension (xs: Chain[SqlStmt]) def sqls = xs.resolve(Ident("DEV_SYSADM"), Ident("DEV_SECADM")).map(_._2).toList
