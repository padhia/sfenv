package sfenv

import cats.effect.IO

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*

import fabric.*

@extern
@link("fyaml")
private object libfyaml:
  type FYDoc      = Ptr[Byte]
  type FYNode     = Ptr[Byte]
  type FYNodePair = Ptr[Byte]

  // Adjusted signatures to target valid libfyaml 0.9.6 symbols
  def fy_document_build_from_string(cfg: Ptr[Byte], str: CString, len: CSize): FYDoc = extern
  def fy_document_resolve(fyd: FYDoc): CInt                                          = extern
  def fy_document_root(fyd: FYDoc): FYNode                                           = extern
  def fy_document_destroy(fyd: FYDoc): Unit                                          = extern

  // Node Inspection Elements
  def fy_node_get_type(fyn: FYNode): CInt                                = extern
  def fy_node_resolve_alias(fyn: FYNode): FYNode                         = extern
  def fy_node_get_scalar(fyn: FYNode, lenOut: Ptr[CSize]): Ptr[Byte]     = extern
  def fy_node_sequence_get_by_index(fyn: FYNode, index: CInt): FYNode    = extern
  def fy_node_sequence_item_count(fyn: FYNode): CInt                     = extern
  def fy_node_mapping_get_by_index(fyn: FYNode, index: CInt): FYNodePair = extern
  def fy_node_mapping_item_count(fyn: FYNode): CInt                      = extern
  def fy_node_pair_key(fynp: FYNodePair): FYNode                         = extern
  def fy_node_pair_value(fynp: FYNodePair): FYNode                       = extern
  def fy_node_get_tag(fyn: FYNode, lenOut: Ptr[CSize]): Ptr[Byte]        = extern

object YamlParser:
  private val FYNT_SCALAR: Int   = 0
  private val FYNT_SEQUENCE: Int = 1
  private val FYNT_MAPPING: Int  = 2

  // fy_node_get_tag returns NULL for implicitly-typed scalars, so we fall back
  // to text matching for the YAML 1.2 core-schema null literals.
  private def isNullLiteral(text: String): Boolean =
    text == "null" || text == "Null" || text == "NULL" || text == "~"

  def apply(yamlStr: String): IO[Json] =
    IO.fromOption(
      Zone:
        val cStr = toCString(yamlStr)
        val fyd  = libfyaml.fy_document_build_from_string(null, cStr, -1L.toCSize)

        if fyd == null then None
        else if libfyaml.fy_document_resolve(fyd) != 0 then
          libfyaml.fy_document_destroy(fyd)
          None
        else
          val rootNode = libfyaml.fy_document_root(fyd)
          val scalaAst = if rootNode != null then Some(convertNode(rootNode)) else None
          libfyaml.fy_document_destroy(fyd)
          scalaAst
    )(AppError.RulesParsingError("Error parsing YAML/JSON"))

  extension (f: (Ptr[Byte], Ptr[USize]) => Ptr[Byte])
    private def callAsScala(value: Ptr[Byte]): String =
      Zone:
        given Zone = summon[Zone]

        val lenPtr = alloc[CSize]()
        val cText  = f(value, lenPtr)

        if cText == null || !lenPtr == 0 then ""
        else
          val length = !lenPtr
          val bytes  = new Array[Byte](length.toInt)
          var i      = 0

          while i < length.toInt do
            bytes(i) = cText(i.toLong)
            i += 1
          new String(bytes, "UTF-8")

  private def convertNode(fyn: libfyaml.FYNode): Json =
    libfyaml.fy_node_get_type(fyn) match
      case FYNT_SCALAR =>
        val tag  = libfyaml.fy_node_get_tag.callAsScala(fyn)
        val text = libfyaml.fy_node_get_scalar.callAsScala(fyn)

        if tag.endsWith(":int") then NumInt(text.toLong)
        else if tag.endsWith(":bool") then Bool(text.toLowerCase == "true" || text.toLowerCase == "yes")
        else if tag.endsWith(":float") then NumDec(text.toDouble)
        else if tag.endsWith(":null") || (tag.isEmpty && isNullLiteral(text)) then Null
        else Str(text)

      case FYNT_SEQUENCE =>
        val count = libfyaml.fy_node_sequence_item_count(fyn)
        val items = (0 until count).map { i =>
          val childNode = libfyaml.fy_node_sequence_get_by_index(fyn, i)
          convertNode(childNode)
        }.toVector
        Arr(items)

      case FYNT_MAPPING =>
        val count = libfyaml.fy_node_mapping_item_count(fyn)
        val pairs = (0 until count).flatMap { i =>
          val pair    = libfyaml.fy_node_mapping_get_by_index(fyn, i)
          val keyText = libfyaml.fy_node_get_scalar.callAsScala(libfyaml.fy_node_pair_key(pair))

          if keyText.nonEmpty then Some(keyText -> convertNode(libfyaml.fy_node_pair_value(pair)))
          else None
        }.toMap
        Obj(pairs)

      case _ => Null
