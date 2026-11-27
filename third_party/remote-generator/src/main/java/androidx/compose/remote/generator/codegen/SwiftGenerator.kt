/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.remote.generator.codegen

import androidx.compose.remote.generator.AstProperty
import androidx.compose.remote.generator.DerivedExpr
import androidx.compose.remote.generator.OperationSchema
import androidx.compose.remote.generator.SchemaBitField
import androidx.compose.remote.generator.SchemaConditionalSkip
import androidx.compose.remote.generator.SchemaField
import androidx.compose.remote.generator.SchemaOperation
import androidx.compose.remote.generator.SchemaSparseParam
import androidx.compose.remote.generator.SchemaStruct

/**
 * Generator that transforms an [OperationSchema] into zero-Foundation, Swift 6-ready wire bindings:
 * - `OpCode` enum with `UInt8` raw values (`Equatable, Hashable, Sendable`)
 * - Typed `WireError` enum (`Error, Equatable, Hashable, Sendable`)
 * - Pure-Swift `[UInt8]`-backed `WireBuffer` with typed `throws(WireError)`
 * - Auxiliary structs and operation structs (`Equatable, Hashable, Sendable`)
 * - Closed `WireOperation` enum with one case per schema operation
 * - `WireDocument` container with lossless legacy and modern v1.1+ header round-tripping
 */
// LOCAL PATCH (rc-players): rc-players' fork names every wire value in the generated Swift
// (`WireLimits`, `BitLayout`, `SparseParameterID`, `HeaderLayout`, `WireExpressionConstants`, field
// defaults, `MemoryLayout` sizes), as its native-wire-constants rule asks. The hunks are marked.
internal class SwiftGenerator(private val schema: OperationSchema) {

    private val multiLang = MultiLangProtocolGenerator(schema)

    // LOCAL PATCH (rc-players): every wire value the templates below write is named. Upstream
    // writes limits, element sizes, sparse-parameter ids, enum values, bit layouts, field defaults
    // and the header's words as numbers; these name them from the schema, so the generated Swift
    // carries no wire magic numbers (AGENTS.md; `RcCodegenTest.swiftNamesEveryNumber`).

    private fun lc(name: String): String = multiLang.toSwiftLowerCamel(name)

    private fun constName(name: String): String = CodegenExprUtils.toSnakeCase(name).uppercase()

    /** The `WireLimits` name of each field's `maxLength`: its `javaLimit`, or one made for it. */
    private val limits: Pair<Map<SchemaField, String>, Map<String, Int>> by lazy {
        val names = HashMap<SchemaField, String>()
        val values = linkedMapOf<String, Int>()
        fun add(owner: String, f: SchemaField) {
            val max = f.maxLength ?: return
            val own = lc("${constName(owner)}_${constName(f.name)}_MAX_LENGTH")
            // A `javaLimit` that is an expression (`Limits.X * 2`) is not a name: the field gets one.
            var name =
                f.javaLimit
                    ?.removePrefix("Limits.")
                    ?.takeIf { it.matches(Regex("[A-Z_][A-Z0-9_]*")) }
                    ?.let { lc(it) } ?: own
            if (values[name]?.let { it != max } == true) name = own
            values[name] = max
            names[f] = name
        }
        schema.operations.forEach { op -> op.fields.forEach { add(op.name, it) } }
        schema.structs.values.forEach { st -> st.fields.forEach { add(st.name, it) } }
        names to values
    }

    private fun limitRef(f: SchemaField): String? = limits.first[f]?.let { "WireLimits.$it" }

    /** Each field's `minLength`, named `<owner><Field>MinLength` in `WireLimits`. */
    private val minimums: Pair<Map<SchemaField, String>, Map<String, Int>> by lazy {
        val names = HashMap<SchemaField, String>()
        val values = linkedMapOf<String, Int>()
        fun add(owner: String, f: SchemaField) {
            val min = f.minLength ?: return
            val name = lc("${constName(owner)}_${constName(f.name)}_MIN_LENGTH")
            values[name] = min
            names[f] = name
        }
        schema.operations.forEach { op -> op.fields.forEach { add(op.name, it) } }
        schema.structs.values.forEach { st -> st.fields.forEach { add(st.name, it) } }
        names to values
    }

    private fun minRef(f: SchemaField): String? = minimums.first[f]?.let { "WireLimits.$it" }

    private fun emitWireLimits(w: CodeWriter) {
        w.line("/// The reader's limits, by name: each field's `maxLength` (`Limits.*` where AndroidX")
        w.line("/// names it).")
        w.block("public enum WireLimits") {
            for ((name, value) in (limits.second + minimums.second).entries.sortedBy { it.key }) {
                line("public static let $name: Int = $value")
            }
            line("/// The most elements an array read reserves room for before it has read them.")
            line("public static let maxInitialCapacity: Int = $MAX_INITIAL_CAPACITY")
        }
        w.line()
    }

    /** The most elements a length prefix of [lengthType] can count, as Swift computes it. */
    private fun lengthTypeMax(lengthType: String): String =
        when (lengthType) {
            "byte" -> "Int(Int8.max)"
            "ubyte" -> "Int(UInt8.max)"
            "short" -> "Int(Int16.max)"
            "ushort" -> "Int(UInt16.max)"
            else -> "Int(Int32.max)"
        }

    /** The fewer of [limit] (a `WireLimits` name) and a length prefix's range, or either alone. */
    private fun effectiveMax(f: SchemaField): String? {
        val limit = limitRef(f)
        if (f.countFrom != null) return limit
        val prefix = lengthTypeMax(f.effectiveLengthType)
        return if (limit != null) "Swift.min($limit, $prefix)" else prefix
    }

    /** The fewest bytes one element of [elemType] takes on the wire, as Swift computes it. */
    private fun elementBytes(elemType: String): String =
        when (elemType) {
            "int", "int_id", "color", "float", "float_or_id", "string", "bytes" ->
                "MemoryLayout<Int32>.size"
            "short" -> "MemoryLayout<Int16>.size"
            "byte", "boolean" -> "MemoryLayout<UInt8>.size"
            "long" -> "MemoryLayout<Int64>.size"
            else ->
                if (schema.structs.containsKey(elemType)) "$elemType.minWireBytes"
                else "MemoryLayout<Int32>.size"
        }

    /** A sparse key's size: a sparse entry is a key and at least one byte of value. */
    private fun sparseKeyBytes(keyType: String): String =
        when (keyType) {
            "int" -> "MemoryLayout<Int32>.size"
            "short", "ushort" -> "MemoryLayout<Int16>.size"
            else -> "MemoryLayout<UInt8>.size"
        }

    /** [value] as the owner's own enum constant, which the struct emits by that name. */
    private fun enumRef(ownerName: String, fields: List<SchemaField>, value: Int): String {
        val name =
            multiLang.collectEnumConstants(fields).firstOrNull { it.second == value }?.first
                ?: error("$ownerName has no enum constant for $value")
        return "$ownerName.${lc(name)}"
    }

    private fun bitBase(field: String, bit: String): String =
        field + bit.replaceFirstChar { it.uppercaseChar() }

    private fun hex(value: Long): String = "0x" + value.toString(16).uppercase()

    /** The `BitLayout` constants of [fields]' bit ranges, reserved bits and skip condition. */
    private fun emitBitLayout(w: CodeWriter, fields: List<SchemaField>, skip: SchemaConditionalSkip?) {
        val out = linkedMapOf<String, String>()
        fun range(base: String, b: SchemaBitField) {
            if (b.bitWidth >= 32) return
            out["${base}Shift"] = "${b.lowBit}"
            out["${base}Mask"] = hex(b.bitMask)
            if (b.signed) {
                out["${base}LeftShift"] = "${31 - b.highBit}"
                out["${base}RightShift"] = "${31 - b.highBit + b.lowBit}"
            }
        }
        fun bit(field: String, b: SchemaBitField) {
            val base = bitBase(field, b.name)
            range(base, b)
            b.whenBitSet?.let { c ->
                out["${base}NarrowFlagShift"] = "${c.bit}"
                range("${base}Narrow", SchemaBitField(name = b.name, range = c.range, signed = false))
            }
        }
        for (f in fields) {
            f.bits?.forEach { bit(f.name, it) }
            f.mustBeZeroBits?.let { r ->
                val low = r.substringBefore("..").toInt()
                val high = r.substringAfter("..", r).toInt()
                val width = high - low + 1
                val mask = if (width >= 32) 0xFFFFFFFFL else ((1L shl width) - 1L) shl low
                out["${f.name}ReservedMask"] = hex(mask)
            }
        }
        skip?.let { bit(it.conditionField, skipBit(it)) }
        if (out.isEmpty()) return
        w.line("/// The shifts and masks of this type's packed fields, from the schema's bit ranges.")
        w.block("public enum BitLayout") {
            for ((name, value) in out) line("public static let $name: UInt32 = $value")
        }
    }

    private fun skipBit(cs: SchemaConditionalSkip): SchemaBitField =
        SchemaBitField(name = "condition", range = cs.conditionBits, signed = cs.conditionSigned)

    /** [bit] of [field] read out of [src], as `CodegenExprUtils.extractBitExpr` does, by name. */
    private fun swiftExtract(src: String, field: String, bit: SchemaBitField): String {
        val base = bitBase(field, bit.name)
        val normal = swiftExtractRange(src, base, bit)
        val c = bit.whenBitSet ?: return normal
        val narrow =
            swiftExtractRange(src, "${base}Narrow", SchemaBitField(name = bit.name, range = c.range))
        return "(((UInt32(bitPattern: $src) >> BitLayout.${base}NarrowFlagShift) & 1) != 0 ? $narrow : $normal)"
    }

    private fun swiftExtractRange(src: String, base: String, b: SchemaBitField): String {
        if (b.bitWidth >= 32) return src
        return if (b.signed) {
            "(Int32(bitPattern: UInt32(bitPattern: $src) << BitLayout.${base}LeftShift) >> BitLayout.${base}RightShift)"
        } else {
            "Int32((UInt32(bitPattern: $src) >> BitLayout.${base}Shift) & BitLayout.${base}Mask)"
        }
    }

    private fun swiftPack(field: String, terms: List<Pair<String, SchemaBitField>>): String {
        if (terms.isEmpty()) return "0"
        return "Int32(bitPattern: " +
            terms.joinToString(" | ") { (v, b) ->
                val base = bitBase(field, b.name)
                if (b.bitWidth >= 32) "UInt32(bitPattern: Int32($v))"
                else "((UInt32(bitPattern: Int32($v)) & BitLayout.${base}Mask) << BitLayout.${base}Shift)"
            } + ")"
    }

    /** The largest value [b] holds, from its mask: what a length packed into it may reach. */
    private fun bitMax(field: String, b: SchemaBitField): String {
        if (b.bitWidth >= 32) return "Int(Int32.max)"
        val mask = "BitLayout.${bitBase(field, b.name)}Mask"
        return if (b.signed) "Int($mask >> 1)" else "Int($mask)"
    }

    private fun swiftReservedBits(src: String, field: String): String =
        "((UInt32(bitPattern: Int32($src)) & BitLayout.${field}ReservedMask) != 0)"

    // LOCAL PATCH (rc-players): a field's schema default by name, `<Owner>.default<Field>`.
    private val numericTypes =
        setOf("int", "int_id", "color", "short", "byte", "long", "float", "float_or_id")

