package com.example.consensus.analytics.excel;

import com.example.consensus.model.Enums.BreakStatus;
import com.example.consensus.model.Enums.DataSource;
import com.example.consensus.model.entity.Trade;
import com.example.consensus.model.entity.TradeBreak;
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
public class SettlementRiskBuilder {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final double CSDR_DAILY_RATE = 0.0001; // 1 bp per day (equities)

    public XSSFWorkbook build(List<TradeBreak> breaks, List<Trade> trades) {
        XSSFWorkbook wb = new XSSFWorkbook();

        // Only open breaks matter for settlement risk
        List<TradeBreak> openBreaks = breaks.stream()
                .filter(b -> BreakStatus.OPEN.equals(b.getStatus()))
                .collect(Collectors.toList());

        // BLOTTER trades indexed by tradeId for notional calculation
        Map<String, Trade> blotterByTradeId = trades.stream()
                .filter(t -> DataSource.BLOTTER.equals(t.getSource()))
                .collect(Collectors.toMap(Trade::getTradeId, t -> t, (a, b) -> a));

        Styles s = new Styles(wb);
        int dataRows = buildPositions(wb, openBreaks, blotterByTradeId, s);
        buildSummary(wb, dataRows, s);

        wb.setActiveSheet(1);
        return wb;
    }

    private int buildPositions(XSSFWorkbook wb, List<TradeBreak> openBreaks,
                                Map<String, Trade> blotter, Styles s) {
        XSSFSheet sheet = wb.createSheet("POSITIONS");
        sheet.createFreezePane(0, 1);

        // Columns: A=BreakID B=TradeID C=Symbol D=Counterparty E=Currency
        //          F=Notional G=CSDR_Daily_Penalty H=At_Risk I=Break_Type
        //          J=Materiality K=BPS_Dev L=Settlement_Date M=Est_Res_Mins N=Mins_To_Settlement
        String[] headers = {
            "Break ID", "Trade ID", "Symbol", "Counterparty", "Currency",
            "Notional", "CSDR Daily Penalty", "At Risk",
            "Break Type", "Materiality", "BPS Deviation",
            "Settlement Date", "Est. Resolution (mins)", "Mins to Settlement"
        };

        XSSFRow hRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            XSSFCell c = hRow.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(s.header);
        }

