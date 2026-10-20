package sfenv
package git

/** A state of a file in a git repository */
enum Rev:
  case Index
  case Commit(spec: String)
  case WorkDir