    private fun defaultFields(fields: List<SchemaField>): List<SchemaField> =
        fields.filter { it.defaultValue != null && it.type in numericTypes }

    private fun defaultName(f: SchemaField): String = lc("DEFAULT_${constName(f.name)}")

    private fun defaultRef(owner: String, f: SchemaField): String = "$owner.${defaultName(f)}"

    private fun emitDefaults(w: CodeWriter, fields: List<SchemaField>) {
        // A field's minimum, which the reader checks, by name too.
        for (f in fields.filter { it.minValue != null }) {
            w.line("public static let ${lc("MIN_${constName(f.name)}")}: Int32 = ${f.minValue}")
        }
        for (f in defaultFields(fields)) {
            w.line(
                "public static let ${defaultName(f)}: ${mapToSwiftType(f.type, false)} = ${mapToSwiftDefault(f.type, false, f.defaultValue)}"
            )
        }
    }

    private fun initDefault(owner: String, fields: List<SchemaField>, prop: AstProperty): String {
        val f = defaultFields(fields).firstOrNull { it.name == prop.name }
        return if (f != null && !prop.isNullable) defaultRef(owner, f)
        else mapToSwiftDefault(prop.type, prop.isNullable, prop.defaultValue)
    }

    // LOCAL PATCH (rc-players): a condition with every value named. Upstream copies the schema's
    // expression into Swift as it stands, numbers and all; the fork's schema names them, and Swift
    // reads them from `WireExpressionConstants`. The fields are widened to `Int` so one constant
    // compares with a `UInt8`, an `Int16` or an `Int32` field alike.
    private fun swiftCondition(expr: String): String =
        // A bare name is a `Bool` the read computed (`skipTaken`): it is the condition as it stands.
        if (expr.trim().matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) expr.trim()
        else
            CodegenExprUtils.translateCondition(
                expr,
                ExprLang.SWIFT,
                constants =
                    schema.conditionConstants.keys.associateWith {
                        "$WIRE_EXPRESSION_CONSTANTS.${lc(it)}"
                    },
            ) {
                "Int($it)"
            }

    // LOCAL PATCH (rc-players): the constants [swiftCondition] names, from the schema.
    private fun emitWireExpressionConstants(w: CodeWriter) {
        if (schema.conditionConstants.isEmpty()) return
        w.line("/// Named values the schema's field conditions compare against: its `expressionConstants`")
        w.line("/// and its header constants.")
        w.block("public enum $WIRE_EXPRESSION_CONSTANTS") {
            for ((name, value) in schema.conditionConstants) {
                line("public static let ${lc(name)}: Int = $value")
            }
        }
        w.line()
    }

    // LOCAL PATCH (rc-players): the header's bit layout and keys, derived from the schema, so the
    // loader and the writer name every wire value instead of repeating it as a literal. A schema
    // without `Header` or `HeaderProperty` (a subset) gets upstream's values.
    private fun headerBit(owner: String, field: String, bit: String): SchemaBitField? =
        (schema.operationsByName[owner]?.fields ?: schema.structs[owner]?.fields)
            ?.firstOrNull { it.name == field }
            ?.bits
            ?.firstOrNull { it.name == bit }

    private fun emitHeaderLayout(w: CodeWriter) {
        val version = headerBit("Header", "majorVersion", "version")
        val magic = headerBit("Header", "majorVersion", "magic")
        val key = headerBit("HeaderProperty", "tag", "key")
        val dataType = headerBit("HeaderProperty", "tag", "dataType")
        val typeWidth = dataType?.bitWidth ?: UPSTREAM_PROPERTY_TYPE_BITS
        w.line("/// A header's bit layout, from the schema: the major-version word (version and magic) and")
        w.line("/// each property's tag (key and data type).")
        w.block("public enum HeaderLayout") {
            line("public static let versionMask: Int32 = ${hex(version?.let { it.bitMask shl it.lowBit } ?: 0xFFFFL)}")
            line("public static let magicMask: UInt32 = ${hex(magic?.let { it.bitMask shl it.lowBit } ?: 0xFFFF0000L)}")
            line("public static let propertyKeyMask: Int32 = ${hex(key?.let { it.bitMask shl it.lowBit } ?: 0x3FL)}")
            line("public static let propertyTypeShift: Int32 = ${dataType?.lowBit ?: UPSTREAM_PROPERTY_TYPE_SHIFT}")
            line("public static let propertyTypeMask: Int32 = ${hex((1L shl typeWidth) - 1L)}")
            line("/// The smallest and largest data type a tag's signed type bits hold.")
            line("public static let propertyTypeMin: Int32 = ${-(1 shl (typeWidth - 1))}")
            line("public static let propertyTypeMax: Int32 = ${(1 shl (typeWidth - 1)) - 1}")
            line("/// The fewest bytes a header property takes: its tag, its length and an int.")
            line(
                "public static let propertyMinWireBytes: Int = ${schema.structs["HeaderProperty"]?.let { CodegenExprUtils.minWireElementBytes(it.name, schema) } ?: UPSTREAM_PROPERTY_MIN_BYTES}"
            )
        }
        w.line()
        w.block("enum HeaderKey") {
            for ((name, value) in UPSTREAM_HEADER_KEYS) line("static let ${lc(name)}: Int32 = ${headerKeyValue(name, value)}")
        }
        w.line()
    }

    /** A header property key's value: the schema's `HeaderProperty` constant, else upstream's. */
    private fun headerKeyValue(name: String, fallback: Int): String {
        val tag = schema.structs["HeaderProperty"] ?: return "$fallback"
        return if (multiLang.collectEnumConstants(tag.fields).any { it.first == name }) {
            "HeaderProperty.${lc(name)}"
        } else {
            "$fallback"
        }
    }

    /** A header property key, by name. */
    private fun headerKey(name: String): String {
        check(name in UPSTREAM_HEADER_KEYS) { "no header key $name" }
        return "HeaderKey.${lc(name)}"
    }

    /** A header constant (`MAGIC_NUMBER`, `DATA_TYPE_INT`, …) by name, or upstream's value. */
    private fun headerConstant(name: String): String {
        if (schema.headerConstants.values.any { name in it }) return "HeaderConstants.${lc(name)}"
        return "${checkNotNull(UPSTREAM_HEADER_CONSTANTS[name]) { "no header constant $name" }}"
    }


    fun generate(): String = buildCode {
        line("// Generated by RemoteCompose Schema Codegen. DO NOT EDIT.")
        line()
        line("/// Operation wire identifiers for RemoteCompose.")
        block("public enum OpCode: UInt8, CaseIterable, Equatable, Hashable, Sendable") {
            for (op in schema.operations) {
                val swiftCase = toCamelCase(op.name)
                val desc = op.description.replace("\n", " ").trim()
                if (desc.isNotEmpty()) {
                    line("/// $desc")
                }
                line("case $swiftCase = ${op.id}")
            }
        }
        if (schema.reservedOpcodes.isNotEmpty()) {
            line()
            line("/// Reserved and experimental operation wire identifiers without wire codecs.")
            block(
                "public enum ReservedOpCode: UInt8, CaseIterable, Equatable, Hashable, Sendable"
            ) {
                for (rop in schema.reservedOpcodes) {
                    val swiftCase = toCamelCase(rop.name)
                    val desc = rop.description.replace("\n", " ").trim()
                    if (desc.isNotEmpty()) {
                        line("/// $desc")
                    }
                    line("case $swiftCase = ${rop.id}")
                }
            }
        }
        line()
        emitWireError(this)
        line()
        emitWireBuffer(this)
        line()
        emitFloatBitEqualityHelpers(this)
        line()
        multiLang.emitSwiftProtocol(this)
        for ((_, structDef) in schema.structs) {
            emitAuxStruct(this, structDef)
            line()
        }
        for (op in schema.operations) {
            emitStruct(this, op)
            line()
        }
        // LOCAL PATCH (rc-players): the named values the templates above refer to.
        emitWireExpressionConstants(this)
        emitWireLimits(this)
        emitHeaderLayout(this)
        emitWireOperationEnum(this)
        line()
        emitWireDocument(this)
    }

    private fun emitWireError(w: CodeWriter) {
        w.line("/// Typed error thrown while decoding or encoding RemoteCompose wire streams.")
        w.block("public enum WireError: Error, Equatable, Hashable, Sendable") {
            line("case underflow(needed: Int, remaining: Int)")
            line("case indexOutOfBounds(index: Int, count: Int)")
            line("case invalidLength(field: String, length: Int)")
            line("case lengthOutOfRange(field: String, length: Int, min: Int?, max: Int?)")
            line("case invalidUTF8(field: String)")
            line("case unknownOpCode(UInt8)")
            line("case expectedHeaderOpCode(actual: UInt8)")
            line("case unexpectedHeaderInBody")
            line("case invalidHeaderMagic(Int32)")
            line("case compressedDocumentUnsupported")
            line("case operationCountExceeded(limit: Int)")
            line("case invalidFieldValue(owner: String, field: String, detail: String)")
            line("case missingRequiredField(owner: String, field: String)")
            line("case unknownSparseParameter(owner: String, id: Int)")
            line("case invalidAnimationSpec(String)")
            line("case invalidPathEffect(String)")
        }
    }

