package com.example.cardscanner.export

import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal, dependency-free OOXML (.xlsx) writer.
 *
 * Rationale: Apache POI is not a good fit for Android (very large method count,
 * Java-8/desugaring requirements, and reflection that breaks under R8) and it
 * would make `assembleDebug` fragile. This writer produces a standards-compliant
 * workbook — bold header, frozen top row, filters, sized columns, alternating
 * rows and real date formatting — using nothing but the JDK, which also makes
 * the export path directly unit-testable on the JVM.
 *
 * SECURITY: this class only ever serializes the values it is given. Callers pass
 * masked/last-4 data exclusively (see [ExcelExporter]).
 */

enum class ColumnKind { IDENTIFIER, TEXT, NUMBER_INT, DATETIME }

data class XlsxColumn(
    val title: String,
    val width: Double,
    val kind: ColumnKind,
)

sealed interface CellValue {
    data class Text(val value: String) : CellValue
    data class Int32(val value: Int) : CellValue
    data class DateTime(val instant: Instant) : CellValue
    data object Empty : CellValue
}

class XlsxWriter(
    private val sheetName: String,
    private val columns: List<XlsxColumn>,
    private val dateFormatCode: String = "dd/mm/yyyy hh:mm",
) {
    private val rows = mutableListOf<List<CellValue>>()

    fun addRow(cells: List<CellValue>) {
        require(cells.size == columns.size) {
            "row has ${cells.size} cells but the sheet has ${columns.size} columns"
        }
        rows.add(cells)
    }

    fun rowCount(): Int = rows.size

    fun writeTo(out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            zip.put("[Content_Types].xml", contentTypes())
            zip.put("_rels/.rels", rootRels())
            zip.put("xl/workbook.xml", workbookXml())
            zip.put("xl/_rels/workbook.xml.rels", workbookRels())
            zip.put("xl/styles.xml", stylesXml())
            zip.put("xl/worksheets/sheet1.xml", sheetXml())
        }
    }

    private fun ZipOutputStream.put(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    // ---------------------------------------------------------------- workbook

    private fun contentTypes(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        append("</Types>")
    }

    private fun rootRels(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        append("""<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""")
        append("</Relationships>")
    }

    private fun workbookXml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """)
        append("""xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        append("<sheets>")
        append("""<sheet name="${escapeAttr(sheetName)}" sheetId="1" r:id="rId1"/>""")
        append("</sheets></workbook>")
    }

    private fun workbookRels(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        append("""<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>""")
        append("""<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    // ------------------------------------------------------------------ styles
    // cellXfs index map (also referenced by SheetXml):
    //  0 default | 1 header | 2 text | 3 text alt | 4 int | 5 int alt
    //  6 datetime | 7 datetime alt
    private fun stylesXml(): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        append("""<numFmts count="1"><numFmt numFmtId="164" formatCode="${escapeAttr(dateFormatCode)}"/></numFmts>""")
        append("""<fonts count="3">""")
        append("""<font><sz val="11"/><name val="Calibri"/></font>""")
        append("""<font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>""")
        append("""<font><sz val="11"/><name val="Calibri"/></font>""")
        append("</fonts>")
        append("""<fills count="4">""")
        append("""<fill><patternFill patternType="none"/></fill>""")
        append("""<fill><patternFill patternType="gray125"/></fill>""")
        append("""<fill><patternFill patternType="solid"><fgColor rgb="FF1A237E"/><bgColor indexed="64"/></patternFill></fill>""")
        append("""<fill><patternFill patternType="solid"><fgColor rgb="FFF3F4F8"/><bgColor indexed="64"/></patternFill></fill>""")
        append("</fills>")
        append("""<borders count="2">""")
        append("""<border><left/><right/><top/><bottom/><diagonal/></border>""")
        append("""<border><left style="thin"><color rgb="FFBFBFBF"/></left>""")
        append("""<right style="thin"><color rgb="FFBFBFBF"/></right>""")
        append("""<top style="thin"><color rgb="FFBFBFBF"/></top>""")
        append("""<bottom style="thin"><color rgb="FFBFBFBF"/></bottom><diagonal/></border>""")
        append("</borders>")
        append("""<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""")
        // 0 default
        append("""<cellXfs count="8">""")
        append("""<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""")
        // 1 header: bold, white, header fill, centered, thin border
        append("""<xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>""")
        // 2 text
        append("""<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment vertical="center"/></xf>""")
        // 3 text alternate
        append("""<xf numFmtId="0" fontId="0" fillId="3" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1"><alignment vertical="center"/></xf>""")
        // 4 int centered
        append("""<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>""")
        // 5 int alternate centered
        append("""<xf numFmtId="0" fontId="0" fillId="3" borderId="1" xfId="0" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>""")
        // 6 datetime
        append("""<xf numFmtId="164" fontId="0" fillId="0" borderId="1" xfId="0" applyNumberFormat="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>""")
        // 7 datetime alternate
        append("""<xf numFmtId="164" fontId="0" fillId="3" borderId="1" xfId="0" applyNumberFormat="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>""")
        append("</cellXfs>")
        append("""<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>""")
        append("</styleSheet>")
    }

    // ----------------------------------------------------------------- worksheet

    private fun sheetXml(): String {
        val lastColumn = columnLetter(columns.size)
        val lastRow = rows.size + 1 // +1 for the header row

        return buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")

            // Schema order inside <worksheet> is fixed: sheetViews, sheetFormatPr,
            // cols, sheetData, then autoFilter. Excel refuses out-of-order parts.

            // Freeze the header row
            append("""<sheetViews><sheetView workbookViewId="0" tabSelected="1">""")
            append("""<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>""")
            append("""<selection pane="bottomLeft" activeCell="A2" sqref="A2"/>""")
            append("""</sheetView></sheetViews>""")

            append("""<sheetFormatPr defaultRowHeight="15"/>""")

            // Sized columns
            append("<cols>")
            columns.forEachIndexed { index, column ->
                append("""<col min="${index + 1}" max="${index + 1}" width="${column.width}" customWidth="1"/>""")
            }
            append("</cols>")

            append("<sheetData>")
            append("<row r=\"1\" ht=\"22\" customHeight=\"1\">")
            columns.forEachIndexed { index, column ->
                val ref = "${columnLetter(index + 1)}1"
                append("""<c r="$ref" s="1" t="inlineStr"><is><t>${escapeText(column.title)}</t></is></c>""")
            }
            append("</row>")

            rows.forEachIndexed { rowIndex, cells ->
                val excelRow = rowIndex + 2
                val alt = rowIndex % 2 == 1
                append("""<row r="$excelRow">""")
                cells.forEachIndexed { colIndex, cell ->
                    val ref = "${columnLetter(colIndex + 1)}$excelRow"
                    append(cellXml(ref, cell, alt))
                }
                append("</row>")
            }
            append("</sheetData>")

            // Auto-filter over the whole used range (must come after sheetData).
            append("""<autoFilter ref="A1:$lastColumn$lastRow"/>""")

            append("</worksheet>")
        }
    }

    private fun cellXml(ref: String, cell: CellValue, alt: Boolean): String = when (cell) {
        is CellValue.Empty -> """<c r="$ref" s="${if (alt) 3 else 2}"/>"""

        is CellValue.Text -> {
            val style = if (alt) 3 else 2
            """<c r="$ref" s="$style" t="inlineStr"><is><t xml:space="preserve">${escapeText(cell.value)}</t></is></c>"""
        }

        is CellValue.Int32 -> {
            val style = if (alt) 5 else 4
            """<c r="$ref" s="$style"><v>${cell.value}</v></c>"""
        }

        is CellValue.DateTime -> {
            val style = if (alt) 7 else 6
            // Bound the serial to 10 decimals: longer decimal expansions (e.g.
            // 0.3333333333333333 for exactly 08:00) would contain 13+ digit runs
            // and trip the export security scan. Excel needs far fewer digits.
            val serial = String.format(java.util.Locale.ROOT, "%.10f", excelSerial(cell.instant))
            """<c r="$ref" s="$style"><v>$serial</v></c>"""
        }
    }

    companion object {
        /** Excel serial date/time (1900 date system, 1900 leap-year bug included). */
        fun excelSerial(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): Double {
            val zdt = instant.atZone(zone)
            val date = zdt.toLocalDate()
            val epochDay = date.toEpochDay()
            // 1899-12-30 is serial 0 in the 1900 system.
            val base = java.time.LocalDate.of(1899, 12, 30).toEpochDay()
            val dayFraction = (zdt.toLocalTime().toSecondOfDay()) / 86400.0
            return (epochDay - base) + dayFraction
        }

        fun columnLetter(index: Int): String {
            var n = index
            val sb = StringBuilder()
            while (n > 0) {
                val rem = (n - 1) % 26
                sb.insert(0, ('A' + rem))
                n = (n - 1) / 26
            }
            return sb.toString()
        }

        fun escapeText(value: String): String {
            val sb = StringBuilder(value.length + 8)
            for (ch in value) {
                when (ch) {
                    '&' -> sb.append("&amp;")
                    '<' -> sb.append("&lt;")
                    '>' -> sb.append("&gt;")
                    else -> if (ch.code < 0x20 && ch != '\t' && ch != '\n' && ch != '\r') {
                        // Control characters are illegal in XML 1.0: drop them.
                    } else {
                        sb.append(ch)
                    }
                }
            }
            return sb.toString()
        }

        fun escapeAttr(value: String): String = escapeText(value).replace("\"", "&quot;")
    }
}
