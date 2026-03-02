package terminal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JsonParserTest {

    // --- Primitive values ---

    @Test
    fun `parse string`() {
        val result = JsonParser.parse(""""hello"""")
        assertEquals(JsonString("hello"), result)
    }

    @Test
    fun `parse empty string`() {
        val result = JsonParser.parse("""""""")
        assertEquals(JsonString(""), result)
    }

    @Test
    fun `parse integer`() {
        val result = JsonParser.parse("42")
        assertEquals(JsonNumber(42), result)
    }

    @Test
    fun `parse negative integer`() {
        val result = JsonParser.parse("-7")
        assertEquals(JsonNumber(-7), result)
    }

    @Test
    fun `parse zero`() {
        val result = JsonParser.parse("0")
        assertEquals(JsonNumber(0), result)
    }

    @Test
    fun `parse true`() {
        val result = JsonParser.parse("true")
        assertEquals(JsonBool(true), result)
    }

    @Test
    fun `parse false`() {
        val result = JsonParser.parse("false")
        assertEquals(JsonBool(false), result)
    }

    @Test
    fun `parse null`() {
        val result = JsonParser.parse("null")
        assertEquals(JsonNull, result)
    }

    // --- String escaping ---

    @Test
    fun `parse string with escaped quotes`() {
        val result = JsonParser.parse(""""he said \"hi\""""")
        assertEquals(JsonString("he said \"hi\""), result)
    }

    @Test
    fun `parse string with escaped backslash`() {
        val result = JsonParser.parse(""""C:\\temp"""")
        assertEquals(JsonString("C:\\temp"), result)
    }

    @Test
    fun `parse string with escaped newline and tab`() {
        val result = JsonParser.parse(""""line1\nline2\tend"""")
        assertEquals(JsonString("line1\nline2\tend"), result)
    }

    @Test
    fun `parse string with escaped carriage return and backspace`() {
        val result = JsonParser.parse(""""a\r\bb"""")
        assertEquals(JsonString("a\r\bb"), result)
    }

    @Test
    fun `parse string with escaped form feed`() {
        val result = JsonParser.parse(""""a\fb"""")
        assertEquals(JsonString("a\u000Cb"), result)
    }

    @Test
    fun `parse string with escaped slash`() {
        val result = JsonParser.parse(""""a\/b"""")
        assertEquals(JsonString("a/b"), result)
    }

    @Test
    fun `parse string with unicode escape`() {
        val result = JsonParser.parse(""""\\u0041"""")
        // \u0041 is 'A' — but here the backslash is escaped, so it's literal \u0041
        assertEquals(JsonString("\\u0041"), result)
    }

    @Test
    fun `parse string with actual unicode escape`() {
        val result = JsonParser.parse(""""\u0041"""")
        assertEquals(JsonString("A"), result)
    }

    @Test
    fun `parse string with null character unicode escape`() {
        val result = JsonParser.parse(""""\u0000"""")
        assertEquals(JsonString("\u0000"), result)
    }

    // --- Objects ---

    @Test
    fun `parse empty object`() {
        val result = JsonParser.parse("{}")
        val obj = result.asObject()
        assertTrue(obj.fields.isEmpty())
    }

    @Test
    fun `parse object with string field`() {
        val result = JsonParser.parse("""{"name":"Alice"}""")
        val obj = result.asObject()
        assertEquals("Alice", obj.getString("name"))
    }

    @Test
    fun `parse object with int field`() {
        val result = JsonParser.parse("""{"age":30}""")
        val obj = result.asObject()
        assertEquals(30, obj.getInt("age"))
    }

    @Test
    fun `parse object with boolean field`() {
        val result = JsonParser.parse("""{"active":true}""")
        val obj = result.asObject()
        assertEquals(true, obj.getBool("active"))
    }

    @Test
    fun `parse object with multiple fields`() {
        val result = JsonParser.parse("""{"name":"Bob","age":25,"active":true}""")
        val obj = result.asObject()
        assertEquals("Bob", obj.getString("name"))
        assertEquals(25, obj.getInt("age"))
        assertEquals(true, obj.getBool("active"))
    }

    @Test
    fun `parse nested object`() {
        val result = JsonParser.parse("""{"inner":{"x":1}}""")
        val obj = result.asObject()
        val inner = obj.getObject("inner")
        assertEquals(1, inner.getInt("x"))
    }

    // --- Arrays ---

    @Test
    fun `parse empty array`() {
        val result = JsonParser.parse("[]")
        val arr = result.asArray()
        assertEquals(0, arr.size)
    }

    @Test
    fun `parse array of integers`() {
        val result = JsonParser.parse("[1,2,3]")
        val arr = result.asArray()
        assertEquals(3, arr.size)
        assertEquals(JsonNumber(1), arr[0])
        assertEquals(JsonNumber(2), arr[1])
        assertEquals(JsonNumber(3), arr[2])
    }

    @Test
    fun `parse array of strings`() {
        val result = JsonParser.parse("""["a","b","c"]""")
        val arr = result.asArray()
        assertEquals(3, arr.size)
        assertEquals("a", arr[0].asString().value)
        assertEquals("b", arr[1].asString().value)
        assertEquals("c", arr[2].asString().value)
    }

    @Test
    fun `parse array of objects`() {
        val result = JsonParser.parse("""[{"id":1},{"id":2}]""")
        val arr = result.asArray()
        assertEquals(2, arr.size)
        assertEquals(1, arr[0].asObject().getInt("id"))
        assertEquals(2, arr[1].asObject().getInt("id"))
    }

    @Test
    fun `parse array with mixed types`() {
        val result = JsonParser.parse("""[1,"two",true,null]""")
        val arr = result.asArray()
        assertEquals(4, arr.size)
        assertEquals(JsonNumber(1), arr[0])
        assertEquals(JsonString("two"), arr[1])
        assertEquals(JsonBool(true), arr[2])
        assertEquals(JsonNull, arr[3])
    }

    // --- Whitespace handling ---

    @Test
    fun `parse with whitespace`() {
        val json = """
        {
            "name" : "Alice" ,
            "items" : [ 1 , 2 , 3 ]
        }
        """
        val result = JsonParser.parse(json)
        val obj = result.asObject()
        assertEquals("Alice", obj.getString("name"))
        assertEquals(3, obj.getArray("items").size)
    }

    @Test
    fun `parse with tabs and newlines`() {
        val json = "{\t\n\"key\"\t:\t\"val\"\n}"
        val result = JsonParser.parse(json)
        assertEquals("val", result.asObject().getString("key"))
    }

    // --- Convenience methods on JsonObject ---

    @Test
    fun `getOrNull returns null for missing key`() {
        val obj = JsonParser.parse("""{"a":1}""").asObject()
        assertEquals(null, obj.getOrNull("b"))
    }

    @Test
    fun `getOrNull returns value for existing key`() {
        val obj = JsonParser.parse("""{"a":1}""").asObject()
        assertEquals(JsonNumber(1), obj.getOrNull("a"))
    }

    @Test
    fun `get throws for missing key`() {
        val obj = JsonParser.parse("""{"a":1}""").asObject()
        assertFailsWith<NoSuchElementException> { obj["b"] }
    }

    // --- Error cases ---

    @Test
    fun `error on empty input`() {
        assertFailsWith<IllegalArgumentException> {
            JsonParser.parse("")
        }
    }

    @Test
    fun `error on trailing content`() {
        assertFailsWith<IllegalArgumentException> {
            JsonParser.parse("42 extra")
        }
    }

    @Test
    fun `error on unterminated string`() {
        assertFailsWith<IllegalArgumentException> {
            JsonParser.parse(""""unterminated""")
        }
    }

    @Test
    fun `error on unterminated object`() {
        assertFailsWith<IllegalArgumentException> {
            JsonParser.parse("""{"key":1""")
        }
    }

    @Test
    fun `error on unterminated array`() {
        assertFailsWith<IllegalArgumentException> {
            JsonParser.parse("[1,2")
        }
    }

    @Test
    fun `error on invalid escape sequence`() {
        assertFailsWith<IllegalArgumentException> {
            JsonParser.parse(""""bad\x"""")
        }
    }

    @Test
    fun `error on invalid unicode escape`() {
        assertFailsWith<IllegalArgumentException> {
            JsonParser.parse(""""\uGGGG"""")
        }
    }

    @Test
    fun `error on unexpected character`() {
        assertFailsWith<IllegalArgumentException> {
            JsonParser.parse("@")
        }
    }

    // --- Round-trip with JsonWriter ---

    @Test
    fun `round-trip simple object`() {
        val json = JsonWriter.build {
            writeObject {
                writeField("name", "Alice")
                writeField("age", 30)
                writeField("active", true)
            }
        }
        val parsed = JsonParser.parse(json).asObject()
        assertEquals("Alice", parsed.getString("name"))
        assertEquals(30, parsed.getInt("age"))
        assertEquals(true, parsed.getBool("active"))
    }

    @Test
    fun `round-trip nested structure`() {
        val json = JsonWriter.build {
            writeObject {
                writeFieldObject("attrs") {
                    writeField("fg", "RED")
                    writeField("bold", true)
                }
                writeFieldArray("items") {
                    writeValueObject { writeField("id", 1) }
                    writeValueObject { writeField("id", 2) }
                }
            }
        }
        val parsed = JsonParser.parse(json).asObject()
        assertEquals("RED", parsed.getObject("attrs").getString("fg"))
        assertEquals(true, parsed.getObject("attrs").getBool("bold"))
        assertEquals(2, parsed.getArray("items").size)
        assertEquals(1, parsed.getArray("items")[0].asObject().getInt("id"))
    }

    @Test
    fun `round-trip string with special characters`() {
        val original = "line1\nline2\ttab\"quote\\backslash"
        val json = JsonWriter.build {
            writeObject { writeField("text", original) }
        }
        val parsed = JsonParser.parse(json).asObject()
        assertEquals(original, parsed.getString("text"))
    }

    @Test
    fun `round-trip string with control characters`() {
        val original = "a\u0001\u0002\u001Fb"
        val json = JsonWriter.build {
            writeObject { writeField("text", original) }
        }
        val parsed = JsonParser.parse(json).asObject()
        assertEquals(original, parsed.getString("text"))
    }
}