    private fun emitWireBuffer(w: CodeWriter) {
        w.line("/// Big-endian binary reader and writer backed by a Swift byte array.")
        w.block("public struct WireBuffer: Equatable, Hashable, Sendable") {
            line("public private(set) var data: [UInt8]")
            line("public private(set) var index: Int = 0")
            line()
            block("public init(data: [UInt8] = [])") {
                line("self.data = data")
                line("self.index = 0")
            }
            line()
            block("public var remaining: Int") { line("return Swift.max(0, data.count - index)") }
            line()
            block("public var available: Bool") { line("return index < data.count") }
            line()
            block("public mutating func setIndex(_ newIndex: Int) throws(WireError)") {
                block("guard newIndex >= 0 && newIndex <= data.count else") {
                    line("throw WireError.indexOutOfBounds(index: newIndex, count: data.count)")
                }
                line("index = newIndex")
            }
            line()
            block("public mutating func skipBytes(_ count: Int) throws(WireError)") {
                block("guard count >= 0 else") {
                    line("throw WireError.invalidLength(field: \"skip\", length: count)")
                }
                block("guard count <= remaining else") {
                    line("throw WireError.underflow(needed: count, remaining: remaining)")
                }
                line("index += count")
            }
            line()
            block("public mutating func truncate(to count: Int)") {
                block("if count >= 0 && count < data.count") {
                    line("data.removeLast(data.count - count)")
                    block("if index > count") { line("index = count") }
                }
            }
            line()
            block("public mutating func writeByte(_ value: UInt8)") { line("data.append(value)") }
            line()
            block("public mutating func readByte() throws(WireError) -> UInt8") {
                block("guard 1 <= remaining else") {
                    line("throw WireError.underflow(needed: 1, remaining: remaining)")
                }
                line("let byte = data[index]")
                line("index += 1")
                line("return byte")
            }
            line()
            // LOCAL PATCH (rc-players): one big-endian writer and one reader, sized by the type they
            // carry, so no byte count or shift is written as a number.
            block(
                "private mutating func writeBigEndian<T: FixedWidthInteger & UnsignedInteger>(_ bits: T)"
            ) {
                block(
                    "for shift in stride(from: T.bitWidth - UInt8.bitWidth, through: 0, by: -UInt8.bitWidth)"
                ) {
                    line("data.append(UInt8(truncatingIfNeeded: bits >> shift))")
                }
            }
            line()
            block(
                "private mutating func readBigEndian<T: FixedWidthInteger & UnsignedInteger>(_ type: T.Type) throws(WireError) -> T"
            ) {
                line("let size = MemoryLayout<T>.size")
                block("guard size <= remaining else") {
                    line("throw WireError.underflow(needed: size, remaining: remaining)")
                }
                line("var bits: T = 0")
                block("for offset in 0..<size") {
                    line("bits = (bits << UInt8.bitWidth) | T(data[index + offset])")
                }
                line("index += size")
                line("return bits")
            }
            line()
            block("public mutating func writeShort(_ value: Int16)") {
                line("writeBigEndian(UInt16(bitPattern: value))")
            }
            line()
            block("public mutating func readShort() throws(WireError) -> Int16") {
                line("return Int16(bitPattern: try readBigEndian(UInt16.self))")
            }
            line()
            block("public mutating func writeInt(_ value: Int32)") {
                line("writeBigEndian(UInt32(bitPattern: value))")
            }
            line()
            block("public mutating func readInt() throws(WireError) -> Int32") {
                line("return Int32(bitPattern: try readBigEndian(UInt32.self))")
            }
            line()
            block("public mutating func writeLong(_ value: Int64)") {
                line("writeBigEndian(UInt64(bitPattern: value))")
            }
            line()
            block("public mutating func readLong() throws(WireError) -> Int64") {
                line("return Int64(bitPattern: try readBigEndian(UInt64.self))")
            }
            line()
            block("public mutating func writeFloat(_ value: Float)") {
                line("writeInt(Int32(bitPattern: value.bitPattern))")
            }
            line()
            block("public mutating func readFloat() throws(WireError) -> Float") {
                line("let bitVal = UInt32(bitPattern: try readInt())")
                line("return Float(bitPattern: bitVal)")
            }
            line()
            block("public mutating func writeBoolean(_ value: Bool)") {
                line("writeByte(value ? 1 : 0)")
            }
            line()
            block("public mutating func readBoolean() throws(WireError) -> Bool") {
                line("return (try readByte()) != 0")
            }
            line()
            block(
                "public mutating func writeUTF8(_ value: String, maxLength: Int? = nil) throws(WireError)"
            ) {
                line("let utf8Count = value.utf8.count")
                line(
                    "let limit = maxLength.map { Swift.min(\$0, Int(Int32.max)) } ?? Int(Int32.max)"
                )
                block("guard utf8Count <= limit else") {
                    line(
                        "throw WireError.lengthOutOfRange(field: \"utf8\", length: utf8Count, min: nil, max: limit)"
                    )
                }
                line("writeInt(Int32(utf8Count))")
                line("data.append(contentsOf: value.utf8)")
            }
            line()
            block(
                "public mutating func readUTF8(maxLength: Int? = nil) throws(WireError) -> String"
            ) {
                line("let len = Int(try readInt())")
                block("guard len >= 0 else") {
                    line("throw WireError.invalidLength(field: \"utf8\", length: len)")
                }
                block("if let maxLen = maxLength, len > maxLen") {
                    line(
                        "throw WireError.lengthOutOfRange(field: \"utf8\", length: len, min: nil, max: maxLen)"
                    )
                }
                block("guard len <= remaining else") {
                    line("throw WireError.underflow(needed: len, remaining: remaining)")
                }
                line("var scalars = String.UnicodeScalarView()")
                line("scalars.reserveCapacity(len)")
                line("var decoder = UTF8()")
                line("var iterator = data[index..<(index + len)].makeIterator()")
                block("while true") {
                    swiftSwitch("decoder.decode(&iterator)") {
                        swiftCase("case .scalarValue(let scalar)") {
                            line("scalars.append(scalar)")
                        }
                        swiftCase("case .emptyInput") {
                            line("index += len")
                            line("return String(scalars)")
                        }
                        swiftCase("case .error") {
                            line("throw WireError.invalidUTF8(field: \"utf8\")")
                        }
                    }
                }
            }
            line()
            block(
                "public mutating func writeBuffer(_ value: [UInt8], maxLength: Int? = nil) throws(WireError)"
            ) {
                line(
                    "let limit = maxLength.map { Swift.min(\$0, Int(Int32.max)) } ?? Int(Int32.max)"
                )
                block("guard value.count <= limit else") {
                    line(
                        "throw WireError.lengthOutOfRange(field: \"buffer\", length: value.count, min: nil, max: limit)"
                    )
                }
                line("writeInt(Int32(value.count))")
                line("data.append(contentsOf: value)")
            }
            line()
            block("public mutating func writeBytes(_ value: [UInt8])") {
                line("data.append(contentsOf: value)")
            }
            line()
            block("public mutating func readBytes(count: Int) throws(WireError) -> [UInt8]") {
                block("guard count >= 0 else") {
                    line("throw WireError.invalidLength(field: \"bytes\", length: count)")
                }
                block("guard count <= remaining else") {
                    line("throw WireError.underflow(needed: count, remaining: remaining)")
                }
                line("let bufData = Array(data[index..<(index + count)])")
                line("index += count")
                line("return bufData")
            }
            line()
            block(
                "public mutating func readBuffer(maxLength: Int? = nil) throws(WireError) -> [UInt8]"
            ) {
                line("let len = Int(try readInt())")
                block("guard len >= 0 else") {
                    line("throw WireError.invalidLength(field: \"buffer\", length: len)")
                }
                block("if let maxLen = maxLength, len > maxLen") {
                    line(
                        "throw WireError.lengthOutOfRange(field: \"buffer\", length: len, min: nil, max: maxLen)"
                    )
                }
                block("guard len <= remaining else") {
                    line("throw WireError.underflow(needed: len, remaining: remaining)")
                }
                line("let bufData = Array(data[index..<(index + len)])")
                line("index += len")
                line("return bufData")
            }
        }
    }

    private fun emitFloatBitEqualityHelpers(w: CodeWriter) {
        w.line("@inline(__always)")
        w.block("private func wireFloatEquals(_ lhs: Float, _ rhs: Float) -> Bool") {
            line("lhs.bitPattern == rhs.bitPattern")
        }
        w.line()
        w.line("@inline(__always)")
        w.block("private func wireFloatEquals(_ lhs: Float?, _ rhs: Float?) -> Bool") {
            line("lhs?.bitPattern == rhs?.bitPattern")
        }
        w.line()
        w.line("@inline(__always)")
        w.block("private func wireFloatEquals(_ lhs: [Float], _ rhs: [Float]) -> Bool") {
            line("guard lhs.count == rhs.count else { return false }")
            block("for i in lhs.indices") {
                line("if lhs[i].bitPattern != rhs[i].bitPattern { return false }")
            }
            line("return true")
        }
        w.line()
        w.line("@inline(__always)")
        w.block("private func wireFloatEquals(_ lhs: [Float]?, _ rhs: [Float]?) -> Bool") {
            swiftSwitch("(lhs, rhs)") {
                swiftCase("case (nil, nil)") { line("return true") }
                swiftCase("case (let a?, let b?)") { line("return wireFloatEquals(a, b)") }
                swiftCase("default") { line("return false") }
            }
        }
        w.line()
        w.line("@inline(__always)")
        w.block("private func wireFloatHash(_ value: Float, into hasher: inout Hasher)") {
            line("hasher.combine(value.bitPattern)")
        }
        w.line()
        w.line("@inline(__always)")
        w.block("private func wireFloatHash(_ value: Float?, into hasher: inout Hasher)") {
            line("hasher.combine(value?.bitPattern)")
        }
        w.line()
        w.line("@inline(__always)")
        w.block("private func wireFloatHash(_ value: [Float], into hasher: inout Hasher)") {
            line("hasher.combine(value.count)")
            block("for element in value") { line("hasher.combine(element.bitPattern)") }
        }
        w.line()
        w.line("@inline(__always)")
        w.block("private func wireFloatHash(_ value: [Float]?, into hasher: inout Hasher)") {
            block("if let array = value", close = null) {
                line("hasher.combine(true)")
                line("wireFloatHash(array, into: &hasher)")
            }
            block("} else") { line("hasher.combine(false)") }
        }
    }

    private fun isFloatProperty(prop: AstProperty): Boolean =
        prop.type == "float" ||
            prop.type == "float_or_id" ||
            prop.type == "float[]" ||
            prop.type == "float_or_id[]"

    private fun emitStructBitExactEquality(
        w: CodeWriter,
        structName: String,
        props: List<AstProperty>,
    ) {
        if (props.none { isFloatProperty(it) }) return
        w.line()
        w.block("public static func == (lhs: $structName, rhs: $structName) -> Bool") {
            if (props.size == 1) {
                val p = props[0]
                val cmp =
                    if (isFloatProperty(p)) "wireFloatEquals(lhs.${p.name}, rhs.${p.name})"
                    else "lhs.${p.name} == rhs.${p.name}"
                line("return $cmp")
            } else {
                for ((idx, p) in props.withIndex()) {
                    val cmp =
                        if (isFloatProperty(p)) "wireFloatEquals(lhs.${p.name}, rhs.${p.name})"
                        else "lhs.${p.name} == rhs.${p.name}"
                    if (idx == 0) {
                        line("return $cmp")
                    } else {
                        indent { line("&& $cmp") }
                    }
                }
            }
        }
        w.line()
        w.block("public func hash(into hasher: inout Hasher)") {
            for (p in props) {
                if (isFloatProperty(p)) {
                    line("wireFloatHash(self.${p.name}, into: &hasher)")
                } else {
                    line("hasher.combine(self.${p.name})")
                }
            }
        }
    }

    private fun emitWireOperationEnum(w: CodeWriter) {
        w.line("/// Closed union of all RemoteCompose wire operations.")
        w.block("public enum WireOperation: Equatable, Hashable, Sendable") {
            for (op in schema.operations) {
                val swiftCase = toCamelCase(op.name)
                line("case $swiftCase(${op.name})")
            }
            line()
            block("public var opCode: OpCode") {
                swiftSwitch("self") {
                    for (op in schema.operations) {
                        val swiftCase = toCamelCase(op.name)
                        line("case .$swiftCase: return .$swiftCase")
                    }
                }
            }
            line()
            block("public func write(to buffer: inout WireBuffer) throws(WireError)") {
                swiftSwitch("self") {
                    for (op in schema.operations) {
                        val swiftCase = toCamelCase(op.name)
                        line("case .$swiftCase(let op): try op.write(to: &buffer)")
                    }
                }
            }
        }
    }

