package terminal

/**
 * A lightweight, dependency-free JSON writer that builds JSON text via a [StringBuilder].
 *
 * Usage example:
 * ```
 * val json = JsonWriter.build {
 *     writeObject {
 *         writeField("name", "Alice")
 *         writeField("age", 30)
 *         writeFieldArray("scores") {
 *             writeValue(100)
 *             writeValue(95)
 *         }
 *     }
 * }
 * ```
 */
class JsonWriter(private val sb: StringBuilder = StringBuilder()) {

    companion object {
        /**
         * Builds a JSON string using the given [block] to populate the writer.
         */
        fun build(block: JsonWriter.() -> Unit): String {
            val writer = JsonWriter()
            writer.block()
            return writer.toString()
        }
    }

    override fun toString(): String = sb.toString()

    // --- Object writing ---

    /**
     * Writes a JSON object `{ ... }`. The [block] is called to write fields inside it.
     */
    fun writeObject(block: JsonWriter.() -> Unit) {
        sb.append('{')
        val inner = JsonWriter(sb)
        inner.block()
        // Remove trailing comma if present
        if (sb.isNotEmpty() && sb[sb.length - 1] == ',') {
            sb.deleteCharAt(sb.length - 1)
        }
        sb.append('}')
    }

    /**
     * Writes a JSON array `[ ... ]`. The [block] is called to write values inside it.
     */
    fun writeArray(block: JsonWriter.() -> Unit) {
        sb.append('[')
        val inner = JsonWriter(sb)
        inner.block()
        // Remove trailing comma if present
        if (sb.isNotEmpty() && sb[sb.length - 1] == ',') {
            sb.deleteCharAt(sb.length - 1)
        }
        sb.append(']')
    }

    // --- Field writers (key: value inside an object) ---

    /**
     * Writes a string field: `"key":"value"`.
     */
    fun writeField(key: String, value: String) {
        writeString(key)
        sb.append(':')
        writeString(value)
        sb.append(',')
    }

    /**
     * Writes an integer field: `"key":value`.
     */
    fun writeField(key: String, value: Int) {
        writeString(key)
        sb.append(':')
        sb.append(value)
        sb.append(',')
    }

    /**
     * Writes a boolean field: `"key":true` or `"key":false`.
     */
    fun writeField(key: String, value: Boolean) {
        writeString(key)
        sb.append(':')
        sb.append(value)
        sb.append(',')
    }

    /**
     * Writes an object field: `"key":{ ... }`.
     */
    fun writeFieldObject(key: String, block: JsonWriter.() -> Unit) {
        writeString(key)
        sb.append(':')
        writeObject(block)
        sb.append(',')
    }

    /**
     * Writes an array field: `"key":[ ... ]`.
     */
    fun writeFieldArray(key: String, block: JsonWriter.() -> Unit) {
        writeString(key)
        sb.append(':')
        writeArray(block)
        sb.append(',')
    }

    // --- Value writers (bare values inside an array) ---

    /**
     * Writes a string value followed by a comma (for use in arrays).
     */
    fun writeValue(value: String) {
        writeString(value)
        sb.append(',')
    }

    /**
     * Writes an integer value followed by a comma (for use in arrays).
     */
    fun writeValue(value: Int) {
        sb.append(value)
        sb.append(',')
    }

    /**
     * Writes a boolean value followed by a comma (for use in arrays).
     */
    fun writeValue(value: Boolean) {
        sb.append(value)
        sb.append(',')
    }

    /**
     * Writes an object value followed by a comma (for use in arrays).
     */
    fun writeValueObject(block: JsonWriter.() -> Unit) {
        writeObject(block)
        sb.append(',')
    }

    // --- String escaping ---

    /**
     * Writes a JSON-escaped string surrounded by double quotes.
     *
     * Escapes: `"`, `\`, `/`, backspace, form feed, newline, carriage return, tab,
     * and all control characters (U+0000..U+001F) as `\uXXXX`.
     */
    private fun writeString(value: String) {
        sb.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code in 0..0x1F) {
                        sb.append("\\u")
                        sb.append(ch.code.toString(16).padStart(4, '0'))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
    }
}
