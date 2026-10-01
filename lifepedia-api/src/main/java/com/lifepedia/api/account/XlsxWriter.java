package com.lifepedia.api.account;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Planilha .xlsx mínima (texto, número, dinheiro, data e mês), sem fórmulas e sem dependência externa. */
class XlsxWriter {

    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);
    private static final int STYLE_HEADER = 1, STYLE_DATE = 2, STYLE_MONTH = 3, STYLE_MONEY = 4;
    private static final int MAX_WIDTH = 60;

    private final List<Sheet> sheets = new ArrayList<>();

    Sheet sheet(String name, String... headers) {
        Sheet sheet = new Sheet(name, headers);
        sheets.add(sheet);
        return sheet;
    }

    byte[] toBytes() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", contentTypes());
            put(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">\
                    <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>\
                    </Relationships>""");
            put(zip, "xl/workbook.xml", workbook());
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels());
            put(zip, "xl/styles.xml", STYLES);
            for (int i = 0; i < sheets.size(); i++) put(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", sheets.get(i).xml());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    /** Aba com cabeçalho fixo e filtro. */
    static final class Sheet {

        private final String name;
        private final int[] widths;
        private final StringBuilder rows = new StringBuilder();
        private int count;

        private Sheet(String name, String[] headers) {
            this.name = name;
            this.widths = new int[headers.length];
            Arrays.fill(widths, 8);
            append(STYLE_HEADER, (Object[]) headers);
        }

        /** Valores na ordem do cabeçalho; nulo = célula vazia. */
        void row(Object... values) {
            append(0, values);
        }

        private void append(int textStyle, Object... values) {
            count++;
            rows.append("<row r=\"").append(count).append("\">");
            for (int i = 0; i < values.length && i < widths.length; i++) {
                Object v = values[i];
                if (v == null) continue;
                String ref = column(i) + count;
                switch (v) {
                    case BigDecimal n -> number(ref, n.toPlainString(), STYLE_MONEY, 14, i);
                    case Integer n -> number(ref, n.toString(), 0, 4, i);
                    case LocalDate d -> number(ref, Long.toString(ChronoUnit.DAYS.between(EXCEL_EPOCH, d)), STYLE_DATE, 11, i);
                    case YearMonth m -> number(ref, Long.toString(ChronoUnit.DAYS.between(EXCEL_EPOCH, m.atDay(1))),
                            STYLE_MONTH, 8, i);
                    default -> {
                        String text = v.toString();
                        rows.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"")
                                .append(textStyle == 0 ? "" : " s=\"" + textStyle + "\"")
                                .append("><is><t xml:space=\"preserve\">").append(escape(text)).append("</t></is></c>");
                        widths[i] = Math.max(widths[i], Math.min(MAX_WIDTH, text.length() + 2));
                    }
                }
            }
            rows.append("</row>");
        }

        private void number(String ref, String value, int style, int width, int column) {
            rows.append("<c r=\"").append(ref).append('"').append(style == 0 ? "" : " s=\"" + style + "\"")
                    .append("><v>").append(value).append("</v></c>");
            widths[column] = Math.max(widths[column], width);
        }

        private String xml() {
            StringBuilder cols = new StringBuilder("<cols>");
            for (int i = 0; i < widths.length; i++) {
                cols.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1).append("\" width=\"")
                        .append(widths[i]).append("\" customWidth=\"1\"/>");
            }
            cols.append("</cols>");
            return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                    + "<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\""
                    + " state=\"frozen\"/></sheetView></sheetViews>"
                    + cols + "<sheetData>" + rows + "</sheetData>"
                    + "<autoFilter ref=\"A1:" + column(widths.length - 1) + count + "\"/></worksheet>";
        }
    }

    private String contentTypes() {
        StringBuilder s = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">\
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>\
                <Default Extension="xml" ContentType="application/xml"/>\
                <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>\
                <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""");
        for (int i = 1; i <= sheets.size(); i++) {
            s.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return s.append("</Types>").toString();
    }

    private String workbook() {
        StringBuilder s = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
                + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
        for (int i = 1; i <= sheets.size(); i++) {
            s.append("<sheet name=\"").append(escape(sheets.get(i - 1).name)).append("\" sheetId=\"").append(i)
                    .append("\" r:id=\"rId").append(i).append("\"/>");
        }
        s.append("</sheets><definedNames>");
        for (int i = 0; i < sheets.size(); i++) {
            Sheet sheet = sheets.get(i);
            s.append("<definedName name=\"_xlnm._FilterDatabase\" localSheetId=\"").append(i).append("\" hidden=\"1\">'")
                    .append(escape(sheet.name.replace("'", "''"))).append("'!$A$1:$").append(column(sheet.widths.length - 1))
                    .append('$').append(sheet.count).append("</definedName>");
        }
        return s.append("</definedNames></workbook>").toString();
    }

    private String workbookRels() {
        StringBuilder s = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 1; i <= sheets.size(); i++) {
            s.append("<Relationship Id=\"rId").append(i)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\"")
                    .append(" Target=\"worksheets/sheet").append(i).append(".xml\"/>");
        }
        int styles = sheets.size() + 1;
        s.append("<Relationship Id=\"rId").append(styles)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        return s.append("</Relationships>").toString();
    }

    private static final String STYLES = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">\
            <numFmts count="3"><numFmt numFmtId="164" formatCode="dd/mm/yyyy"/><numFmt numFmtId="165" formatCode="mm/yyyy"/>\
            <numFmt numFmtId="166" formatCode="#,##0.00"/></numFmts>\
            <fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>\
            <fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>\
            <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>\
            <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>\
            <cellXfs count="5"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>\
            <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>\
            <xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>\
            <xf numFmtId="165" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>\
            <xf numFmtId="166" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs>\
            <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>\
            </styleSheet>""";

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /** 0 → A, 25 → Z, 26 → AA. */
    static String column(int index) {
        StringBuilder s = new StringBuilder();
        for (int i = index + 1; i > 0; i = (i - 1) / 26) s.insert(0, (char) ('A' + (i - 1) % 26));
        return s.toString();
    }

    /** Escapa XML e tira caracteres de controle que o XML não aceita. */
    static String escape(String text) {
        StringBuilder s = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            switch (c) {
                case '&' -> s.append("&amp;");
                case '<' -> s.append("&lt;");
                case '>' -> s.append("&gt;");
                case '"' -> s.append("&quot;");
                default -> {
                    if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') s.append(c);
                }
            }
        }
        return s.toString();
    }
}
