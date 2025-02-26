package com.election.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.opencsv.ICSVWriter;
import com.opencsv.CSVWriterBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@Slf4j
public class ElectionExportService {

    public enum ExportFormat {
        EXCEL, CSV
    }

    private static final class I18nLabels {
        // Section headers
        private static final Map<String, Map<String, String>> SECTION_HEADERS = Map.of(
                "en", Map.of(
                        "MAIN_DATA", "Main Data",
                        "GENDER_DIST", "Gender Distribution",
                        "AGE_DIST", "Age Range Distribution",
                        "RESULTS_SUMMARY", "Results Summary",
                        "INSIGHTS", "Insights"
                ),
                "ar", Map.of(
                        "MAIN_DATA", "البيانات الرئيسية",
                        "GENDER_DIST", "توزيع الجنس",
                        "AGE_DIST", "توزيع الفئات العمرية",
                        "RESULTS_SUMMARY", "ملخص النتائج",
                        "INSIGHTS", "رؤى"
                )
        );

        // Field labels
        private static final Map<String, Map<String, String>> FIELD_LABELS = Map.of(
                "en", Map.ofEntries(
                        Map.entry("electionId", "Election Id"),
                        Map.entry("electionName", "Election Name"),
                        Map.entry("electionDescription", "Election Description"),
                        Map.entry("startDate", "Start Date"),
                        Map.entry("endDate", "End Date"),
                        Map.entry("exportType", "Export Type"),
                        Map.entry("creator", "Creator"),
                        Map.entry("voter", "Voter"),
                        Map.entry("candidateName", "Candidate Name"),
                        Map.entry("numberOfVoters", "Number Of Voters"),
                        Map.entry("voters", "Voters"),
                        Map.entry("percentage", "Percentage"),
                        Map.entry("notAvailable", "Not Available"),
                        Map.entry("totalCandidates", "Total Candidates"),
                        Map.entry("allVotersCount", "All Voters Count"),
                        Map.entry("completionRate", "Completion Rate"),
                        Map.entry("submittedVotesCount", "Submitted Votes Count"),
                        Map.entry("category", "Category")
                ),
                "ar", Map.ofEntries(
                        Map.entry("electionId", "معرّف الانتخاب"),
                        Map.entry("electionName", "اسم الانتخاب"),
                        Map.entry("electionDescription", "وصف الانتخاب"),
                        Map.entry("startDate", "تاريخ البدء"),
                        Map.entry("endDate", "تاريخ الانتهاء"),
                        Map.entry("exportType", "نوع التصدير"),
                        Map.entry("creator", "منشئ"),
                        Map.entry("voter", "مشاهد"),
                        Map.entry("candidateName", "اسم المرشح"),
                        Map.entry("numberOfVoters", "عدد المصوتين"),
                        Map.entry("voters", "المصوتين"),
                        Map.entry("percentage", "النسبة المئوية"),
                        Map.entry("notAvailable", "غير متاح للمشاهد"),
                        Map.entry("totalCandidates", "إجمالي المرشحين"),
                        Map.entry("allVotersCount", "عدد المصوتين"),
                        Map.entry("completionRate", "معدل الإكمال"),
                        Map.entry("submittedVotesCount", "عدد الأصوات المقدمة"),
                        Map.entry("category", "الفئة")
                )
        );
    }

    /**
     * Language and translation handler
     */
    private static class I18nHandler {
        private final String language;

        I18nHandler(boolean isArabic) {
            this.language = isArabic ? "ar" : "en";
        }

        public String getSectionHeader(String key) {
            return I18nLabels.SECTION_HEADERS
                    .getOrDefault(language, Collections.emptyMap())
                    .getOrDefault(key, key);
        }

        public String getFieldLabel(String key) {
            return I18nLabels.FIELD_LABELS
                    .getOrDefault(language, Collections.emptyMap())
                    .getOrDefault(key, key);
        }
    }

    /**
     * Base context for export operations
     */
    private static abstract class ExportContext {
        protected final JsonNode jsonData;
        protected final JsonNode dataNode;
        protected final boolean isCreator;
        protected final I18nHandler i18n;

        ExportContext(JsonNode jsonData, boolean isCreator, boolean isArabic) {
            this.jsonData = jsonData;
            this.dataNode = jsonData.path("data");
            this.isCreator = isCreator;
            this.i18n = new I18nHandler(isArabic);
        }

