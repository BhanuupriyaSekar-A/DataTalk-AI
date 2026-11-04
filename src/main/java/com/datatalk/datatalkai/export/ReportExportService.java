package com.datatalk.datatalkai.export;

import com.datatalk.datatalkai.reporting.ReportExecutionService;
import com.datatalk.datatalkai.reporting.SecureQuery;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Writes real query results to PDF (OpenPDF) or Excel (Apache POI streaming workbook).
 * Rows are streamed from the database, so large exports never sit in memory as a whole.
 */
@Service
public class ReportExportService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int PDF_CHUNK = 500;

    private final ReportExecutionService executionService;
    private final int maxRows;

    public ReportExportService(ReportExecutionService executionService,
                               @Value("${app.export.max-rows:50000}") int maxRows) {
        this.executionService = executionService;
        this.maxRows = maxRows;
    }

    public int maxRows() {
        return maxRows;
    }

    /** Writes the report and returns the number of exported rows. */
    public long write(ExportFormat format, SecureQuery query, String title, String generatedFor, OutputStream out) {
        return format == ExportFormat.PDF
                ? writePdf(query, title, generatedFor, out)
                : writeExcel(query, title, generatedFor, out);
    }

    private long writePdf(SecureQuery query, String title, String generatedFor, OutputStream out) {
        Document doc = new Document(PageSize.A4.rotate(), 24, 24, 24, 24);
        PdfWriter.getInstance(doc, out);
        doc.open();
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        Font infoFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY);
        Font headFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, 8);
        doc.add(new Paragraph(title, titleFont));
        doc.add(new Paragraph("DataTalk AI report - generated " + LocalDateTime.now().format(STAMP)
                + " for " + generatedFor, infoFont));
        doc.add(new Paragraph(" ", infoFont));

        AtomicLong count = new AtomicLong();
        PdfPTable[] table = new PdfPTable[1];
        executionService.stream(query, maxRows, new ReportExecutionService.RowSink() {
            @Override
            public void start(List<String> columns, List<String> columnTypes) {
                PdfPTable t = new PdfPTable(columns.size());
                t.setWidthPercentage(100);
                t.setHeaderRows(1);
                t.setComplete(false);
                for (String c : columns) {
                    PdfPCell cell = new PdfPCell(new Phrase(c, headFont));
                    cell.setBackgroundColor(new Color(13, 110, 253));
                    t.addCell(cell);
                }
                table[0] = t;
            }

            @Override
            public void row(List<Object> values) {
                for (Object v : values) {
                    PdfPCell cell = new PdfPCell(new Phrase(format(v), cellFont));
                    if (v instanceof Number) {
                        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    }
                    table[0].addCell(cell);
                }
                if (count.incrementAndGet() % PDF_CHUNK == 0) {
                    doc.add(table[0]);  // flush rows already laid out to keep memory low
                }
            }
        });
        if (table[0] != null) {
            table[0].setComplete(true);
            doc.add(table[0]);
        }
        doc.add(new Paragraph(" ", infoFont));
        doc.add(new Paragraph(count.get() + " row(s)" + (count.get() >= maxRows ? " (export limit reached)" : ""), infoFont));
        doc.close();
        return count.get();
    }

    private long writeExcel(SecureQuery query, String title, String generatedFor, OutputStream out) {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(200)) {
            SXSSFSheet sheet = wb.createSheet("Report");
            CellStyle bold = wb.createCellStyle();
            org.apache.poi.ss.usermodel.Font boldFont = wb.createFont();
            boldFont.setBold(true);
            bold.setFont(boldFont);
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd hh:mm"));

            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue(title);
            titleRow.getCell(0).setCellStyle(bold);
            sheet.createRow(1).createCell(0).setCellValue("Generated " + LocalDateTime.now().format(STAMP) + " for " + generatedFor);

            AtomicLong count = new AtomicLong();
            int[] rowIndex = {3};
            executionService.stream(query, maxRows, new ReportExecutionService.RowSink() {
                @Override
                public void start(List<String> columns, List<String> columnTypes) {
                    Row header = sheet.createRow(rowIndex[0]++);
                    for (int i = 0; i < columns.size(); i++) {
                        Cell c = header.createCell(i);
                        c.setCellValue(columns.get(i));
                        c.setCellStyle(bold);
                        sheet.setColumnWidth(i, 18 * 256);
                    }
                }

                @Override
                public void row(List<Object> values) {
                    Row r = sheet.createRow(rowIndex[0]++);
                    for (int i = 0; i < values.size(); i++) {
                        Object v = values.get(i);
                        Cell c = r.createCell(i);
                        if (v instanceof Number n) {
                            c.setCellValue(n.doubleValue());
                        } else if (v instanceof LocalDateTime dt) {
                            c.setCellValue(dt);
                            c.setCellStyle(dateStyle);
                        } else if (v != null) {
                            c.setCellValue(v.toString());
                        }
                    }
                    count.incrementAndGet();
                }
            });
            wb.write(out);
            wb.dispose();
            return count.get();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String format(Object v) {
        if (v == null) {
            return "";
        }
        if (v instanceof BigDecimal bd) {
            return bd.toPlainString();
        }
        if (v instanceof LocalDateTime dt) {
            return dt.format(STAMP);
        }
        return v.toString();
    }

    public static String fileName(String title, ExportFormat format) {
        String base = title == null ? "report" : title.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isEmpty()) {
            base = "report";
        }
        if (base.length() > 60) {
            base = base.substring(0, 60);
        }
        return base.toLowerCase() + "-" + java.time.LocalDate.now() + "." + format.extension();
    }
}
