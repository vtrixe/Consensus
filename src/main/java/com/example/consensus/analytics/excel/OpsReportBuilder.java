package com.example.consensus.analytics.excel;

import com.example.consensus.model.entity.TradeBreak;
import com.example.consensus.model.entity.TradeBreakAudit;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class OpsReportBuilder {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public XSSFWorkbook build(List<TradeBreak> breaks, List<TradeBreakAudit> audits) {
        XSSFWorkbook wb = new XSSFWorkbook();

        // resolvedBy lookup: breakId → changedBy where toStatus=RESOLVED
        Map<Long, String> resolvedBy = audits.stream()
                .filter(a -> "RESOLVED".equals(a.getToStatus() != null ? a.getToStatus().name() : ""))
                .collect(Collectors.toMap(
                        a -> a.getTradeBreak().getId(),
                        a -> a.getChangedBy() != null ? a.getChangedBy() : "SYSTEM",
                        (a, b) -> a
                ));

        Styles s = new Styles(wb);
        buildRawData(wb, breaks, resolvedBy, s);
        buildSummary(wb, s);

        wb.setActiveSheet(1); // open on Summary
        return wb;
    }

    private void buildRawData(XSSFWorkbook wb, List<TradeBreak> breaks,
                              Map<Long, String> resolvedBy, Styles s) {
        XSSFSheet sheet = wb.createSheet("RAW_DATA");
        sheet.createFreezePane(0, 1);

        String[] headers = {
            "Break ID", "Trade ID", "Break Type", "Status", "Materiality",
            "Notional Impact", "BPS Deviation", "Settle Fail Risk", "SLA Breached",
            "Detected At", "Resolved At", "Age (mins)", "Resolved By"
        };

        XSSFRow hRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            XSSFCell c = hRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(s.header);
        }

        int rowIdx = 1;
        for (TradeBreak b : breaks) {
            XSSFRow row = sheet.createRow(rowIdx);
            XSSFCellStyle base = (rowIdx % 2 == 0) ? s.alt : s.data;

            cell(row, 0, b.getId(), base);
            cell(row, 1, b.getTradeId(), base);
            cell(row, 2, b.getBreakType() != null ? b.getBreakType().name() : "", base);
            cell(row, 3, b.getStatus() != null ? b.getStatus().name() : "", base);
            cell(row, 4, b.getMaterialityTier() != null ? b.getMaterialityTier().name() : "", base);
            cell(row, 5, b.getNotionalImpact() != null ? b.getNotionalImpact().doubleValue() : 0, s.currency);
            cell(row, 6, b.getBpsDeviation() != null ? b.getBpsDeviation().doubleValue() : 0, base);
            cell(row, 7, b.getSettlementFailRisk(), base);
            cell(row, 8, b.getSlaBreached(), base);
            cell(row, 9, b.getDetectedAt() != null ? b.getDetectedAt().format(FMT) : "", base);
            cell(row, 10, b.getResolvedAt() != null ? b.getResolvedAt().format(FMT) : "", base);

            long ageMinutes = ageMinutes(b.getDetectedAt(), b.getResolvedAt());
            cell(row, 11, ageMinutes, base);
            cell(row, 12, resolvedBy.getOrDefault(b.getId(), ""), base);
            rowIdx++;
        }

        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));
        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
    }

    private void buildSummary(XSSFWorkbook wb, Styles s) {
        XSSFSheet sheet = wb.createSheet("SUMMARY");

        // Title
        XSSFRow title = sheet.createRow(0);
        XSSFCell titleCell = title.createCell(0);
        titleCell.setCellValue("OPS MORNING REPORT — " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        titleCell.setCellStyle(s.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 2));

        // Column headers row
        XSSFRow hRow = sheet.createRow(2);
        cell(hRow, 0, "Metric", s.header);
        cell(hRow, 1, "Value", s.header);

        // KPI rows — formulas reference RAW_DATA sheet
        String[][] kpis = {
            {"Total Breaks",              "COUNTA(RAW_DATA!$A:$A)-1"},
            {"Open Breaks",               "COUNTIF(RAW_DATA!$D:$D,\"OPEN\")"},
            {"Resolved Breaks",           "COUNTIF(RAW_DATA!$D:$D,\"RESOLVED\")"},
            {"Resolution Rate %",         "IFERROR(COUNTIF(RAW_DATA!$D:$D,\"RESOLVED\")/(COUNTA(RAW_DATA!$A:$A)-1),0)"},
            {"Auto-Resolve Rate %",       "IFERROR(COUNTIF(RAW_DATA!$M:$M,\"SYSTEM\")/COUNTIF(RAW_DATA!$D:$D,\"RESOLVED\"),0)"},
            {"Avg MTTR (hours)",          "IFERROR(AVERAGEIF(RAW_DATA!$D:$D,\"RESOLVED\",RAW_DATA!$L:$L)/60,0)"},
            {"SLA Breach Rate %",         "IFERROR(COUNTIF(RAW_DATA!$I:$I,TRUE)/(COUNTA(RAW_DATA!$A:$A)-1),0)"},
            {"Settlement Fail Risk Count","COUNTIF(RAW_DATA!$H:$H,TRUE)"},
            {"Critical Open",             "COUNTIFS(RAW_DATA!$D:$D,\"OPEN\",RAW_DATA!$E:$E,\"CRITICAL\")"},
            {"Major Open",                "COUNTIFS(RAW_DATA!$D:$D,\"OPEN\",RAW_DATA!$E:$E,\"MAJOR\")"},
        };

        // row index → style for value column
        // Row 3 = idx 0 (Total), Row 6 = idx 3 (Resolution Rate %), etc.
        int startRow = 3;
        for (int i = 0; i < kpis.length; i++) {
            XSSFRow row = sheet.createRow(startRow + i);
            cell(row, 0, kpis[i][0], s.label);
            XSSFCell valCell = row.createCell(1);
            valCell.setCellFormula(kpis[i][1]);

            // pick style based on metric type
            if (i == 3 || i == 4 || i == 6) {
                valCell.setCellStyle(s.percent);
            } else {
                valCell.setCellStyle(s.value);
            }
        }

        // Conditional formatting
        SheetConditionalFormatting scf = sheet.getSheetConditionalFormatting();

        // Resolution Rate (row 6, Excel row 7, cell B7) — green ≥80%, amber ≥50%, red <50%
        applyTrafficLight(scf, "B6", "0.8", "0.5");

        // Auto-Resolve Rate (B7) — green ≥70%, amber ≥40%, red <40%
        applyTrafficLight(scf, "B7", "0.7", "0.4");

        // SLA Breach Rate (B9) — inverted: green <10%, amber <25%, red ≥25%
        applyInvertedTrafficLight(scf, "B9", "0.1", "0.25");

        // Critical Open (B11) — green =0, red >0
        applyZeroGreenNonzeroRed(scf, "B11");

        sheet.setColumnWidth(0, 9000);
        sheet.setColumnWidth(1, 5000);
    }

    // ── Conditional formatting helpers ────────────────────────────────────

    private void applyTrafficLight(SheetConditionalFormatting scf, String cell,
                                   String greenThreshold, String amberThreshold) {
        CellRangeAddress[] range = {CellRangeAddress.valueOf(cell)};

        ConditionalFormattingRule green = scf.createConditionalFormattingRule(ComparisonOperator.GE, greenThreshold);
        green.createPatternFormatting().setFillBackgroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        green.createPatternFormatting().setFillPattern(PatternFormatting.SOLID_FOREGROUND);

        ConditionalFormattingRule amber = scf.createConditionalFormattingRule(ComparisonOperator.GE, amberThreshold);
        amber.createPatternFormatting().setFillBackgroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        amber.createPatternFormatting().setFillPattern(PatternFormatting.SOLID_FOREGROUND);

        ConditionalFormattingRule red = scf.createConditionalFormattingRule(ComparisonOperator.LT, amberThreshold);
        red.createPatternFormatting().setFillBackgroundColor(IndexedColors.ROSE.getIndex());
        red.createPatternFormatting().setFillPattern(PatternFormatting.SOLID_FOREGROUND);

        scf.addConditionalFormatting(range, new ConditionalFormattingRule[]{green, amber, red});
    }

    private void applyInvertedTrafficLight(SheetConditionalFormatting scf, String cell,
                                           String greenMax, String amberMax) {
        CellRangeAddress[] range = {CellRangeAddress.valueOf(cell)};

        ConditionalFormattingRule green = scf.createConditionalFormattingRule(ComparisonOperator.LT, greenMax);
        green.createPatternFormatting().setFillBackgroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        green.createPatternFormatting().setFillPattern(PatternFormatting.SOLID_FOREGROUND);

        ConditionalFormattingRule amber = scf.createConditionalFormattingRule(ComparisonOperator.LT, amberMax);
        amber.createPatternFormatting().setFillBackgroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        amber.createPatternFormatting().setFillPattern(PatternFormatting.SOLID_FOREGROUND);

        ConditionalFormattingRule red = scf.createConditionalFormattingRule(ComparisonOperator.GE, amberMax);
        red.createPatternFormatting().setFillBackgroundColor(IndexedColors.ROSE.getIndex());
        red.createPatternFormatting().setFillPattern(PatternFormatting.SOLID_FOREGROUND);

        scf.addConditionalFormatting(range, new ConditionalFormattingRule[]{green, amber, red});
    }

    private void applyZeroGreenNonzeroRed(SheetConditionalFormatting scf, String cell) {
        CellRangeAddress[] range = {CellRangeAddress.valueOf(cell)};

        ConditionalFormattingRule green = scf.createConditionalFormattingRule(ComparisonOperator.EQUAL, "0");
        green.createPatternFormatting().setFillBackgroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        green.createPatternFormatting().setFillPattern(PatternFormatting.SOLID_FOREGROUND);

        ConditionalFormattingRule red = scf.createConditionalFormattingRule(ComparisonOperator.GT, "0");
        red.createPatternFormatting().setFillBackgroundColor(IndexedColors.ROSE.getIndex());
        red.createPatternFormatting().setFillPattern(PatternFormatting.SOLID_FOREGROUND);

        scf.addConditionalFormatting(range, new ConditionalFormattingRule[]{green, red});
    }

    // ── Cell helpers ─────────────────────────────────────────────────────

    private void cell(XSSFRow row, int col, Object value, XSSFCellStyle style) {
        XSSFCell c = row.createCell(col);
        if (value instanceof String v)       c.setCellValue(v);
        else if (value instanceof Number v)  c.setCellValue(v.doubleValue());
        else if (value instanceof Boolean v) c.setCellValue(v);
        else if (value != null)              c.setCellValue(value.toString());
        if (style != null) c.setCellStyle(style);
    }

    private long ageMinutes(LocalDateTime detected, LocalDateTime resolved) {
        if (detected == null) return 0;
        LocalDateTime end = resolved != null ? resolved : LocalDateTime.now();
        return java.time.temporal.ChronoUnit.MINUTES.between(detected, end);
    }

    // ── Inner style class ─────────────────────────────────────────────────

    private static class Styles {
        final XSSFCellStyle title, header, label, value, data, alt, currency, percent;

        Styles(XSSFWorkbook wb) {
            XSSFFont boldWhite = wb.createFont();
            boldWhite.setBold(true);
            boldWhite.setColor(IndexedColors.WHITE.getIndex());
            boldWhite.setFontHeightInPoints((short) 12);

            XSSFFont boldDark = wb.createFont();
            boldDark.setBold(true);

            XSSFFont boldLarge = wb.createFont();
            boldLarge.setBold(true);
            boldLarge.setColor(IndexedColors.WHITE.getIndex());
            boldLarge.setFontHeightInPoints((short) 14);

            title = wb.createCellStyle();
            title.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            title.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            title.setFont(boldLarge);
            title.setAlignment(HorizontalAlignment.CENTER);
            title.setVerticalAlignment(VerticalAlignment.CENTER);

            header = wb.createCellStyle();
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setFont(boldWhite);
            header.setBorderBottom(BorderStyle.THIN);

            label = wb.createCellStyle();
            label.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            label.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            label.setFont(boldDark);
            label.setBorderBottom(BorderStyle.THIN);
            label.setBorderRight(BorderStyle.THIN);

            value = wb.createCellStyle();
            value.setAlignment(HorizontalAlignment.RIGHT);
            value.setBorderBottom(BorderStyle.THIN);

            data = wb.createCellStyle();
            data.setBorderBottom(BorderStyle.HAIR);

            alt = wb.createCellStyle();
            alt.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            alt.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            alt.setBorderBottom(BorderStyle.HAIR);

            currency = wb.createCellStyle();
            XSSFDataFormat fmt = wb.createDataFormat();
            currency.setDataFormat(fmt.getFormat("\"$\"#,##0.00"));
            currency.setAlignment(HorizontalAlignment.RIGHT);

            percent = wb.createCellStyle();
            percent.setDataFormat(fmt.getFormat("0.0%"));
            percent.setAlignment(HorizontalAlignment.RIGHT);
        }
    }
}