    private fun emitWireDocument(w: CodeWriter) {
        w.line("/// Typed value stored in a modern (v1.1+) RemoteCompose header property.")
        w.block("public enum HeaderPropertyValue: Equatable, Hashable, Sendable") {
            line("case int(Int32)")
            line("case float(Float)")
            line("case long(Int64)")
            line("case string(String)")
            line("case unknown(dataType: Int32, payload: [UInt8])")
            line()
            block(
                "public static func == (lhs: HeaderPropertyValue, rhs: HeaderPropertyValue) -> Bool"
            ) {
                swiftSwitch("(lhs, rhs)") {
                    swiftCase("case (.int(let a), .int(let b))") { line("return a == b") }
                    swiftCase("case (.float(let a), .float(let b))") {
                        line("return wireFloatEquals(a, b)")
                    }
                    swiftCase("case (.long(let a), .long(let b))") { line("return a == b") }
                    swiftCase("case (.string(let a), .string(let b))") { line("return a == b") }
                    swiftCase(
                        "case (.unknown(let aDt, let aPayload), .unknown(let bDt, let bPayload))"
                    ) {
                        line("return aDt == bDt && aPayload == bPayload")
                    }
                    swiftCase("default") { line("return false") }
                }
            }
            line()
            block("public func hash(into hasher: inout Hasher)") {
                swiftSwitch("self") {
                    swiftCase("case .int(let val)") {
                        line("hasher.combine(\"int\")")
                        line("hasher.combine(val)")
                    }
                    swiftCase("case .float(let val)") {
                        line("hasher.combine(\"float\")")
                        line("wireFloatHash(val, into: &hasher)")
                    }
                    swiftCase("case .long(let val)") {
                        line("hasher.combine(\"long\")")
                        line("hasher.combine(val)")
                    }
                    swiftCase("case .string(let val)") {
                        line("hasher.combine(\"string\")")
                        line("hasher.combine(val)")
                    }
                    swiftCase("case .unknown(let dt, let payload)") {
                        line("hasher.combine(\"unknown\")")
                        line("hasher.combine(dt)")
                        line("hasher.combine(payload)")
                    }
                }
            }
        }
        w.line()
        w.line("/// Ordered header property entry in a modern (v1.1+) RemoteCompose document.")
        w.block("public struct WireHeaderProperty: Equatable, Hashable, Sendable") {
            line("public var key: Int32")
            line("public var value: HeaderPropertyValue")
            line()
            block("public init(key: Int32, value: HeaderPropertyValue)") {
                line("self.key = key")
                line("self.value = value")
            }
        }
        w.line()
        w.line("/// Wire-level representation of a RemoteCompose binary document.")
        w.block("public struct WireDocument: Equatable, Hashable, Sendable") {
            line("public static let maxHeaderProperties: Int = 1000")
            line("public static let maxOperationCount: Int = 100_000")
            line("public static let defaultDimension: Int32 = ${headerConstant("DEFAULT_DIMENSION")}")
            line("public static let magicNumber: Int32 = ${headerConstant("MAGIC_NUMBER")}")
            line("public static let maxHeaderStringLength: Int = 4000")
            line()
            line("public var majorVersion: Int32")
            line("public var minorVersion: Int32")
            line("public var patchVersion: Int32")
            line("public var width: Int32")
            line("public var height: Int32")
            line("public var capabilities: Int64")
            line("public var isModernHeader: Bool")
            line("public var properties: [WireHeaderProperty]")
            line("public var operations: [WireOperation]")
            line()
            block(
                "public init(",
                openSuffix = "",
                close = null,
            ) {
                line("majorVersion: Int32 = ${headerConstant("DEFAULT_MAJOR_VERSION")},")
                line("minorVersion: Int32 = ${headerConstant("DEFAULT_MINOR_VERSION")},")
                line("patchVersion: Int32 = ${headerConstant("DEFAULT_PATCH_VERSION")},")
                line("width: Int32? = nil,")
                line("height: Int32? = nil,")
                line("capabilities: Int64? = nil,")
                line("isModernHeader: Bool = false,")
                line("properties: [WireHeaderProperty] = [],")
                line("operations: [WireOperation] = []")
            }
            block(")") {
                line("let modern = isModernHeader || !properties.isEmpty")
                line("var resolvedWidth: Int32 = modern ? Self.defaultDimension : 0")
                line("var resolvedHeight: Int32 = modern ? Self.defaultDimension : 0")
                line("var resolvedCapabilities: Int64 = 0")
                block("for prop in properties") {
                    swiftSwitch("(prop.key, prop.value)") {
                        swiftCase("case (${headerKey("DOC_WIDTH")}, .int(let val))") { line("resolvedWidth = val") }
                        swiftCase("case (${headerKey("DOC_HEIGHT")}, .int(let val))") { line("resolvedHeight = val") }
                        swiftCase("case (${headerKey("DOC_PROFILES")}, .int(let val))") {
                            line("resolvedCapabilities = Int64(UInt32(bitPattern: val))")
                        }
                        swiftCase("case (${headerKey("DOC_PROFILES")}, .long(let val))") {
                            line("resolvedCapabilities = val")
                        }
                        swiftCase("default") { line("break") }
                    }
                }
                line("self.majorVersion = majorVersion")
                line("self.minorVersion = minorVersion")
                line("self.patchVersion = patchVersion")
                line("self.width = width ?? resolvedWidth")
                line("self.height = height ?? resolvedHeight")
                line("self.capabilities = capabilities ?? resolvedCapabilities")
                line("self.isModernHeader = modern")
                line("self.properties = properties")
                line("self.operations = operations")
            }
            line()
            line("/// Deserializes a WireDocument from raw binary bytes.")
            block(
                "public static func load(from data: [UInt8], maxOperations: Int = maxOperationCount) throws(WireError) -> WireDocument"
            ) {
                line(
                    "return try loadImpl(from: data, maxOperations: maxOperations, evaluateSkip: false, playerApiLevel: 0, playerProfiles: 0)"
                )
            }
            line()
            line(
                "/// Deserializes a WireDocument evaluating conditional skip operations against the target player."
            )
            block(
                "public static func loadForPlayer(",
                openSuffix = "",
                close = null,
            ) {
                line("from data: [UInt8],")
                line("playerApiLevel: Int32,")
                line("playerProfiles: Int32 = 0,")
                line("maxOperations: Int = maxOperationCount")
            }
            block(") throws(WireError) -> WireDocument") {
                block("return try loadImpl(", openSuffix = "", close = ")") {
                    line("from: data,")
                    line("maxOperations: maxOperations,")
                    line("evaluateSkip: true,")
                    line("playerApiLevel: playerApiLevel,")
                    line("playerProfiles: playerProfiles")
                }
            }
            line()
            block(
                "private static func loadImpl(",
                openSuffix = "",
                close = null,
            ) {
                line("from data: [UInt8],")
                line("maxOperations: Int,")
                line("evaluateSkip: Bool,")
                line("playerApiLevel: Int32,")
                line("playerProfiles: Int32")
            }
            block(") throws(WireError) -> WireDocument") {
                line("var buffer = WireBuffer(data: data)")
                line("var doc = WireDocument()")
                line("guard buffer.available else { return doc }")
                line()
                line("let headerOp = try buffer.readByte()")
                block("guard headerOp == OpCode.header.rawValue else") {
                    line("throw WireError.expectedHeaderOpCode(actual: headerOp)")
                }
                line("let rawMajor = try buffer.readInt()")
                line("doc.minorVersion = try buffer.readInt()")
                line("doc.patchVersion = try buffer.readInt()")
                block(
                    "if (UInt32(bitPattern: rawMajor) & HeaderLayout.magicMask) == UInt32(bitPattern: magicNumber)",
                    close = null,
                ) {
                    line("doc.isModernHeader = true")
                    line("doc.majorVersion = rawMajor & HeaderLayout.versionMask")
                    line("doc.width = defaultDimension")
                    line("doc.height = defaultDimension")
                    line("let numProps = Int(try buffer.readInt())")
                    block("guard numProps >= 0 && numProps <= maxHeaderProperties else") {
                        line(
                            "throw WireError.lengthOutOfRange(field: \"headerProperties\", length: numProps, min: 0, max: maxHeaderProperties)"
                        )
                    }
                    block("guard numProps <= buffer.remaining / HeaderLayout.propertyMinWireBytes else") {
                        line(
                            "throw WireError.underflow(needed: numProps * HeaderLayout.propertyMinWireBytes, remaining: buffer.remaining)"
                        )
                    }
                    line("doc.properties.reserveCapacity(numProps)")
                    block("for _ in 0..<numProps") {
                        line("let rawTag = try buffer.readShort()")
                        line("let itemLen = Int(UInt16(bitPattern: try buffer.readShort()))")
                        line("let dataType = Int32(rawTag) >> HeaderLayout.propertyTypeShift")
                        line("let key = Int32(rawTag) & HeaderLayout.propertyKeyMask")
                        swiftSwitch("dataType") {
                            swiftCase("case ${headerConstant("DATA_TYPE_INT")}") {
                                line("let val = try buffer.readInt()")
                                line(
                                    "doc.properties.append(WireHeaderProperty(key: key, value: .int(val)))"
                                )
                                line("if key == ${headerKey("DOC_WIDTH")} { doc.width = val }")
                                line("else if key == ${headerKey("DOC_HEIGHT")} { doc.height = val }")
                                line(
                                    "else if key == ${headerKey("DOC_PROFILES")} { doc.capabilities = Int64(UInt32(bitPattern: val)) }"
                                )
                                block("else if key == ${headerKey("COMPRESS")} && val != 0") {
                                    line("throw WireError.compressedDocumentUnsupported")
                                }
                            }
                            swiftCase("case ${headerConstant("DATA_TYPE_FLOAT")}") {
                                line("let val = try buffer.readFloat()")
                                line(
                                    "doc.properties.append(WireHeaderProperty(key: key, value: .float(val)))"
                                )
                            }
                            swiftCase("case ${headerConstant("DATA_TYPE_LONG")}") {
                                line("let val = try buffer.readLong()")
                                line(
                                    "doc.properties.append(WireHeaderProperty(key: key, value: .long(val)))"
                                )
                                line("if key == ${headerKey("DOC_PROFILES")} { doc.capabilities = val }")
                                block("else if key == ${headerKey("COMPRESS")} && val != 0") {
                                    line("throw WireError.compressedDocumentUnsupported")
                                }
                            }
                            swiftCase("case ${headerConstant("DATA_TYPE_STRING")}") {
                                line(
                                    "let val = try buffer.readUTF8(maxLength: maxHeaderStringLength)"
                                )
                                line(
                                    "doc.properties.append(WireHeaderProperty(key: key, value: .string(val)))"
                                )
                            }
                            swiftCase("default") {
                                line("let payload = try buffer.readBytes(count: itemLen)")
                                line(
                                    "doc.properties.append(WireHeaderProperty(key: key, value: .unknown(dataType: dataType, payload: payload)))"
                                )
                            }
                        }
                    }
                }
                block("} else if rawMajor >= 0 && rawMajor <= HeaderLayout.versionMask", close = null) {
                    line("doc.isModernHeader = false")
                    line("doc.majorVersion = rawMajor")
                    line("doc.width = try buffer.readInt()")
                    line("doc.height = try buffer.readInt()")
                    line("doc.capabilities = try buffer.readLong()")
                }
                block("} else") { line("throw WireError.invalidHeaderMagic(rawMajor)") }
                line()
                block("while buffer.available") {
                    block("guard doc.operations.count < maxOperations else") {
                        line("throw WireError.operationCountExceeded(limit: maxOperations)")
                    }
                    line("let opByte = try buffer.readByte()")
                    block("guard let opCode = OpCode(rawValue: opByte) else") {
                        line("throw WireError.unknownOpCode(opByte)")
                    }
                    swiftSwitch("opCode") {
                        for (op in schema.operations) {
                            val swiftCase = toCamelCase(op.name)
                            swiftCase("case .$swiftCase") {
                                if (op.id == 0) {
                                    line("throw WireError.unexpectedHeaderInBody")
                                } else if (op.conditionalSkip != null) {
                                    block("if evaluateSkip", close = null) {
                                        line(
                                            "doc.operations.append(.$swiftCase(try ${op.name}.readForPlayer(from: &buffer, playerApiLevel: playerApiLevel, playerProfiles: playerProfiles)))"
                                        )
                                    }
                                    block("} else") {
                                        line(
                                            "doc.operations.append(.$swiftCase(try ${op.name}.read(from: &buffer)))"
                                        )
                                    }
                                } else {
                                    line(
                                        "doc.operations.append(.$swiftCase(try ${op.name}.read(from: &buffer)))"
                                    )
                                }
                            }
                        }
                    }
                }
                line("return doc")
            }
            line()
            line("/// Serializes this document to raw binary bytes, preserving modern headers.")
            block("public func save() throws(WireError) -> [UInt8]") {
                block("guard majorVersion >= 0 && majorVersion <= HeaderLayout.versionMask else") {
                    line(
                        "throw WireError.invalidFieldValue(owner: \"WireDocument\", field: \"majorVersion\", detail: \"majorVersion \\(majorVersion) out of range (0..65535)\")"
                    )
                }
                line("var buffer = WireBuffer()")
                line("buffer.writeByte(OpCode.header.rawValue)")
                block("if isModernHeader || !properties.isEmpty", close = null) {
                    line("var outProps = properties")
                    block(
                        "if let idx = outProps.lastIndex(where: { if \$0.key != ${headerKey("DOC_WIDTH")} { return false }; if case .int(_) = \$0.value { return true }; return false })",
                        close = null,
                    ) {
                        line("outProps[idx].value = .int(width)")
                    }
                    block("} else if width != Self.defaultDimension") {
                        line("outProps.append(WireHeaderProperty(key: ${headerKey("DOC_WIDTH")}, value: .int(width)))")
                    }
                    block(
                        "if let idx = outProps.lastIndex(where: { if \$0.key != ${headerKey("DOC_HEIGHT")} { return false }; if case .int(_) = \$0.value { return true }; return false })",
                        close = null,
                    ) {
                        line("outProps[idx].value = .int(height)")
                    }
                    block("} else if height != Self.defaultDimension") {
                        line("outProps.append(WireHeaderProperty(key: ${headerKey("DOC_HEIGHT")}, value: .int(height)))")
                    }
                    line("let capValue: HeaderPropertyValue")
                    block(
                        "if capabilities >= 0 && capabilities <= Int64(UInt32.max)",
                        close = null,
                    ) {
                        line("capValue = .int(Int32(truncatingIfNeeded: capabilities))")
                    }
                    block("} else") { line("capValue = .long(capabilities)") }
                    block(
                        "if let idx = outProps.lastIndex(where: { if \$0.key != ${headerKey("DOC_PROFILES")} { return false }; switch \$0.value { case .int(_), .long(_): return true; default: return false } })",
                        close = null,
                    ) {
                        line("let matchesExisting: Bool")
                        swiftSwitch("outProps[idx].value") {
                            swiftCase("case .int(let existing)") {
                                line(
                                    "matchesExisting = Int64(UInt32(bitPattern: existing)) == capabilities"
                                )
                            }
                            swiftCase("case .long(let existing)") {
                                line("matchesExisting = existing == capabilities")
                            }
                            swiftCase("default") { line("matchesExisting = false") }
                        }
                        block("if !matchesExisting") { line("outProps[idx].value = capValue") }
                    }
                    block("} else if capabilities != 0") {
                        line("outProps.append(WireHeaderProperty(key: ${headerKey("DOC_PROFILES")}, value: capValue))")
                    }
                    block("guard outProps.count <= Self.maxHeaderProperties else") {
                        line(
                            "throw WireError.lengthOutOfRange(field: \"headerProperties\", length: outProps.count, min: 0, max: Self.maxHeaderProperties)"
                        )
                    }
                    line("buffer.writeInt(Self.magicNumber | (majorVersion & HeaderLayout.versionMask))")
                    line("buffer.writeInt(minorVersion)")
                    line("buffer.writeInt(patchVersion)")
                    line("buffer.writeInt(Int32(outProps.count))")
                    block("for prop in outProps") {
                        block("guard prop.key >= 0 && prop.key <= HeaderLayout.propertyKeyMask else") {
                            line(
                                "throw WireError.invalidFieldValue(owner: \"WireDocument\", field: \"headerPropertyKey\", detail: \"Property key \\(prop.key) out of range (0..63)\")"
                            )
                        }
                        swiftSwitch("prop.value") {
                            swiftCase("case .int(let val)") {
                                line(
                                    "buffer.writeShort(Int16(truncatingIfNeeded: (prop.key & HeaderLayout.propertyKeyMask) | (${headerConstant("DATA_TYPE_INT")} << HeaderLayout.propertyTypeShift)))"
                                )
                                line("buffer.writeShort(Int16(MemoryLayout<Int32>.size))")
                                line("buffer.writeInt(val)")
                            }
                            swiftCase("case .float(let val)") {
                                line(
                                    "buffer.writeShort(Int16(truncatingIfNeeded: (prop.key & HeaderLayout.propertyKeyMask) | (${headerConstant("DATA_TYPE_FLOAT")} << HeaderLayout.propertyTypeShift)))"
                                )
                                line("buffer.writeShort(Int16(MemoryLayout<Float>.size))")
                                line("buffer.writeFloat(val)")
                            }
                            swiftCase("case .long(let val)") {
                                line(
                                    "buffer.writeShort(Int16(truncatingIfNeeded: (prop.key & HeaderLayout.propertyKeyMask) | (${headerConstant("DATA_TYPE_LONG")} << HeaderLayout.propertyTypeShift)))"
                                )
                                line("buffer.writeShort(Int16(MemoryLayout<Int64>.size))")
                                line("buffer.writeLong(val)")
                            }
                            swiftCase("case .string(let val)") {
                                line("let utf8Count = val.utf8.count")
                                block("guard utf8Count <= Self.maxHeaderStringLength else") {
                                    line(
                                        "throw WireError.lengthOutOfRange(field: \"headerStringProperty\", length: utf8Count, min: nil, max: Self.maxHeaderStringLength)"
                                    )
                                }
                                line(
                                    "buffer.writeShort(Int16(truncatingIfNeeded: (prop.key & HeaderLayout.propertyKeyMask) | (${headerConstant("DATA_TYPE_STRING")} << HeaderLayout.propertyTypeShift)))"
                                )
                                line("buffer.writeShort(Int16(truncatingIfNeeded: utf8Count + MemoryLayout<Int32>.size))")
                                line(
                                    "try buffer.writeUTF8(val, maxLength: Self.maxHeaderStringLength)"
                                )
                            }
                            swiftCase("case .unknown(let dt, let payload)") {
                                block("guard dt >= HeaderLayout.propertyTypeMin && dt <= HeaderLayout.propertyTypeMax && (dt < ${headerConstant("DATA_TYPE_INT")} || dt > ${headerConstant("DATA_TYPE_STRING")}) else") {
                                    line(
                                        "throw WireError.invalidFieldValue(owner: \"WireDocument\", field: \"headerPropertyDataType\", detail: \"Unknown dataType \\(dt) out of range\")"
                                    )
                                }
                                block("guard payload.count <= Int(UInt16.max) else") {
                                    line(
                                        "throw WireError.lengthOutOfRange(field: \"headerUnknownProperty\", length: payload.count, min: nil, max: Int(UInt16.max))"
                                    )
                                }
                                line(
                                    "buffer.writeShort(Int16(truncatingIfNeeded: (prop.key & HeaderLayout.propertyKeyMask) | ((dt & HeaderLayout.propertyTypeMask) << HeaderLayout.propertyTypeShift)))"
                                )
                                line("buffer.writeShort(Int16(truncatingIfNeeded: payload.count))")
                                line("buffer.writeBytes(payload)")
                            }
                        }
                    }
                }
                block("} else") {
                    line("buffer.writeInt(majorVersion)")
                    line("buffer.writeInt(minorVersion)")
                    line("buffer.writeInt(patchVersion)")
                    line("buffer.writeInt(width)")
                    line("buffer.writeInt(height)")
                    line("buffer.writeLong(capabilities)")
                }
                line()
                block("for op in operations") { line("try op.write(to: &buffer)") }
                line("return buffer.data")
            }
        }
    }

