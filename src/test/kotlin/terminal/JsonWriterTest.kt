package terminal

import kotlin.test.Test
import kotlin.test.assertEquals

class JsonWriterTest {

    @Test
    fun `empty object`() {
        val json = JsonWriter.build { writeObject {} }
        assertEquals("{}", json)
    }

    @Test
    fun `empty array`() {
        val json = JsonWriter.build { writeArray {} }
        assertEquals("[]", json)
    }

    @Test
    fun `object with string field`() {
        val json = JsonWriter.build {
            writeObject {
                writeField("name", "Alice")
            }
        }
        assertEquals("""{"name":"Alice"}""", json)
    }

    @Test
    fun `object with int field`() {
        val json = JsonWriter.build {
            writeObject {
                writeField("age", 30)
            }
        }
        assertEquals("""{"age":30}""", json)
    }

    @Test
    fun `object with boolean fields`() {
        val json = JsonWriter.build {
            writeObject {
                writeField("active", true)
                writeField("deleted", false)
            }
        }
        assertEquals("""{"active":true,"deleted":false}""", json)
    }

    @Test
    fun `object with multiple fields`() {
        val json = JsonWriter.build {
            writeObject {
                writeField("name", "Bob")
                writeField("age", 25)
                writeField("active", true)
            }
        }
        assertEquals("""{"name":"Bob","age":25,"active":true}""", json)
    }

    @Test
    fun `nested object`() {
        val json = JsonWriter.build {
            writeObject {
                writeFieldObject("inner") {
                    writeField("x", 1)
                }
            }
        }
        assertEquals("""{"inner":{"x":1}}""", json)
    }

    @Test
    fun `array with values`() {
        val json = JsonWriter.build {
            writeArray {
                writeValue(1)
                writeValue(2)
                writeValue(3)
            }
        }
        assertEquals("[1,2,3]", json)
    }

    @Test
    fun `array with string values`() {
        val json = JsonWriter.build {
            writeArray {
                writeValue("a")
                writeValue("b")
            }
        }
        assertEquals("""["a","b"]""", json)
    }

    @Test
    fun `array with boolean values`() {
        val json = JsonWriter.build {
            writeArray {
                writeValue(true)
                writeValue(false)
            }
        }
        assertEquals("[true,false]", json)
    }

    @Test
    fun `array with object values`() {
        val json = JsonWriter.build {
            writeArray {
                writeValueObject {
                    writeField("id", 1)
                }
                writeValueObject {
                    writeField("id", 2)
                }
            }
        }
        assertEquals("""[{"id":1},{"id":2}]""", json)
    }

    @Test
    fun `object with array field`() {
        val json = JsonWriter.build {
            writeObject {
                writeFieldArray("items") {
                    writeValue(10)
                    writeValue(20)
                }
            }
        }
        assertEquals("""{"items":[10,20]}""", json)
    }

    // --- String escaping ---

    @Test
    fun `escapes double quotes`() {
        val json = JsonWriter.build {
            writeObject {
                writeField("val", """he said "hello"""")
            }
        }
        assertEquals("""{"val":"he said \"hello\""}""", json)
    }

    @Test
    fun `escapes backslash`() {
        val json = JsonWriter.build {
            writeObject {
                writeField("path", """C:\temp""")
            }
        }
        assertEquals("""{"path":"C:\\temp"}""", json)
    }

    @Test
    fun `escapes newline and tab`() {
        val json = JsonWriter.build {
            writeObject {
                writeField("text", "line1\nline2\tend")
            }
        }
        assertEquals("""{"text":"line1\nline2\tend"}""", json)
    }

    @Test
    fun `escapes carriage return and backspace`() {
        val json = JsonWriter.build {
            writeObject {
                writeField("text", "a\r\bb")
            }
        }
        assertEquals("""{"text":"a\r\bb"}""", json)
    }

    @Test
    fun `escapes form feed`() {
        val json = JsonWriter.build {
            writeObject {
                writeField("text", "a\u000Cb")
            }
        }
        assertEquals("""{"text":"a\fb"}""", json)
    }

    @Test
    fun `escapes control characters as unicode`() {
        val json = JsonWriter.build {
            writeObject {
                writeField("text", "a\u0001b")
            }
        }
        assertEquals("""{"text":"a\u0001b"}""", json)
    }

    @Test
    fun `escapes null character`() {
        val json = JsonWriter.build {
            writeObject {
                writeField("text", "a\u0000b")
            }
        }
        assertEquals("""{"text":"a\u0000b"}""", json)
    }

    @Test
    fun `handles empty string`() {
        val json = JsonWriter.build {
            writeObject {
                writeField("empty", "")
            }
        }
        assertEquals("""{"empty":""}""", json)
    }

    @Test
    fun `handles unicode characters without escaping`() {
        val json = JsonWriter.build {
            writeObject {
                writeField("emoji", "\u4E16\u754C")  // 世界
            }
        }
        // CJK characters should pass through without escaping
        assertEquals("{\"emoji\":\"\u4E16\u754C\"}", json)
    }

    // --- Complex nested structures ---

    @Test
    fun `deeply nested structure`() {
        val json = JsonWriter.build {
            writeObject {
                writeFieldObject("level1") {
                    writeFieldArray("items") {
                        writeValueObject {
                            writeField("name", "x")
                            writeField("value", 42)
                        }
                    }
                }
            }
        }
        assertEquals("""{"level1":{"items":[{"name":"x","value":42}]}}""", json)
    }

    @Test
    fun `negative integer`() {
        val json = JsonWriter.build {
            writeObject {
                writeField("offset", -5)
            }
        }
        assertEquals("""{"offset":-5}""", json)
    }

    @Test
    fun `zero integer`() {
        val json = JsonWriter.build {
            writeObject {
                writeField("count", 0)
            }
        }
        assertEquals("""{"count":0}""", json)
    }
}