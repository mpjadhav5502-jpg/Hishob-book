package com.example.data.ocr

/**
 * Lightweight pure-Kotlin RFC-8259 JSON parser so structured OCR JSON parsing
 * works identically on Android runtime and pure-JVM unit tests without Android stubs.
 */
internal object SimpleJsonParser {

    fun parseObject(json: String): Map<String, Any?> {
        val parser = Parser(json)
        val value = parser.readValue()
        @Suppress("UNCHECKED_CAST")
        return (value as? Map<String, Any?>)
            ?: throw IllegalArgumentException("Expected top-level JSON object")
    }

    private class Parser(private val src: String) {
        private var pos = 0

        fun readValue(): Any? {
            skipWhitespace()
            if (pos >= src.length) throw IllegalArgumentException("Unexpected end of JSON")
            return when (val c = src[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                '-', in '0'..'9' -> readNumber()
                else -> throw IllegalArgumentException("Unexpected char '$c' at $pos")
            }
        }

        private fun readObject(): Map<String, Any?> {
            pos++ // '{'
            val map = linkedMapOf<String, Any?>()
            skipWhitespace()
            if (pos < src.length && src[pos] == '}') {
                pos++
                return map
            }
            while (pos < src.length) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                val value = readValue()
                map[key] = value
                skipWhitespace()
                if (pos < src.length && src[pos] == ',') {
                    pos++
                } else if (pos < src.length && src[pos] == '}') {
                    pos++
                    return map
                } else {
                    throw IllegalArgumentException("Malformed JSON object at $pos")
                }
            }
            throw IllegalArgumentException("Unterminated JSON object")
        }

        private fun readArray(): List<Any?> {
            pos++ // '['
            val list = mutableListOf<Any?>()
            skipWhitespace()
            if (pos < src.length && src[pos] == ']') {
                pos++
                return list
            }
            while (pos < src.length) {
                list.add(readValue())
                skipWhitespace()
                if (pos < src.length && src[pos] == ',') {
                    pos++
                } else if (pos < src.length && src[pos] == ']') {
                    pos++
                    return list
                } else {
                    throw IllegalArgumentException("Malformed JSON array at $pos")
                }
            }
            throw IllegalArgumentException("Unterminated JSON array")
        }

        private fun readString(): String {
            expect('"')
            val sb = StringBuilder()
            while (pos < src.length) {
                val c = src[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= src.length) throw IllegalArgumentException("Unterminated escape")
                        when (val esc = src[pos++]) {
                            '"', '\\', '/' -> sb.append(esc)
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                val hex = src.substring(pos, pos + 4)
                                sb.append(hex.toInt(16).toChar())
                                pos += 4
                            }
                            else -> sb.append(esc)
                        }
                    }
                    else -> sb.append(c)
                }
            }
            throw IllegalArgumentException("Unterminated JSON string")
        }

        private fun readNumber(): Double {
            val start = pos
            if (src[pos] == '-') pos++
            while (pos < src.length && (src[pos].isDigit() || src[pos] in ".eE+-")) {
                pos++
            }
            return src.substring(start, pos).toDouble()
        }

        private fun readLiteral(expected: String, value: Any?): Any? {
            if (src.startsWith(expected, pos)) {
                pos += expected.length
                return value
            }
            throw IllegalArgumentException("Invalid literal at $pos")
        }

        private fun expect(ch: Char) {
            if (pos >= src.length || src[pos] != ch) {
                throw IllegalArgumentException("Expected '$ch' at $pos")
            }
            pos++
        }

        private fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }
    }
}
