package me.vektory79.tinyc

import java.util.logging.Formatter
import java.util.logging.LogRecord

class LogFormatter : Formatter() {
    override fun format(record: LogRecord): String {
        return "${record.level.toString().substring(0, 1)}: ${record.message}\n"
    }
}