    private fun emitAuxStruct(w: CodeWriter, structDef: SchemaStruct) {
        val props = structDef.astProperties
        val desc = structDef.description.replace("\n", " ").trim()
        if (desc.isNotEmpty()) {
            w.line("/// $desc")
        }
        w.block("public struct ${structDef.name}: Equatable, Hashable, Sendable") {
            multiLang.emitSwiftOpEnumConstants(this, structDef.fields)
            // LOCAL PATCH (rc-players): the struct's least wire size, bit layout and defaults, named.
            line("/// The fewest bytes one value takes on the wire.")
            line(
                "public static let minWireBytes: Int = ${CodegenExprUtils.minWireElementBytes(structDef.name, schema)}"
            )
            emitBitLayout(this, structDef.fields, null)
            emitDefaults(this, structDef.fields)
            for (prop in props) {
                val swiftType = mapToSwiftType(prop.type, prop.isNullable)
                val fieldDesc = prop.description.replace("\n", " ").trim()
                if (fieldDesc.isNotEmpty()) {
                    line("/// $fieldDesc")
                }
                line("public var ${prop.name}: $swiftType")
            }
            val structByteOrShort =
                structDef.fields
                    .filter { it.type == "byte" || it.type == "short" }
                    .map { it.name }
                    .toSet()
            val structNullable = props.filter { it.isNullable }.map { it.name }.toSet()
            for ((fieldName, bit) in structDef.computedBitProperties) {
                if (fieldName in structNullable) {
                    val srcExpr = if (fieldName in structByteOrShort) "Int32(\$0)" else "\$0"
                    block("public var ${bit.name}: Int32?") {
                        line(
                            "return $fieldName.map { ${swiftExtract(srcExpr, fieldName, bit)} }"
                        )
                    }
                } else {
                    val srcExpr =
                        if (fieldName in structByteOrShort) "Int32($fieldName)" else fieldName
                    block("public var ${bit.name}: Int32") {
                        line(
                            "return ${swiftExtract(srcExpr, fieldName, bit)}"
                        )
                    }
                }
            }
            line()

            val params =
                props.joinToString(", ") {
                    "${it.name}: ${mapToSwiftType(it.type, it.isNullable)} = ${initDefault(structDef.name, structDef.fields, it)}"
                }
            block("public init($params)") {
                for (prop in props) {
                    line("self.${prop.name} = ${prop.name}")
                }
            }
            line()

            block(
                "public static func read(from buffer: inout WireBuffer) throws(WireError) -> ${structDef.name}"
            ) {
                emitSwiftReadBody(this, structDef.name, structDef.fields, null, props)
            }
            line()

            block("public func write(to buffer: inout WireBuffer) throws(WireError)") {
                emitSwiftAtomicWriteBody(this) {
                    emitSwiftWriteBody(this, structDef.name, structDef.fields, null, props)
                }
            }
            emitStructBitExactEquality(this, structDef.name, props)
        }
    }

