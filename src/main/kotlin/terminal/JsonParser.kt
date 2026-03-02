package terminal

/**
 * Sealed class hierarchy representing JSON values.
 */
sealed class JsonValue {
    /** Returns this value as a [JsonObject], or throws if it is not one. */
    fun asObject(): JsonObject = this as JsonObject
    /** Returns this value as a [JsonArray], or throws if it is not one. */
    fun asArray(): JsonArray = this as JsonArray
    /** Returns this value as a [JsonString], or throws if it is not one. */
    fun asString(): JsonString = this as JsonString
    /** Returns this value as a [JsonNumber], or throws if it is not one. */
    fun asNumber(): JsonNumber = this as JsonNumber
    /** Returns this value as a [JsonBool], or throws if it is not one. */
    fun asBool(): JsonBool = this as JsonBool
}

data class JsonObject(val fields: Map<String, JsonValue>) : JsonValue() {
    operator fun get(key: String): JsonValue =
        fields[key] ?: throw NoSuchElementException("Missing JSON field: \"$key\"")

    fun getOrNull(key: String): JsonValue? = fields[key]

    /** Convenience: get a string field value. */
    fun getString(key: String): String = get(key).asString().value

    /** Convenience: get an int field value. */
    fun getInt(key: String): Int = get(key).asNumber().value

    /** Convenience: get a boolean field value. */
    fun getBool(key: String): Boolean = get(key).asBool().value

    /** Convenience: get an object field value. */
    fun getObject(key: String): JsonObject = get(key).asObject()

    /** Convenience: get an array field value. */
    fun getArray(key: String): JsonArray = get(key).asArray()
}

data class JsonArray(val elements: List<JsonValue>) : JsonValue() {
    val size: Int get() = elements.size
    operator fun get(index: Int): JsonValue = elements[index]
}

data class JsonString(val value: String) : JsonValue()

data class JsonNumber(val value: Int) : JsonValue()

data class JsonBool(val value: Boolean) : JsonValue()

data object JsonNull : JsonValue()

/**
 * A minimal recursive-descent JSON parser.
 *
 * Supports the subset of JSON needed for terminal buffer serialization:
 * objects, arrays, strings, integers, booleans, and null.
 * Floating-point numbers are not supported (not needed).
 *
 * Usage:
 * ```
 * val root = JsonParser.parse(jsonString)
 * val obj = root.asObject()
 * val width = obj.getInt("width")
 * ```
 */
class JsonParser private constructor(private val input: String) {

    companion object {
        /**
         * Parses a JSON string into a [JsonValue] tree.
         *
         * @throws IllegalArgumentException on malformed input.
         */
        fun parse(input: String): JsonValue {
            val parser = JsonParser(input)
            val result = parser.parseValue()
            parser.skipWhitespace()
            if (parser.pos < parser.input.length) {
                parser.error("Unexpected trailing content")
            }
            return result
        }
    }

    private var pos: Int = 0

    private fun parseValue(): JsonValue {
        skipWhitespace()
        if (pos >= input.length) error("Unexpected end of input")

        return when (input[pos]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> JsonString(parseString())
            't', 'f' -> parseBool()
            'n' -> parseNull()
            '-', in '0'..'9' -> parseNumber()
            else -> error("Unexpected character '${input[pos]}'")
        }
    }

    private fun parseObject(): JsonObject {
        expect('{')
        skipWhitespace()

        val fields = mutableMapOf<String, JsonValue>()
        if (pos < input.length && input[pos] == '}') {
            pos++
            return JsonObject(fields)
        }

        while (true) {
            skipWhitespace()
            val key = parseString()
            skipWhitespace()
            expect(':')
            val value = parseValue()
            fields[key] = value
            skipWhitespace()

            if (pos >= input.length) error("Unexpected end of input in object")
            when (input[pos]) {
                ',' -> pos++
                '}' -> { pos++; return JsonObject(fields) }
                else -> error("Expected ',' or '}' in object, got '${input[pos]}'")
            }
        }
    }

    private fun parseArray(): JsonArray {
        expect('[')
        skipWhitespace()

        val elements = mutableListOf<JsonValue>()
        if (pos < input.length && input[pos] == ']') {
            pos++
            return JsonArray(elements)
        }

        while (true) {
            elements.add(parseValue())
            skipWhitespace()

            if (pos >= input.length) error("Unexpected end of input in array")
            when (input[pos]) {
                ',' -> pos++
                ']' -> { pos++; return JsonArray(elements) }
                else -> error("Expected ',' or ']' in array, got '${input[pos]}'")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val sb = StringBuilder()
        while (pos < input.length) {
            val ch = input[pos]
            if (ch == '"') {
                pos++
                return sb.toString()
            }
            if (ch == '\\') {
                pos++
                if (pos >= input.length) error("Unexpected end of input in string escape")
                when (input[pos]) {
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/' -> sb.append('/')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000C')
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    'u' -> {
                        pos++
                        if (pos + 4 > input.length) error("Unexpected end of input in unicode escape")
                        val hex = input.substring(pos, pos + 4)
                        val codePoint = hex.toIntOrNull(16)
                            ?: error("Invalid unicode escape: \\u$hex")
                        sb.append(codePoint.toChar())
                        pos += 3 // +1 happens below
                    }
                    else -> error("Invalid escape sequence: \\${input[pos]}")
                }
                pos++
            } else {
                sb.append(ch)
                pos++
            }
        }
        error("Unterminated string")
    }

    private fun parseNumber(): JsonNumber {
        val start = pos
        if (input[pos] == '-') pos++
        if (pos >= input.length || input[pos] !in '0'..'9') error("Invalid number")

        while (pos < input.length && input[pos] in '0'..'9') {
            pos++
        }

        val numStr = input.substring(start, pos)
        val value = numStr.toIntOrNull() ?: error("Number out of range: $numStr")
        return JsonNumber(value)
    }

    private fun parseBool(): JsonBool {
        return if (input.startsWith("true", pos)) {
            pos += 4
            JsonBool(true)
        } else if (input.startsWith("false", pos)) {
            pos += 5
            JsonBool(false)
        } else {
            error("Expected 'true' or 'false'")
        }
    }

    private fun parseNull(): JsonNull {
        if (input.startsWith("null", pos)) {
            pos += 4
            return JsonNull
        }
        error("Expected 'null'")
    }

    private fun expect(ch: Char) {
        skipWhitespace()
        if (pos >= input.length || input[pos] != ch) {
            val actual = if (pos < input.length) "'${input[pos]}'" else "end of input"
            error("Expected '$ch', got $actual")
        }
        pos++
    }

    private fun skipWhitespace() {
        while (pos < input.length && input[pos] in " \t\n\r") {
            pos++
        }
    }

    private fun error(message: String): Nothing {
        throw IllegalArgumentException("JSON parse error at position $pos: $message")
    }
}