        public String getExportTypeLabel() {
            return isCreator ? i18n.getFieldLabel("creator") : i18n.getFieldLabel("voter");
        }

        public String getFieldValue(String fieldName) {
            JsonNode node = dataNode.path(fieldName);
            return node.isMissingNode() ? "N/A" : node.asText("");
        }

        protected List<String> getHeaderRowColumns() {
            List<String> columns = new ArrayList<>();
            columns.add("electionId");
            columns.add("electionName");
            columns.add("electionDescription");

            if (!dataNode.path("startDate").isMissingNode()) {
                columns.add("startDate");
            }

            if (!dataNode.path("endDate").isMissingNode()) {
                columns.add("endDate");
            }

            columns.add("exportType");
            return columns;
        }

        protected Map<String, Integer> extractDistribution(String distributionKey) {
            JsonNode analytics = dataNode.path("analytics");
            if (analytics.isMissingNode() || !analytics.has(distributionKey)) {
                return Collections.emptyMap();
            }

            Map<String, Integer> distribution = new HashMap<>();
            analytics.path(distributionKey).fields()
                    .forEachRemaining(e -> distribution.put(e.getKey(), e.getValue().asInt()));

            return distribution;
        }
    }

    /**
     * Interface for the export strategy pattern
     */
    private interface ExportStrategy {
        byte[] export(JsonNode data, boolean isCreator, boolean isArabic) throws Exception;
    }

    /**
     * Excel export implementation
     */
    private static class ExcelExportStrategy implements ExportStrategy {
        @Override
        public byte[] export(JsonNode jsonData, boolean isCreator, boolean isArabic) throws Exception {
            try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) { // Better memory handling
                ExcelContext context = new ExcelContext(jsonData, workbook, isCreator, isArabic);
                int currentRow = 0;

                // Main data section
                currentRow = writeMainDataDynamically(context, currentRow);

                // Creator-only sections
                if (isCreator) {
                    currentRow = writeDistribution(context, currentRow, "GENDER_DIST", "candidateGender");
                    currentRow = writeDistribution(context, currentRow, "AGE_DIST", "candidateAgeRange");
                }

                // Results for all users
                currentRow = writeResultsSummary(context, currentRow);

                // Creator-only insights
                if (isCreator) {
                    currentRow = writeInsights(context, currentRow);
                }

                autoSizeColumns(context.sheet);
                return writeWorkbookToBytes(workbook);
            }
        }

        /**
         * Excel-specific context
         */
        private static class ExcelContext extends ExportContext {
            final Sheet sheet;
            final Workbook workbook;
            final CellStyle titleStyle;
            final CellStyle headerStyle;
            final CellStyle dataStyle;

            ExcelContext(JsonNode jsonData, Workbook workbook, boolean isCreator, boolean isArabic) {
                super(jsonData, isCreator, isArabic);

                this.workbook = workbook;
                String sheetName = i18n.getSectionHeader("MAIN_DATA");
                this.sheet = workbook.createSheet(sheetName);

                // Initialize reusable styles
                this.titleStyle = createTitleStyle(workbook);
                this.headerStyle = createHeaderStyle(workbook);
                this.dataStyle = createDataStyle(workbook);
            }
        }