    private fun emitStruct(w: CodeWriter, op: SchemaOperation) {
        val props = op.astProperties
        val desc = op.description.replace("\n", " ").trim()
        if (desc.isNotEmpty()) {
            w.line("/// $desc")
        }
        val swiftCase = toCamelCase(op.name)
        w.block("public struct ${op.name}: Equatable, Hashable, Sendable") {
            line("public static let opCode: OpCode = .$swiftCase")
            line("public var opCode: OpCode { Self.opCode }")
            multiLang.emitSwiftOpEnumConstants(this, op.fields)
            // LOCAL PATCH (rc-players): named bit layouts, defaults and sparse-parameter ids.
            emitBitLayout(this, op.fields, op.conditionalSkip)
            emitDefaults(this, op.fields)
            op.sparseParameters?.takeIf { it.isNotEmpty() }?.let { params ->
                block("public enum SparseParameterID") {
                    for (sp in params) line("public static let ${sp.name}: Int = ${sp.id}")
                }
            }
            line()

            for (prop in props) {
                val swiftType = mapToSwiftType(prop.type, prop.isNullable)
                val fieldDesc = prop.description.replace("\n", " ").trim()
                if (fieldDesc.isNotEmpty()) {
                    line("/// $fieldDesc")
                }
                line("public var ${prop.name}: $swiftType")
            }
            val opByteOrShort =
                op.fields.filter { it.type == "byte" || it.type == "short" }.map { it.name }.toSet()
            val opNullable = props.filter { it.isNullable }.map { it.name }.toSet()
            for ((fieldName, bit) in op.computedBitProperties) {
                if (fieldName in opNullable) {
                    val srcExpr = if (fieldName in opByteOrShort) "Int32(\$0)" else "\$0"
                    block("public var ${bit.name}: Int32?") {
                        line(
                            "return $fieldName.map { ${swiftExtract(srcExpr, fieldName, bit)} }"
                        )
                    }
                } else {
                    val srcExpr = if (fieldName in opByteOrShort) "Int32($fieldName)" else fieldName
                    block("public var ${bit.name}: Int32") {
                        line(
                            "return ${swiftExtract(srcExpr, fieldName, bit)}"
                        )
                    }
                }
            }
            multiLang.emitSwiftOpExtras(this, op)
            line()

            val params =
                props.joinToString(", ") {
                    "${it.name}: ${mapToSwiftType(it.type, it.isNullable)} = ${initDefault(op.name, op.fields, it)}"
                }
            line("/// Creates a new [${op.name}] operation.")
            if (props.isNotEmpty()) {
                line("/// - Parameters:")
                for (prop in props) {
                    val fieldDesc = prop.description.replace("\n", " ").trim()
                    line("///   - ${prop.name}: $fieldDesc")
                }
            }
            block("public init($params)") {
                for (prop in props) {
                    line("self.${prop.name} = ${prop.name}")
                }
            }
            line()

            line("/// Reads and decodes a [${op.name}] from the wire buffer.")
            block(
                "public static func read(from buffer: inout WireBuffer) throws(WireError) -> ${op.name}"
            ) {
                if (op.conditionalSkip != null) {
                    line("let skipTaken = true")
                }
                emitSwiftReadBody(
                    this,
                    op.name,
                    op.fields,
                    op.sparseParameters,
                    props,
                    op.sparseCountType,
                    op.sparseKeyType,
                )
            }
            line()

            if (op.conditionalSkip != null) {
                val cs = op.conditionalSkip
                // LOCAL PATCH (rc-players): the condition bits and values by name.
                val condExpr = swiftExtract(cs.conditionField, cs.conditionField, skipBit(cs))
                block(
                    "public static func shouldSkip(${cs.conditionField}: Int32, ${cs.valueField}: Int32, playerApiLevel: Int32, playerProfiles: Int32) -> Bool"
                ) {
                    swiftSwitch(condExpr) {
                        for (b in cs.conditions) {
                            val brExpr =
                                CodegenExprUtils.evaluateSkipBranchExpr(
                                    b,
                                    cs.valueField,
                                    "playerApiLevel",
                                    "playerProfiles",
                                    ExprLang.SWIFT,
                                )
                            line("case ${enumRef(op.name, op.fields, b.whenValue)}: return $brExpr")
                        }
                        line("default: return ${cs.unknownConditionSkips}")
                    }
                }
                line()
                block(
                    "public static func readForPlayer(from buffer: inout WireBuffer, playerApiLevel: Int32, playerProfiles: Int32 = 0) throws(WireError) -> ${op.name}"
                ) {
                    emitSwiftReadBody(
                        this,
                        op.name,
                        op.fields,
                        op.sparseParameters,
                        props,
                        op.sparseCountType,
                        op.sparseKeyType,
                        conditionalSkip = cs,
                    )
                }
                line()
            }

            line("/// Serializes this operation to the wire buffer.")
            block("public func write(to buffer: inout WireBuffer) throws(WireError)") {
                emitSwiftAtomicWriteBody(
                    this,
                    prefixLine = "buffer.writeByte(Self.opCode.rawValue)",
                ) {
                    emitSwiftWriteBody(
                        this,
                        op.name,
                        op.fields,
                        op.sparseParameters,
                        props,
                        op.sparseCountType,
                        op.sparseKeyType,
                        op.conditionalSkip,
                    )
                }
            }
            emitStructBitExactEquality(this, op.name, props)
        }
    }

    private fun hasSwiftScalarValidations(f: SchemaField): Boolean {
        if (f.minValue != null) return true
        if (f.mustBeZeroBits != null) return true
        if (f.strictEnum && !f.enumValues.isNullOrEmpty()) return true
        val bits = f.bits
        return bits != null &&
            !f.unpackBits &&
            bits.any { it.strictEnum && !it.enumValues.isNullOrEmpty() }
    }

    private fun emitSwiftScalarValidations(
        w: CodeWriter,
        ownerName: String,
        f: SchemaField,
        varExpr: String,
    ) {
        val intExpr = if (f.type == "byte" || f.type == "short") "Int32($varExpr)" else varExpr
        if (f.minValue != null) {
            // LOCAL PATCH (rc-players): the minimum by name.
            w.block("guard $intExpr >= $ownerName.${lc("MIN_${constName(f.name)}")} else") {
                line(
                    "throw WireError.invalidFieldValue(owner: \"$ownerName\", field: \"${f.name}\", detail: \"below minimum (${f.minValue})\")"
                )
            }
        }
        if (f.mustBeZeroBits != null) {
            val badBits = swiftReservedBits(intExpr, f.name)
            w.block("if $badBits") {
                line(
                    "throw WireError.invalidFieldValue(owner: \"$ownerName\", field: \"${f.name}\", detail: \"non-zero reserved bits\")"
                )
            }
        }
        emitSwiftStrictEnumCheck(w, ownerName, f.name, varExpr, f.strictEnum, f.enumValues, f.enumValues)
        val bits = f.bits
        if (bits != null && !f.unpackBits) {
            for (b in bits) {
                if (b.strictEnum && !b.enumValues.isNullOrEmpty()) {
                    val bVar = "bit_${b.name}"
                    val extract = swiftExtract(intExpr, f.name, b)
                    w.line("let $bVar = $extract")
                    emitSwiftStrictEnumCheck(
                        w,
                        ownerName,
                        b.name,
                        bVar,
                        b.strictEnum,
                        b.enumValues,
                        b.enumValues,
                    )
                }
            }
        }
    }

    private fun emitSwiftReadBody(
        w: CodeWriter,
        ownerName: String,
        fields: List<SchemaField>,
        sparseParameters: List<SchemaSparseParam>?,
        props: List<AstProperty>,
        sparseCountType: String? = null,
        sparseKeyType: String? = null,
        conditionalSkip: SchemaConditionalSkip? = null,
    ) {
        for (f in fields) {
            if (conditionalSkip != null && f.name == conditionalSkip.payloadField) {
                w.line(
                    "let skipTaken = shouldSkip(${conditionalSkip.conditionField}: ${conditionalSkip.conditionField}, ${conditionalSkip.valueField}: ${conditionalSkip.valueField}, playerApiLevel: playerApiLevel, playerProfiles: playerProfiles)"
                )
            }
            val bits = f.bits
            if (bits != null && f.unpackBits) {
                val rawVar = "raw_${f.name}"
                w.line("let $rawVar = try buffer.readInt()")
                if (f.mustBeZeroBits != null) {
                    val badBits = swiftReservedBits(rawVar, f.name)
                    w.block("if $badBits") {
                        line(
                            "throw WireError.invalidFieldValue(owner: \"$ownerName\", field: \"${f.name}\", detail: \"non-zero reserved bits\")"
                        )
                    }
                }
                for (b in bits) {
                    w.line(
                        "let ${b.name} = ${swiftExtract(rawVar, f.name, b)}"
                    )
                    emitSwiftStrictEnumCheck(
                        w,
                        ownerName,
                        b.name,
                        b.name,
                        b.strictEnum,
                        b.enumValues,
                        b.enumValues,
                    )
                }
                continue
            }

            val isLocalDerived = f.derivedFrom != null
            val isNullable =
                !isLocalDerived &&
                    f.presentIf != null &&
                    !f.type.endsWith("[]") &&
                    f.defaultValue == null

            if (f.presentIf != null) {
                val cond = swiftCondition(f.presentIf)
                val swiftType = mapToSwiftType(f.type, isNullable)
                w.line("let ${f.name}: $swiftType")
                w.block("if $cond", close = null) {
                    if (f.type.endsWith("[]")) {
                        emitSwiftArrayRead(this, ownerName, f, "${f.name}Tmp")
                        line("${f.name} = ${f.name}Tmp")
                    } else if (hasSwiftScalarValidations(f)) {
                        emitSwiftCountedBytesGuards(this, f)
                        line("let parsed = try ${mapToSwiftFieldReadExpr(f)}")
                        emitSwiftScalarValidations(this, ownerName, f, "parsed")
                        line("${f.name} = parsed")
                    } else {
                        emitSwiftCountedBytesGuards(this, f)
                        line("${f.name} = try ${mapToSwiftFieldReadExpr(f)}")
                    }
                }
                w.block("} else") {
                    line(
                        "${f.name} = ${if (!isNullable && f in defaultFields(fields)) defaultRef(ownerName, f) else mapToSwiftDefault(f.type, isNullable, f.defaultValue)}"
                    )
                }
            } else if (f.type.endsWith("[]")) {
                emitSwiftArrayRead(w, ownerName, f, f.name)
            } else {
                emitSwiftCountedBytesGuards(w, f)
                w.line("let ${f.name} = try ${mapToSwiftFieldReadExpr(f)}")
                emitSwiftScalarValidations(w, ownerName, f, f.name)
            }
        }

        if (sparseParameters != null) {
            for (sp in sparseParameters) {
                w.line("var ${sp.name}: ${mapToSwiftType(sp.type, true)} = nil")
            }
            val effectiveCountType = sparseCountType ?: "ushort"
            val effectiveKeyType = sparseKeyType ?: "ubyte"
            when (effectiveCountType) {
                "int" -> {
                    w.line("let sparseCount = Int(try buffer.readInt())")
                    w.block("guard sparseCount >= 0 else") {
                        line(
                            "throw WireError.invalidLength(field: \"sparseCount\", length: sparseCount)"
                        )
                    }
                }
                "short" -> {
                    w.line("let sparseCount = Int(try buffer.readShort())")
                    w.block("guard sparseCount >= 0 else") {
                        line(
                            "throw WireError.invalidLength(field: \"sparseCount\", length: sparseCount)"
                        )
                    }
                }
                "byte" -> {
                    w.line("let sparseCount = Int(Int8(bitPattern: try buffer.readByte()))")
                    w.block("guard sparseCount >= 0 else") {
                        line(
                            "throw WireError.invalidLength(field: \"sparseCount\", length: sparseCount)"
                        )
                    }
                }
                "ubyte" -> w.line("let sparseCount = Int(try buffer.readByte())")
                else -> w.line("let sparseCount = Int(UInt16(bitPattern: try buffer.readShort()))")
            }
            // LOCAL PATCH (rc-players): a key and at least one byte of value, by size.
            val minEntryBytes = "(${sparseKeyBytes(effectiveKeyType)} + MemoryLayout<UInt8>.size)"
            w.block("guard sparseCount <= buffer.remaining / $minEntryBytes else") {
                line(
                    "let (needed, overflow) = sparseCount.multipliedReportingOverflow(by: $minEntryBytes)"
                )
                line(
                    "throw WireError.underflow(needed: overflow ? Int.max : needed, remaining: buffer.remaining)"
                )
            }
            w.block("for _ in 0..<sparseCount") {
                when (effectiveKeyType) {
                    "int" -> line("let paramId = Int(try buffer.readInt())")
                    "ushort" ->
                        line("let paramId = Int(UInt16(bitPattern: try buffer.readShort()))")
                    "short" -> line("let paramId = Int(try buffer.readShort())")
                    "byte" -> line("let paramId = Int(Int8(bitPattern: try buffer.readByte()))")
                    else -> line("let paramId = Int(try buffer.readByte())")
                }
                swiftSwitch("paramId") {
                    for (sp in sparseParameters) {
                        swiftCase("case ${ownerName}.SparseParameterID.${sp.name}") {
                            if (sp.type.endsWith("[]")) {
                                val synField =
                                    SchemaField(
                                        name = sp.name,
                                        type = sp.type,
                                        description = sp.description,
                                        enumValues = null,
                                        lengthType = sp.lengthType ?: "ushort",
                                    )
                                emitSwiftArrayRead(this, ownerName, synField, "${sp.name}Tmp")
                                line("${sp.name} = ${sp.name}Tmp")
                            } else {
                                line("${sp.name} = try ${mapToSwiftReadExpr(sp.type)}")
                            }
                        }
                    }
                    swiftCase("default") {
                        line(
                            "throw WireError.unknownSparseParameter(owner: \"$ownerName\", id: paramId)"
                        )
                    }
                }
            }
        }

        val args = props.joinToString(", ") { "${it.name}: ${it.name}" }
        w.line("return $ownerName($args)")
    }