        int rowIdx = 1;
        for (TradeBreak b : openBreaks) {
            XSSFRow row = sheet.createRow(rowIdx);
            XSSFCellStyle base = (rowIdx % 2 == 0) ? s.alt : s.data;

            Trade t = blotter.get(b.getTradeId());
            double notional = b.getNotionalImpact() != null
                    ? b.getNotionalImpact().doubleValue()
                    : (t != null ? t.getQuantity().multiply(t.getPrice()).doubleValue() : 0);

            cell(row, 0, b.getId(), base);
            cell(row, 1, b.getTradeId(), base);
            cell(row, 2, t != null ? t.getSymbol() : "", base);
            cell(row, 3, t != null ? t.getCounterparty() : "", base);
            cell(row, 4, t != null ? t.getCurrency() : "", base);
            cell(row, 5, notional, s.currency);

            // G: CSDR Daily Penalty = F * 0.0001 (formula)
            int excelRow = rowIdx + 1;
            XSSFCell csdrCell = row.createCell(6);
            csdrCell.setCellFormula("F" + excelRow + "*" + CSDR_DAILY_RATE);
            csdrCell.setCellStyle(s.currency);

            // H: At Risk flag = IF est_res > mins_to_settlement → AT RISK
            Long estRes = b.getEstimatedResolutionMinutes();
            Long minsLeft = b.getMinutesToSettlement();
            String atRisk = (estRes != null && minsLeft != null && minsLeft > 0 && minsLeft < estRes)
                    ? "AT RISK" : "SAFE";
            cell(row, 7, atRisk, atRisk.equals("AT RISK") ? s.redText : s.greenText);

            cell(row, 8, b.getBreakType() != null ? b.getBreakType().name() : "", base);
            cell(row, 9, b.getMaterialityTier() != null ? b.getMaterialityTier().name() : "", base);
            cell(row, 10, b.getBpsDeviation() != null ? b.getBpsDeviation().doubleValue() : 0, base);
            cell(row, 11, t != null && t.getSettlementDate() != null ? t.getSettlementDate().format(FMT) : "", base);
            cell(row, 12, estRes != null ? estRes : 0L, base);
            cell(row, 13, minsLeft != null ? minsLeft : 0L, base);
            rowIdx++;
        }

        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));
        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
        return rowIdx - 1; // number of data rows
    }

    private void buildSummary(XSSFWorkbook wb, int dataRows, Styles s) {
        XSSFSheet sheet = wb.createSheet("SUMMARY");
        String last = String.valueOf(dataRows + 1); // last data row in Excel (1-indexed)

        XSSFRow title = sheet.createRow(0);
        XSSFCell titleCell = title.createCell(0);
        titleCell.setCellValue("SETTLEMENT RISK EXPOSURE — " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        titleCell.setCellStyle(s.title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 2));

        XSSFRow hRow = sheet.createRow(2);
        cell(hRow, 0, "Metric", s.header);
        cell(hRow, 1, "Value", s.header);

        // Formulas use exact row ranges from POSITIONS to avoid summing empty rows
        String notionalRange  = "POSITIONS!$F$2:$F$" + last;
        String csdrRange      = "POSITIONS!$G$2:$G$" + last;
        String atRiskRange    = "POSITIONS!$H$2:$H$" + last;
        String materialityRange = "POSITIONS!$J$2:$J$" + last;

        Object[][] metrics = {
            {"Total Open Breaks",          "COUNTA(POSITIONS!$A$2:$A$" + last + ")",          s.value},
            {"Total Open Notional",        "SUM(" + notionalRange + ")",                        s.currency},
            {"CSDR Daily Penalty Est.",    "SUM(" + csdrRange + ")",                            s.currency},
            {"At Risk Count",              "COUNTIF(" + atRiskRange + ",\"AT RISK\")",           s.value},
            {"At Risk Notional",           "SUMIF(" + atRiskRange + ",\"AT RISK\"," + notionalRange + ")", s.currency},
            {"CRITICAL Count",             "COUNTIF(" + materialityRange + ",\"CRITICAL\")",    s.value},
            {"CRITICAL Notional",          "SUMIF(" + materialityRange + ",\"CRITICAL\"," + notionalRange + ")", s.currency},
            {"MAJOR Count",                "COUNTIF(" + materialityRange + ",\"MAJOR\")",       s.value},
            {"MAJOR Notional",             "SUMIF(" + materialityRange + ",\"MAJOR\"," + notionalRange + ")", s.currency},
        };

        int startRow = 3;
        for (int i = 0; i < metrics.length; i++) {
            XSSFRow row = sheet.createRow(startRow + i);
            cell(row, 0, (String) metrics[i][0], s.label);
            XSSFCell valCell = row.createCell(1);
            valCell.setCellFormula((String) metrics[i][1]);
            valCell.setCellStyle((XSSFCellStyle) metrics[i][2]);
        }

        // Conditional formatting
        SheetConditionalFormatting scf = sheet.getSheetConditionalFormatting();

        // At Risk Count (B6, startRow+3=row 6 = Excel B7): red if >0, green if =0
        applyZeroGreenNonzeroRed(scf, "B" + (startRow + 3 + 1));

        // CRITICAL Count (B8): red if >0
        applyZeroGreenNonzeroRed(scf, "B" + (startRow + 5 + 1));

        sheet.setColumnWidth(0, 9000);
        sheet.setColumnWidth(1, 6000);
    }

    private void applyZeroGreenNonzeroRed(SheetConditionalFormatting scf, String cellAddr) {
        CellRangeAddress[] range = {CellRangeAddress.valueOf(cellAddr)};

        ConditionalFormattingRule green = scf.createConditionalFormattingRule(ComparisonOperator.EQUAL, "0");
        green.createPatternFormatting().setFillBackgroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        green.createPatternFormatting().setFillPattern(PatternFormatting.SOLID_FOREGROUND);

        ConditionalFormattingRule red = scf.createConditionalFormattingRule(ComparisonOperator.GT, "0");
        red.createPatternFormatting().setFillBackgroundColor(IndexedColors.ROSE.getIndex());
        red.createPatternFormatting().setFillPattern(PatternFormatting.SOLID_FOREGROUND);

        scf.addConditionalFormatting(range, new ConditionalFormattingRule[]{green, red});
    }

    private void cell(XSSFRow row, int col, Object value, XSSFCellStyle style) {
        XSSFCell c = row.createCell(col);
        if (value instanceof String v)       c.setCellValue(v);
        else if (value instanceof Number v)  c.setCellValue(v.doubleValue());
        else if (value instanceof Boolean v) c.setCellValue(v);
        else if (value != null)              c.setCellValue(value.toString());
        if (style != null) c.setCellStyle(style);
    }

    private static class Styles {
        final XSSFCellStyle title, header, label, value, data, alt, currency, percent, redText, greenText;

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

            XSSFFont redFont = wb.createFont();
            redFont.setBold(true);
            redFont.setColor(IndexedColors.RED.getIndex());

            XSSFFont greenFont = wb.createFont();
            greenFont.setBold(true);
            greenFont.setColor(IndexedColors.GREEN.getIndex());

            XSSFDataFormat fmt = wb.createDataFormat();

            title = wb.createCellStyle();
            title.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            title.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            title.setFont(boldLarge);
            title.setAlignment(HorizontalAlignment.CENTER);

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
            currency.setDataFormat(fmt.getFormat("\"$\"#,##0.00"));
            currency.setAlignment(HorizontalAlignment.RIGHT);

            percent = wb.createCellStyle();
            percent.setDataFormat(fmt.getFormat("0.0%"));

            redText = wb.createCellStyle();
            redText.setFont(redFont);
            redText.setAlignment(HorizontalAlignment.CENTER);

            greenText = wb.createCellStyle();
            greenText.setFont(greenFont);
            greenText.setAlignment(HorizontalAlignment.CENTER);
        }
    }
}