        private int writeMainDataDynamically(ExcelContext context, int rowNum) {
            Row titleRow = context.sheet.createRow(rowNum++);
            createStyledCell(titleRow, 0, context.i18n.getSectionHeader("MAIN_DATA"), context.titleStyle);

            List<String> columns = context.getHeaderRowColumns();

            // Merge title cells
            int lastColIndex = columns.size() - 1;
            context.sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, lastColIndex));

            // Write header row
            Row headerRow = context.sheet.createRow(rowNum++);
            for (int i = 0; i < columns.size(); i++) {
                createStyledCell(headerRow, i, context.i18n.getFieldLabel(columns.get(i)), context.headerStyle);
            }

            // Write data row
            Row dataRow = context.sheet.createRow(rowNum++);
            for (int i = 0; i < columns.size(); i++) {
                String colName = columns.get(i);
                String value = "exportType".equals(colName)
                        ? context.getExportTypeLabel()
                        : context.getFieldValue(colName);
                createStyledCell(dataRow, i, value, context.dataStyle);
            }

            return rowNum + 1; // Add empty row
        }

        private int writeDistribution(ExcelContext context, int rowNum, String sectionKey, String distributionKey) {
            Map<String, Integer> distribution = context.extractDistribution(distributionKey);
            if (distribution.isEmpty()) {
                return rowNum;
            }

            // Title row
            Row titleRow = context.sheet.createRow(rowNum++);
            createStyledCell(titleRow, 0, context.i18n.getSectionHeader(sectionKey), context.titleStyle);
            context.sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 1));

            // Header row
            Row headerRow = context.sheet.createRow(rowNum++);
            createStyledCell(headerRow, 0, context.i18n.getFieldLabel("category"), context.headerStyle);
            createStyledCell(headerRow, 1, context.i18n.getFieldLabel("percentage"), context.headerStyle);

            // Calculate percentages
            int total = distribution.values().stream().mapToInt(Integer::intValue).sum();

            // Write distribution data
            for (Map.Entry<String, Integer> entry : distribution.entrySet()) {
                Row row = context.sheet.createRow(rowNum++);
                createStyledCell(row, 0, entry.getKey(), context.dataStyle);

                double percent = total == 0 ? 0.0 : (entry.getValue() / (double) total) * 100;
                createStyledCell(row, 1, String.format("%.2f%%", percent), context.dataStyle);
            }

            return rowNum + 1; // Add empty row
        }

        private int writeResultsSummary(ExcelContext context, int rowNum) {
            // Title row
            Row titleRow = context.sheet.createRow(rowNum++);
            createStyledCell(titleRow, 0, context.i18n.getSectionHeader("RESULTS_SUMMARY"), context.titleStyle);

            int lastCol = context.isCreator ? 2 : 1;
            context.sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, lastCol));

            // Header row
            Row headerRow = context.sheet.createRow(rowNum++);
            createStyledCell(headerRow, 0, context.i18n.getFieldLabel("candidateName"), context.headerStyle);
            createStyledCell(headerRow, 1, context.i18n.getFieldLabel("numberOfVoters"), context.headerStyle);

            if (context.isCreator) {
                createStyledCell(headerRow, 2, context.i18n.getFieldLabel("voters"), context.headerStyle);
            }

            // Data rows
            JsonNode resultsSummary = context.dataNode.path("resultsSummary");
            if (resultsSummary.isArray()) {
                for (JsonNode candidate : resultsSummary) {
                    Row row = context.sheet.createRow(rowNum++);
                    createStyledCell(row, 0, candidate.path("candidateName").asText("N/A"), context.dataStyle);
                    createStyledCell(row, 1, String.valueOf(candidate.path("numberOfVoters").asInt(0)), context.dataStyle);

                    if (context.isCreator) {
                        String votersList = getVotersListAsString(candidate.path("voters"), context);
                        createStyledCell(row, 2, votersList, context.dataStyle);
                    }
                }
            }

            return rowNum + 1; // Add empty row
        }

        private String getVotersListAsString(JsonNode votersNode, ExcelContext context) {
            if (!votersNode.isArray() || votersNode.isEmpty()) {
                return context.i18n.getFieldLabel("notAvailable");
            }

            return StreamSupport.stream(votersNode.spliterator(), false)
                    .map(JsonNode::asText)
                    .collect(Collectors.joining(", "));
        }

        private int writeInsights(ExcelContext context, int rowNum) {
            JsonNode insights = context.dataNode.path("insights");
            if (insights.isMissingNode()) {
                return rowNum;
            }

            // Title row
            Row titleRow = context.sheet.createRow(rowNum++);
            createStyledCell(titleRow, 0, context.i18n.getSectionHeader("INSIGHTS"), context.titleStyle);
            context.sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 3));

            // Header row
            Row headerRow = context.sheet.createRow(rowNum++);
            createStyledCell(headerRow, 0, context.i18n.getFieldLabel("totalCandidates"), context.headerStyle);
            createStyledCell(headerRow, 1, context.i18n.getFieldLabel("allVotersCount"), context.headerStyle);
            createStyledCell(headerRow, 2, context.i18n.getFieldLabel("completionRate"), context.headerStyle);
            createStyledCell(headerRow, 3, context.i18n.getFieldLabel("submittedVotesCount"), context.headerStyle);

            // Data row
            Row dataRow = context.sheet.createRow(rowNum++);
            createStyledCell(dataRow, 0, String.valueOf(insights.path("totalCandidates").asInt(0)), context.dataStyle);
            createStyledCell(dataRow, 1, String.valueOf(insights.path("allVotersCount").asInt(0)), context.dataStyle);

            double rate = insights.path("completionRate").asDouble(0.0);
            createStyledCell(dataRow, 2, String.format("%.2f%%", rate), context.dataStyle);

            createStyledCell(dataRow, 3, String.valueOf(insights.path("submittedVotesCount").asInt(0)), context.dataStyle);

            return rowNum + 1; // Add empty row
        }

        private static CellStyle createTitleStyle(Workbook wb) {
            CellStyle style = wb.createCellStyle();
            style.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Font font = wb.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            font.setFontHeightInPoints((short) 14);
            style.setFont(font);

            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);

            setThinBorders(style, IndexedColors.GREY_25_PERCENT.getIndex());
            return style;
        }

        private static CellStyle createHeaderStyle(Workbook wb) {
            CellStyle style = wb.createCellStyle();
            style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Font font = wb.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            font.setFontHeightInPoints((short) 14);
            style.setFont(font);

            style.setAlignment(HorizontalAlignment.CENTER);
            style.setVerticalAlignment(VerticalAlignment.CENTER);

            setThinBorders(style, IndexedColors.GREY_25_PERCENT.getIndex());
            return style;
        }

        private static CellStyle createDataStyle(Workbook wb) {
            CellStyle style = wb.createCellStyle();
            style.setFillForegroundColor(IndexedColors.WHITE.getIndex());
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Font font = wb.createFont();
            font.setBold(false);
            font.setColor(IndexedColors.BLACK.getIndex());
            font.setFontHeightInPoints((short) 14);
            style.setFont(font);

            style.setAlignment(HorizontalAlignment.LEFT);
            style.setVerticalAlignment(VerticalAlignment.CENTER);

            setThinBorders(style, IndexedColors.GREY_25_PERCENT.getIndex());
            return style;
        }

        private static void setThinBorders(CellStyle style, short borderColor) {
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderTop(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);

            style.setBottomBorderColor(borderColor);
            style.setTopBorderColor(borderColor);
            style.setLeftBorderColor(borderColor);
            style.setRightBorderColor(borderColor);
        }

        private static Cell createStyledCell(Row row, int col, String value, CellStyle style) {
            Cell cell = row.createCell(col);
            cell.setCellValue(value);
            cell.setCellStyle(style);
            return cell;
        }

        private void autoSizeColumns(Sheet sheet) {
            int maxColumns = 0;
            for (int i = 0; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row != null && row.getPhysicalNumberOfCells() > maxColumns) {
                    maxColumns = row.getPhysicalNumberOfCells();
                }
            }
            if (sheet instanceof org.apache.poi.xssf.streaming.SXSSFSheet) {
                org.apache.poi.xssf.streaming.SXSSFSheet sxSheet = (org.apache.poi.xssf.streaming.SXSSFSheet) sheet;
                for (int i = 0; i < maxColumns; i++) {
                    sxSheet.trackColumnForAutoSizing(i);
                    sheet.autoSizeColumn(i);
                }
            } else {
                for (int i = 0; i < maxColumns; i++) {
                    sheet.autoSizeColumn(i);
                }
            }
        }

        private byte[] writeWorkbookToBytes(Workbook workbook) throws Exception {
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                workbook.write(bos);
                return bos.toByteArray();
            }
        }
    }

    /**
     * CSV export implementation
     */
    private static class CsvExportStrategy implements ExportStrategy {
        @Override
        public byte[] export(JsonNode jsonData, boolean isCreator, boolean isArabic) throws Exception {
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                 OutputStreamWriter streamWriter = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {

                streamWriter.write('\uFEFF');

                ICSVWriter csvWriter = new CSVWriterBuilder(streamWriter)
                        .build();


                CsvContext context = new CsvContext(jsonData, csvWriter, isCreator, isArabic);

                // Export sections
                writeMainDataToCsv(context);

                if (context.isCreator) {
                    writeDistributionToCsv(context, "GENDER_DIST", "candidateGender");
                    writeDistributionToCsv(context, "AGE_DIST", "candidateAgeRange");
                }

                writeResultsSummaryToCsv(context);

                if (context.isCreator) {
                    writeInsightsToCsv(context);
                }

                csvWriter.flush();
                return outputStream.toByteArray();
            }
        }

        private static class CsvContext extends ExportContext {
            final ICSVWriter csvWriter;

            CsvContext(JsonNode jsonData, ICSVWriter csvWriter, boolean isCreator, boolean isArabic) {
                super(jsonData, isCreator, isArabic);
                this.csvWriter = csvWriter;
            }

            void writeEmptyLine() {
                csvWriter.writeNext(new String[]{});
            }

            String[] translateHeaders(List<String> keys) {
                return keys.stream()
                        .map(i18n::getFieldLabel)
                        .toArray(String[]::new);
            }
        }

        private void writeMainDataToCsv(CsvContext context) {
            // Title
            context.csvWriter.writeNext(new String[]{
                    context.i18n.getSectionHeader("MAIN_DATA")
            });

            // Get dynamic columns
            List<String> columns = context.getHeaderRowColumns();

            // Headers
            context.csvWriter.writeNext(context.translateHeaders(columns));

            // Data
            String[] dataRow = new String[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                String colName = columns.get(i);
                dataRow[i] = "exportType".equals(colName)
                        ? context.getExportTypeLabel()
                        : context.getFieldValue(colName);
            }
            context.csvWriter.writeNext(dataRow);
            context.writeEmptyLine();
        }

        private void writeDistributionToCsv(CsvContext context, String sectionKey, String distributionKey) {
            Map<String, Integer> distribution = context.extractDistribution(distributionKey);
            if (distribution.isEmpty()) {
                return;
            }

            // Title
            context.csvWriter.writeNext(new String[]{
                    context.i18n.getSectionHeader(sectionKey)
            });

            // Headers
            context.csvWriter.writeNext(new String[]{
                    context.i18n.getFieldLabel("category"),
                    context.i18n.getFieldLabel("percentage")
            });

            // Calculate total for percentages
            int total = distribution.values().stream().mapToInt(Integer::intValue).sum();

            // Write data rows
            for (Map.Entry<String, Integer> entry : distribution.entrySet()) {
                double percent = total == 0 ? 0.0 : (entry.getValue() / (double) total) * 100;
                context.csvWriter.writeNext(new String[]{
                        entry.getKey(),
                        String.format("%.2f%%", percent)
                });
            }

            context.writeEmptyLine();
        }

        private void writeResultsSummaryToCsv(CsvContext context) {
            // Title
            context.csvWriter.writeNext(new String[]{
                    context.i18n.getSectionHeader("RESULTS_SUMMARY")
            });

            // Headers
            String[] headers = context.isCreator
                    ? new String[]{
                    context.i18n.getFieldLabel("candidateName"),
                    context.i18n.getFieldLabel("numberOfVoters"),
                    context.i18n.getFieldLabel("voters")
            }
                    : new String[]{
                    context.i18n.getFieldLabel("candidateName"),
                    context.i18n.getFieldLabel("numberOfVoters")
            };
            context.csvWriter.writeNext(headers);

            // Data rows
            JsonNode resultsSummary = context.dataNode.path("resultsSummary");
            if (resultsSummary.isArray()) {
                for (JsonNode candidate : resultsSummary) {
                    String candidateName = candidate.path("candidateName").asText("N/A");
                    String votersCount = String.valueOf(candidate.path("numberOfVoters").asInt(0));

                    if (context.isCreator) {
                        String votersList = getVotersListForCsv(candidate.path("voters"), context);
                        context.csvWriter.writeNext(new String[]{candidateName, votersCount, votersList});
                    } else {
                        context.csvWriter.writeNext(new String[]{candidateName, votersCount});
                    }
                }
            }

            context.writeEmptyLine();
        }

        private String getVotersListForCsv(JsonNode votersNode, CsvContext context) {
            if (!votersNode.isArray() || votersNode.isEmpty()) {
                return context.i18n.getFieldLabel("notAvailable");
            }

            return StreamSupport.stream(votersNode.spliterator(), false)
                    .map(JsonNode::asText)
                    .collect(Collectors.joining(", "));
        }

        private void writeInsightsToCsv(CsvContext context) {
            JsonNode insights = context.dataNode.path("insights");
            if (insights.isMissingNode()) {
                return;
            }

            // Title
            context.csvWriter.writeNext(new String[]{
                    context.i18n.getSectionHeader("INSIGHTS")
            });

            // Headers
            context.csvWriter.writeNext(new String[]{
                    context.i18n.getFieldLabel("totalCandidates"),
                    context.i18n.getFieldLabel("allVotersCount"),
                    context.i18n.getFieldLabel("completionRate"),
                    context.i18n.getFieldLabel("submittedVotesCount")
            });

            // Data row
            double rate = insights.path("completionRate").asDouble(0.0);
            context.csvWriter.writeNext(new String[]{
                    String.valueOf(insights.path("totalCandidates").asInt(0)),
                    String.valueOf(insights.path("allVotersCount").asInt(0)),
                    String.format("%.1f%%", rate),
                    String.valueOf(insights.path("submittedVotesCount").asInt(0))
            });

            context.writeEmptyLine();
        }
    }

    /**
     * Factory method to get the appropriate export strategy
     */
    private ExportStrategy getExportStrategy(ExportFormat format) {
        return switch (format) {
            case EXCEL -> new ExcelExportStrategy();
            case CSV -> new CsvExportStrategy();
        };
    }

    /**
     * Public export method that can be called by clients
     * @param jsonData The election data in JSON format
     * @param format The desired export format (EXCEL or CSV)
     * @param isCreator Whether the request is from a creator or a voter
     * @param isArabic Whether to use Arabic labels
     * @return The exported data as a byte array
     * @throws Exception If an error occurs during export
     */
    public byte[] exportData(JsonNode jsonData, ExportFormat format, boolean isCreator, boolean isArabic) throws Exception {
        return getExportStrategy(format).export(jsonData, isCreator, isArabic);
    }

    /**
     * Backwards compatibility method for Excel export
     */
    public byte[] exportToExcel(JsonNode jsonData, boolean isCreator, boolean isArabic) throws Exception {
        return exportData(jsonData, ExportFormat.EXCEL, isCreator, isArabic);
    }

    /**
     * Backwards compatibility method for CSV export
     */
    public byte[] exportToCsv(JsonNode jsonData, boolean isCreator, boolean isArabic) throws Exception {
        return exportData(jsonData, ExportFormat.CSV, isCreator, isArabic);
    }

    public ResponseEntity<byte[]> export(JsonNode data, String dataType, boolean isExcel, String lang) {
        log.info("Starting export: type={}, format={}, language={}", dataType, isExcel ? "excel" : "csv", lang);
        try {
            boolean isArabic = lang.equalsIgnoreCase("ar");
            String electionName = "";
            if (data.has("data") && data.get("data").has("electionName")) {
                electionName = data.get("data").get("electionName").asText("unnamed_election");
            }
            String safeElectionName = electionName.replaceAll("[^a-zA-Z0-9\\u0600-\\u06FF_.-]", "_");
            byte[] fileData = generateExportData(data, dataType, isExcel, isArabic);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String extension = isExcel ? ".xlsx" : ".csv";
            String fileName = dataType + "_" + safeElectionName + "_" + timestamp + extension;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentDisposition(org.springframework.http.ContentDisposition
                    .builder("attachment")
                    .filename(fileName)
                    .build());
            MediaType mediaType = isExcel ? MediaType.APPLICATION_OCTET_STREAM : MediaType.TEXT_PLAIN;
            log.info("Export completed successfully: fileName={}", fileName);
            return ResponseEntity.ok()
                    .headers(headers)
                    .contentType(mediaType)
                    .body(fileData);
        } catch (Exception e) {
            log.error("Export failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(("حدث خطأ أثناء التصدير: " + e.getMessage()).getBytes());
        }
    }

    private byte[] generateExportData(JsonNode data, String dataType, boolean isExcel, boolean isArabic) throws Exception {
        boolean isCreator = dataType.equals("creator");
        if (isExcel) {
            return this.exportToExcel(data, isCreator, isArabic);
        } else {
            return this.exportToCsv(data, isCreator, isArabic);
        }
    }
}