    private fun emitSwiftStrictEnumCheck(
        w: CodeWriter,
        ownerName: String,
        fieldName: String,
        varExpr: String,
        strictEnum: Boolean,
        enumValues: Map<String, Int>?,
        // LOCAL PATCH (rc-players): the constants the struct emits for these values, so a check
        // names them.
        names: Map<String, Int>? = null,
    ) {
        if (strictEnum && !enumValues.isNullOrEmpty()) {
            val byValue = names.orEmpty().entries.groupBy({ it.value }, { it.key })
            val allowed =
                enumValues.values.distinct().sorted().joinToString(", ") { v ->
                    byValue[v]?.first()?.let { "$ownerName.${lc(it)}" } ?: "$v"
                }
            w.swiftSwitch(varExpr) {
                swiftCase("case $allowed") { line("break") }
                swiftCase("default") {
                    line(
                        "throw WireError.invalidFieldValue(owner: \"$ownerName\", field: \"$fieldName\", detail: \"invalid enum value \\($varExpr)\")"
                    )
                }
            }
        }
    }

    private fun emitSwiftArrayRead(
        w: CodeWriter,
        ownerName: String,
        f: SchemaField,
        targetVar: String,
    ) {
        val lenVar = "_${f.name}Count"
        if (f.countFrom != null) {
            w.line("let $lenVar = Int(${f.countFrom})")
        } else {
            when (f.effectiveLengthType) {
                "int" -> w.line("let $lenVar = Int(try buffer.readInt())")
                "ushort" -> w.line("let $lenVar = Int(UInt16(bitPattern: try buffer.readShort()))")
                "ubyte" -> w.line("let $lenVar = Int(try buffer.readByte())")
                "byte" -> w.line("let $lenVar = Int(Int8(bitPattern: try buffer.readByte()))")
                else -> w.line("let $lenVar = Int(try buffer.readShort())")
            }
        }
        val elemType = f.type.removeSuffix("[]")
        // LOCAL PATCH (rc-players): sizes and limits by name.
        val elemMinBytes = elementBytes(elemType)
        w.block("guard $lenVar >= 0 else") {
            line("throw WireError.invalidLength(field: \"${f.name}\", length: $lenVar)")
        }
        if (f.minLength != null && f.minLength > 0) {
            w.block("guard $lenVar >= ${minRef(f)} else") {
                line(
                    "throw WireError.lengthOutOfRange(field: \"${f.name}\", length: $lenVar, min: ${minRef(f)}, max: ${limitRef(f) ?: "nil"})"
                )
            }
        }
        if (f.maxLength != null) {
            w.block("guard $lenVar <= ${limitRef(f)} else") {
                line(
                    "throw WireError.lengthOutOfRange(field: \"${f.name}\", length: $lenVar, min: ${minRef(f) ?: "nil"}, max: ${limitRef(f)})"
                )
            }
        }
        w.block("guard $lenVar <= buffer.remaining / $elemMinBytes else") {
            if (elemMinBytes == "MemoryLayout<UInt8>.size") {
                line("throw WireError.underflow(needed: $lenVar, remaining: buffer.remaining)")
            } else {
                line(
                    "let (needed, overflow) = $lenVar.multipliedReportingOverflow(by: $elemMinBytes)"
                )
                line(
                    "throw WireError.underflow(needed: overflow ? Int.max : needed, remaining: buffer.remaining)"
                )
            }
        }
        val swiftElemType = mapToSwiftType(elemType, false)
        w.line("var $targetVar = [$swiftElemType]()")
        w.line("$targetVar.reserveCapacity(Swift.min($lenVar, WireLimits.maxInitialCapacity))")
        w.block("for _ in 0..<$lenVar") {
            line("$targetVar.append(try ${mapToSwiftReadExpr(elemType)})")
        }
    }


    private fun emitSwiftAtomicWriteBody(
        w: CodeWriter,
        prefixLine: String? = null,
        emitBody: CodeWriter.() -> Unit,
    ) {
        val body = buildCode {
            if (prefixLine != null) {
                line(prefixLine)
            }
            emitBody()
        }
        val bodyLines = body.trimEnd('\n').split('\n').filter { it.isNotEmpty() }
        val canThrow = body.contains("throw ") || body.contains("try ")
        if (canThrow) {
            w.line("let _startCount = buffer.data.count")
            w.line("var _committed = false")
            w.block("defer") {
                block("if !_committed") { line("buffer.truncate(to: _startCount)") }
            }
            for (l in bodyLines) {
                w.line(l)
            }
            w.line("_committed = true")
        } else {
            for (l in bodyLines) {
                w.line(l)
            }
        }
    }

    private fun emitSwiftCountedBytesGuards(w: CodeWriter, f: SchemaField) {
        if (f.type != "bytes" || f.countFrom == null) return
        val countExpr = "Int(${f.countFrom})"
        if (f.minLength != null && f.minLength > 0) {
            w.block("guard $countExpr >= ${minRef(f)} else") {
                line(
                    "throw WireError.lengthOutOfRange(field: \"${f.name}\", length: $countExpr, min: ${minRef(f)}, max: ${limitRef(f) ?: "nil"})"
                )
            }
        }
        if (f.maxLength != null) {
            w.block("guard $countExpr <= ${limitRef(f)} else") {
                line(
                    "throw WireError.lengthOutOfRange(field: \"${f.name}\", length: $countExpr, min: ${minRef(f) ?: "nil"}, max: ${limitRef(f)})"
                )
            }
        }
    }

    private fun emitSwiftWriteBody(
        w: CodeWriter,
        ownerName: String,
        fields: List<SchemaField>,
        sparseParameters: List<SchemaSparseParam>?,
        props: List<AstProperty>,
        sparseCountType: String? = null,
        sparseKeyType: String? = null,
        conditionalSkip: SchemaConditionalSkip? = null,
    ) {
        val propByName = props.associateBy { it.name }
        val localVars = mutableSetOf<String>()

        for (f in fields) {
            val bits = f.bits
            if (bits != null && f.unpackBits) {
                val terms = mutableListOf<Pair<String, SchemaBitField>>()
                for (b in bits) {
                    if (b.derivedFrom != null) {
                        val parsed = DerivedExpr.parse(b.derivedFrom)
                        if (parsed is DerivedExpr.LengthOf) {
                            // LOCAL PATCH (rc-players): the bit range's largest value, by its mask.
                            val bitMax = bitMax(f.name, b)
                            val target = parsed.targetName
                            w.block("guard $target.count <= $bitMax else") {
                                line(
                                    "throw WireError.lengthOutOfRange(field: \"$target\", length: $target.count, min: nil, max: $bitMax)"
                                )
                            }
                        }
                        val expr = derivedExprToSwift(parsed)
                        w.line("let ${b.name}: Int32 = $expr")
                        localVars.add(b.name)
                        terms.add(b.name to b)
                    } else {
                        terms.add(b.name to b)
                    }
                }
                val packed = swiftPack(f.name, terms)
                w.line("buffer.writeInt($packed)")
                continue
            }

            if (f.derivedFrom != null) {
                val parsed = DerivedExpr.parse(f.derivedFrom)
                val expr = derivedExprToSwift(parsed)
                val maxLimit =
                    if (parsed is DerivedExpr.LengthOf) lengthTypeMax(f.type) else null
                if (f.presentIf != null) {
                    val cond = swiftCondition(f.presentIf)
                    w.block("if $cond") {
                        if (maxLimit != null) {
                            val target = parsed.targetName
                            block("guard $target.count <= $maxLimit else") {
                                line(
                                    "throw WireError.lengthOutOfRange(field: \"$target\", length: $target.count, min: nil, max: $maxLimit)"
                                )
                            }
                        }
                        line(mapToSwiftDerivedWrite(f.type, expr))
                    }
                } else {
                    if (maxLimit != null) {
                        val target = parsed.targetName
                        w.block("guard $target.count <= $maxLimit else") {
                            line(
                                "throw WireError.lengthOutOfRange(field: \"$target\", length: $target.count, min: nil, max: $maxLimit)"
                            )
                        }
                    }
                    w.line("let ${f.name}: Int32 = $expr")
                    localVars.add(f.name)
                    w.line(mapToSwiftDerivedWrite(f.type, f.name))
                }
                continue
            }

            val isNullable = propByName[f.name]?.isNullable == true
            if (conditionalSkip != null && f.name == conditionalSkip.lengthField) {
                w.block("if let _payload = ${conditionalSkip.payloadField}", close = null) {
                    block("guard _payload.count <= Int(Int32.max) else") {
                        line(
                            "throw WireError.lengthOutOfRange(field: \"${conditionalSkip.payloadField}\", length: _payload.count, min: nil, max: Int(Int32.max))"
                        )
                    }
                    line("buffer.writeInt(Int32(_payload.count))")
                }
                w.block("} else") { line("buffer.writeInt(${f.name})") }
                continue
            }
            if (conditionalSkip != null && f.name == conditionalSkip.payloadField) {
                w.block("if let val = ${f.name}") {
                    if (f.minLength != null && f.minLength > 0) {
                        block("guard val.count >= ${minRef(f)} else") {
                            line(
                                "throw WireError.lengthOutOfRange(field: \"${f.name}\", length: val.count, min: ${minRef(f)}, max: ${limitRef(f) ?: "nil"})"
                            )
                        }
                    }
                    if (f.maxLength != null) {
                        block("guard val.count <= ${limitRef(f)} else") {
                            line(
                                "throw WireError.lengthOutOfRange(field: \"${f.name}\", length: val.count, min: ${minRef(f) ?: "nil"}, max: ${limitRef(f)})"
                            )
                        }
                    }
                    line("buffer.writeBytes(val)")
                }
                continue
            }
            if (f.presentIf != null) {
                val cond = swiftCondition(f.presentIf)
                w.block("if $cond") {
                    if (isNullable) {
                        block("guard let val = ${f.name} else") {
                            line(
                                "throw WireError.missingRequiredField(owner: \"$ownerName\", field: \"${f.name}\")"
                            )
                        }
                        emitSwiftFieldWrite(this, f, "val")
                    } else {
                        emitSwiftFieldWrite(this, f, f.name)
                    }
                }
            } else {
                emitSwiftFieldWrite(w, f, f.name)
            }
        }

        if (sparseParameters != null) {
            w.line("var sparseCount: Int32 = 0")
            for (sp in sparseParameters) {
                w.line("if ${sp.name} != nil { sparseCount += 1 }")
            }
            when (sparseCountType ?: "ushort") {
                "int" -> w.line("buffer.writeInt(sparseCount)")
                "ubyte",
                "byte" -> w.line("buffer.writeByte(UInt8(truncatingIfNeeded: sparseCount))")
                else -> w.line("buffer.writeShort(Int16(truncatingIfNeeded: sparseCount))")
            }
            val effectiveKeyType = sparseKeyType ?: "ubyte"
            for (sp in sparseParameters) {
                w.block("if let val = ${sp.name}") {
                    when (effectiveKeyType) {
                        "int" -> line("buffer.writeInt(Int32(SparseParameterID.${sp.name}))")
                        "ushort",
                        "short" -> line("buffer.writeShort(Int16(truncatingIfNeeded: SparseParameterID.${sp.name}))")
                        else -> line("buffer.writeByte(UInt8(truncatingIfNeeded: SparseParameterID.${sp.name}))")
                    }
                    val synField =
                        SchemaField(
                            name = sp.name,
                            type = sp.type,
                            description = sp.description,
                            enumValues = null,
                            lengthType = sp.lengthType ?: "ushort",
                        )
                    emitSwiftFieldWrite(this, synField, "val")
                }
            }
        }
    }

    private fun emitSwiftFieldWrite(w: CodeWriter, f: SchemaField, varExpr: String) {
        if (f.type.endsWith("[]")) {
            // LOCAL PATCH (rc-players): the limit and the length prefix's range, by name.
            val effectiveMax = effectiveMax(f)
            if (f.minLength != null && f.minLength > 0) {
                w.block("guard $varExpr.count >= ${minRef(f)} else") {
                    line(
                        "throw WireError.lengthOutOfRange(field: \"${f.name}\", length: $varExpr.count, min: ${minRef(f)}, max: ${effectiveMax?.toString() ?: "nil"})"
                    )
                }
            }
            if (effectiveMax != null) {
                w.block("guard $varExpr.count <= $effectiveMax else") {
                    line(
                        "throw WireError.lengthOutOfRange(field: \"${f.name}\", length: $varExpr.count, min: ${minRef(f) ?: "nil"}, max: $effectiveMax)"
                    )
                }
            }
            if (f.countFrom == null) {
                when (f.effectiveLengthType) {
                    "int" -> w.line("buffer.writeInt(Int32($varExpr.count))")
                    "ubyte",
                    "byte" -> w.line("buffer.writeByte(UInt8(truncatingIfNeeded: $varExpr.count))")
                    else -> w.line("buffer.writeShort(Int16(truncatingIfNeeded: $varExpr.count))")
                }
            }
            val elemType = f.type.removeSuffix("[]")
            w.block("for item in $varExpr") { line(mapToSwiftWriteExpr(elemType, "item")) }
        } else if (f.type == "bytes" && f.countFrom != null) {
            if (f.minLength != null && f.minLength > 0) {
                w.block("guard $varExpr.count >= ${minRef(f)} else") {
                    line(
                        "throw WireError.lengthOutOfRange(field: \"${f.name}\", length: $varExpr.count, min: ${minRef(f)}, max: ${limitRef(f) ?: "nil"})"
                    )
                }
            }
            if (f.maxLength != null) {
                w.block("guard $varExpr.count <= ${limitRef(f)} else") {
                    line(
                        "throw WireError.lengthOutOfRange(field: \"${f.name}\", length: $varExpr.count, min: ${minRef(f) ?: "nil"}, max: ${limitRef(f)})"
                    )
                }
            }
            w.line("buffer.writeBytes($varExpr)")
        } else {
            w.line(mapToSwiftWriteExpr(f.type, varExpr, limitRef(f)))
        }
    }

    private fun mapToSwiftFieldReadExpr(f: SchemaField): String {
        if (f.type == "bytes" && f.countFrom != null) {
            return "buffer.readBytes(count: Int(${f.countFrom}))"
        }
        return mapToSwiftReadExpr(f.type, limitRef(f))
    }

    private inline fun CodeWriter.swiftSwitch(expr: String, body: CodeWriter.() -> Unit) {
        line("switch $expr {")
        body()
        line("}")
    }

    private inline fun CodeWriter.swiftCase(label: String, body: CodeWriter.() -> Unit) {
        line("$label:")
        indent(body)
    }

    private fun derivedExprToSwift(expr: DerivedExpr): String {
        val target = expr.targetName
        return when (expr) {
            is DerivedExpr.LengthOf -> "Int32($target.count)"
            is DerivedExpr.PresentOf -> "($target != nil ? 1 : 0)"
            is DerivedExpr.NonEmptyOf -> "(!$target.isEmpty ? 1 : 0)"
        }
    }

    private fun mapToSwiftDerivedWrite(type: String, int32Expr: String): String =
        when (type) {
            "short" -> "buffer.writeShort(Int16(truncatingIfNeeded: $int32Expr))"
            "byte" -> "buffer.writeByte(UInt8(truncatingIfNeeded: $int32Expr))"
            else -> "buffer.writeInt($int32Expr)"
        }

    private fun mapToSwiftType(type: String, isNullable: Boolean): String {
        val base =
            if (type.endsWith("[]")) {
                "[${mapToSwiftType(type.removeSuffix("[]"), false)}]"
            } else {
                when (type) {
                    "int",
                    "int_id",
                    "color" -> "Int32"
                    "short" -> "Int16"
                    "byte" -> "UInt8"
                    "long" -> "Int64"
                    "float",
                    "float_or_id" -> "Float"
                    "boolean" -> "Bool"
                    "string" -> "String"
                    "bytes" -> "[UInt8]"
                    else -> if (schema.structs.containsKey(type)) type else "Int32"
                }
            }
        return if (isNullable) "$base?" else base
    }

    private fun mapToSwiftDefault(
        type: String,
        isNullable: Boolean,
        explicitDefault: String?,
    ): String {
        if (isNullable) return "nil"
        if (explicitDefault != null) {
            return when (type) {
                "float",
                "float_or_id" ->
                    if (explicitDefault.contains('.')) explicitDefault else "$explicitDefault.0"
                else -> explicitDefault
            }
        }
        if (type.endsWith("[]")) return "[]"
        return when (type) {
            "int",
            "int_id",
            "color",
            "short",
            "byte" -> "0"
            "long" -> "0"
            "float",
            "float_or_id" -> "0.0"
            "boolean" -> "false"
            "string" -> "\"\""
            "bytes" -> "[]"
            else -> if (schema.structs.containsKey(type)) "$type()" else "0"
        }
    }

    private fun mapToSwiftReadExpr(type: String, maxLength: String? = null): String =
        when (type) {
            "int",
            "int_id",
            "color" -> "buffer.readInt()"
            "short" -> "buffer.readShort()"
            "byte" -> "buffer.readByte()"
            "long" -> "buffer.readLong()"
            "float",
            "float_or_id" -> "buffer.readFloat()"
            "boolean" -> "buffer.readBoolean()"
            "string" ->
                if (maxLength != null) "buffer.readUTF8(maxLength: $maxLength)"
                else "buffer.readUTF8()"
            "bytes" ->
                if (maxLength != null) "buffer.readBuffer(maxLength: $maxLength)"
                else "buffer.readBuffer()"
            else ->
                if (schema.structs.containsKey(type)) "$type.read(from: &buffer)"
                else "buffer.readInt()"
        }

    private fun mapToSwiftWriteExpr(
        type: String,
        varName: String,
        maxLength: String? = null,
    ): String =
        when (type) {
            "int",
            "int_id",
            "color" -> "buffer.writeInt($varName)"
            "short" -> "buffer.writeShort($varName)"
            "byte" -> "buffer.writeByte($varName)"
            "long" -> "buffer.writeLong($varName)"
            "float",
            "float_or_id" -> "buffer.writeFloat($varName)"
            "boolean" -> "buffer.writeBoolean($varName)"
            "string" ->
                if (maxLength != null) "try buffer.writeUTF8($varName, maxLength: $maxLength)"
                else "try buffer.writeUTF8($varName)"
            "bytes" ->
                if (maxLength != null) "try buffer.writeBuffer($varName, maxLength: $maxLength)"
                else "try buffer.writeBuffer($varName)"
            else ->
                if (schema.structs.containsKey(type)) "try $varName.write(to: &buffer)"
                else "buffer.writeInt($varName)"
        }

    companion object {
        // LOCAL PATCH (rc-players): the names the patches above emit under.
        const val WIRE_EXPRESSION_CONSTANTS: String = "WireExpressionConstants"
        /** Upstream's cap on the room an array read reserves up front. */
        const val MAX_INITIAL_CAPACITY: Int = 4096
        /** Upstream's header values, for a schema that does not define them (a subset). */
        private const val UPSTREAM_PROPERTY_TYPE_BITS: Int = 6
        private const val UPSTREAM_PROPERTY_TYPE_SHIFT: Int = 10
        private const val UPSTREAM_PROPERTY_MIN_BYTES: Int = 8
        private val UPSTREAM_HEADER_KEYS =
            linkedMapOf("DOC_WIDTH" to 5, "DOC_HEIGHT" to 6, "DOC_PROFILES" to 14, "COMPRESS" to 31)
        private val UPSTREAM_HEADER_CONSTANTS =
            mapOf(
                "DEFAULT_DIMENSION" to 256,
                "MAGIC_NUMBER" to 0x048C0000,
                "DEFAULT_MAJOR_VERSION" to 1,
                "DEFAULT_MINOR_VERSION" to 1,
                "DEFAULT_PATCH_VERSION" to 0,
                "DATA_TYPE_INT" to 0,
                "DATA_TYPE_FLOAT" to 1,
                "DATA_TYPE_LONG" to 2,
                "DATA_TYPE_STRING" to 3,
            )

        fun toCamelCase(name: String): String = name.replaceFirstChar { it.lowercaseChar() }
    }
}
