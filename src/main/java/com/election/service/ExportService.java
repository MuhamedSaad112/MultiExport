package com.election.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.opencsv.CSVWriter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

@Service
public class ExportService {

    private static final class ExportConstants {
        static final String UTF8_BOM = "\uFEFF";

        static final class Arabic {
            static final String MAIN_DATA_TITLE = "البيانات الرئيسية";
            static final String CREATOR_SHEET_NAME = "بيانات الاستبيان - منشئ";
            static final String VIEWER_SHEET_NAME = "بيانات الاستبيان - مشاهد";
            static final String UNKNOWN_QUESTION_TYPE = "نوع السؤال غير معروف";
            static final Map<String, String> FIELD_LABELS = createArabicFieldLabels();
            static final String[] QUESTION_HEADERS_WITH_VOTER = new String[]{
                    "رقم السؤال", "العنوان", "النوع", "اسم الإجابة", "نسبة الإجابة", "اسم المصوت"};
            static final String[] QUESTION_HEADERS_WITHOUT_VOTER = new String[]{
                    "رقم السؤال", "العنوان", "النوع", "اسم الإجابة", "نسبة الإجابة"};

            private static Map<String, String> createArabicFieldLabels() {
                Map<String, String> labels = new LinkedHashMap<>();
                labels.put("voteTitle", "عنوان الاستطلاع");
                labels.put("creator", "المنشئ");
                labels.put("loggedInUser", "المستخدم المسجل");
                labels.put("votingStatus", "حالة التصويت");
                labels.put("type", "النوع");
                labels.put("description", "الوصف");
                labels.put("allowShare", "السماح بالمشاركة");
                labels.put("startDate", "تاريخ البدء");
                labels.put("startTime", "وقت البدء");
                labels.put("endDate", "تاريخ الانتهاء");
                labels.put("endTime", "وقت الانتهاء");
                labels.put("totalParticipants", "إجمالي المشاركين");
                labels.put("submittedVotes", "الأصوات المقدمة");
                labels.put("pendingVotes", "الأصوات المعلقة");
                labels.put("views", "عدد المشاهدات");
                labels.put("completionRate", "معدل الإكمال");
                labels.put("questionResultCount", "عدد نتائج الأسئلة");
                return Collections.unmodifiableMap(labels);
            }
        }

        static final class English {
            static final String MAIN_DATA_TITLE = "Main Data";
            static final String CREATOR_SHEET_NAME = "Survey Data - Creator";
            static final String VIEWER_SHEET_NAME = "Survey Data - Viewer";
            static final String UNKNOWN_QUESTION_TYPE = "Unknown Question Type";
            static final Map<String, String> FIELD_LABELS = createEnglishFieldLabels();
            static final String[] QUESTION_HEADERS_WITH_VOTER = new String[]{
                    "Question Number", "Title", "Type", "Answer Name", "Answer Percentage", "Voter Name"};
            static final String[] QUESTION_HEADERS_WITHOUT_VOTER = new String[]{
                    "Question Number", "Title", "Type", "Answer Name", "Answer Percentage"};

            private static Map<String, String> createEnglishFieldLabels() {
                Map<String, String> labels = new LinkedHashMap<>();
                labels.put("voteTitle", "voteTitle");
                labels.put("creator", "creator");
                labels.put("loggedInUser", "loggedInUser");
                labels.put("votingStatus", "votingStatus");
                labels.put("type", "type");
                labels.put("description", "description");
                labels.put("allowShare", "allowShare");
                labels.put("startDate", "startDate");
                labels.put("startTime", "startTime");
                labels.put("endDate", "endDate");
                labels.put("endTime", "endTime");
                labels.put("totalParticipants", "totalParticipants");
                labels.put("submittedVotes", "submittedVotes");
                labels.put("pendingVotes", "pendingVotes");
                labels.put("views", "views");
                labels.put("completionRate", "completionRate");
                labels.put("questionResultCount", "questionResultCount");
                return Collections.unmodifiableMap(labels);
            }
        }
    }

    private enum QuestionType {
        TEXT_SINGLE_LINE, TEXT_MULTI_LINE, TEXT_URL, TEXT_NUMBER, TEXT_DATE, TEXT_DATETIME,
        RANKING, MULTI_SELECTION, MULTI_CHOICE, RATING_RANGE, RATING_STARS,
        UNKNOWN;

        static QuestionType fromString(String type) {
            try {
                return QuestionType.valueOf(type);
            } catch (IllegalArgumentException e) {
                return UNKNOWN;
            }
        }

        boolean isTextType() {
            return this == TEXT_SINGLE_LINE || this == TEXT_MULTI_LINE ||
                    this == TEXT_URL || this == TEXT_NUMBER ||
                    this == TEXT_DATE || this == TEXT_DATETIME;
        }

        boolean isMultiAnswerType() {
            return this == RANKING || this == MULTI_SELECTION || this == MULTI_CHOICE ||
                    this == RATING_RANGE || this == RATING_STARS;
        }
    }

    public byte[] exportCreatorExcel(JsonNode jsonData, boolean isArabic) throws Exception {
        return generateExcel(jsonData, true, isArabic);
    }

    public byte[] exportViewerExcel(JsonNode jsonData, boolean isArabic) throws Exception {
        return generateExcel(jsonData, false, isArabic);
    }

    public byte[] exportCreatorCsv(JsonNode jsonData, boolean isArabic) throws Exception {
        return generateCsvWithUtf8(jsonData, true, isArabic);
    }

    public byte[] exportViewerCsv(JsonNode jsonData, boolean isArabic) throws Exception {
        return generateCsvWithUtf8(jsonData, false, isArabic);
    }

    private byte[] generateExcel(JsonNode jsonData, boolean isCreator, boolean isArabic) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            String sheetName = getSheetName(isCreator, isArabic);
            Sheet sheet = workbook.createSheet(sheetName);
            int currentRow = 0;
            if (isCreator) {
                currentRow = writeMainDataHorizontal(jsonData, sheet, isArabic);
            }
            writeQuestionResultsWithRespondents(jsonData, sheet, currentRow, isCreator, isArabic);
            autoSizeColumns(sheet);
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                workbook.write(outputStream);
                return outputStream.toByteArray();
            }
        }
    }

    private String getSheetName(boolean isCreator, boolean isArabic) {
        if (isArabic) {
            return isCreator ? ExportConstants.Arabic.CREATOR_SHEET_NAME : ExportConstants.Arabic.VIEWER_SHEET_NAME;
        } else {
            return isCreator ? ExportConstants.English.CREATOR_SHEET_NAME : ExportConstants.English.VIEWER_SHEET_NAME;
        }
    }

    private byte[] generateCsvWithUtf8(JsonNode jsonData, boolean includeVoterName, boolean isArabic) throws Exception {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             OutputStreamWriter streamWriter = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
             CSVWriter csvWriter = new CSVWriter(streamWriter)) {
            streamWriter.write(ExportConstants.UTF8_BOM);
            writeQuestionResultsHeaderToCsv(csvWriter, includeVoterName, isArabic);
            writeQuestionResultsWithRespondentsToCsv(jsonData, csvWriter, includeVoterName, isArabic);
            csvWriter.flush();
            return outputStream.toByteArray();
        }
    }

    private void autoSizeColumns(Sheet sheet) {
        int maxColumns = 0;
        for (Row row : sheet) {
            maxColumns = Math.max(maxColumns, row.getPhysicalNumberOfCells());
        }
        for (int i = 0; i < maxColumns; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private int writeMainDataHorizontal(JsonNode jsonData, Sheet sheet, boolean isArabic) {
        int rowNum = 0;
        JsonNode mainData = jsonData.path("data");
        String mainDataTitle = isArabic ? ExportConstants.Arabic.MAIN_DATA_TITLE : ExportConstants.English.MAIN_DATA_TITLE;
        Row titleRow = sheet.createRow(rowNum++);
        CellStyle titleStyle = createHeaderStyle(sheet);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue(mainDataTitle);
        titleCell.setCellStyle(titleStyle);
        Row headerRow = sheet.createRow(rowNum++);
        Row valueRow = sheet.createRow(rowNum++);
        Map<String, String> fieldLabels = isArabic ? ExportConstants.Arabic.FIELD_LABELS : ExportConstants.English.FIELD_LABELS;
        CellStyle headerStyle = createHeaderStyle(sheet);
        int colIndex = 0;
        for (Map.Entry<String, String> field : fieldLabels.entrySet()) {
            String key = field.getKey();
            String label = field.getValue();
            if ((key.equals("endDate") && !mainData.has("endDate")) ||
                    (key.equals("endTime") && !mainData.has("endTime"))) {
                continue;
            }
            Cell headerCell = headerRow.createCell(colIndex);
            headerCell.setCellValue(label);
            headerCell.setCellStyle(headerStyle);
            String valueStr = mainData.path(key).asText("");
            valueRow.createCell(colIndex).setCellValue(valueStr);
            colIndex++;
        }
        return rowNum;
    }

    private void writeQuestionResultsWithRespondents(JsonNode jsonData, Sheet sheet, int rowNum,
                                                     boolean includeVoterName, boolean isArabic) {
        JsonNode questionResults = jsonData.path("data").path("questionResults");
        if (!questionResults.isArray()) {
            return;
        }
        String[] headers = getQuestionHeaders(includeVoterName, isArabic);
        Row headerRow = sheet.createRow(rowNum++);
        CellStyle headerStyle = createHeaderStyle(sheet);
        for (int col = 0; col < headers.length; col++) {
            Cell cell = headerRow.createCell(col);
            cell.setCellValue(headers[col]);
            cell.setCellStyle(headerStyle);
        }
        for (JsonNode question : questionResults) {
            String questionNumber = question.path("questionNumber").asText("");
            String title = question.path("title").asText("");
            String typeStr = question.path("type").asText("");
            QuestionType type = QuestionType.fromString(typeStr);
            if (type.isTextType()) {
                writeTextQuestionRow(sheet, rowNum++, questionNumber, title, typeStr, question, includeVoterName);
            } else if (type.isMultiAnswerType()) {
                rowNum = writeMultiAnswerQuestionRows(sheet, rowNum, questionNumber, title, typeStr, question, includeVoterName);
            } else {
                writeUnknownQuestionTypeRow(sheet, rowNum++, questionNumber, title, typeStr, includeVoterName, isArabic);
            }
        }
    }

    private void writeTextQuestionRow(Sheet sheet, int rowNum, String questionNumber, String title,
                                      String type, JsonNode question, boolean includeVoterName) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(questionNumber);
        row.createCell(1).setCellValue(title);
        row.createCell(2).setCellValue(type);
        row.createCell(3).setCellValue(question.path("singleAnswer").asText(""));
        row.createCell(4).setCellValue("");
        if (includeVoterName) {
            row.createCell(5).setCellValue(question.path("voterName").asText(""));
        }
    }

    private int writeMultiAnswerQuestionRows(Sheet sheet, int rowNum, String questionNumber,
                                             String title, String type, JsonNode question,
                                             boolean includeVoterName) {
        JsonNode answers = question.path("answers");
        if (!answers.isArray() || answers.size() == 0) {
            Row emptyRow = sheet.createRow(rowNum++);
            emptyRow.createCell(0).setCellValue(questionNumber);
            emptyRow.createCell(1).setCellValue(title);
            emptyRow.createCell(2).setCellValue(type);
            emptyRow.createCell(3).setCellValue("");
            emptyRow.createCell(4).setCellValue("");
            if (includeVoterName) {
                emptyRow.createCell(5).setCellValue("");
            }
            return rowNum;
        }
        for (JsonNode answer : answers) {
            Row answerRow = sheet.createRow(rowNum++);
            answerRow.createCell(0).setCellValue(questionNumber);
            answerRow.createCell(1).setCellValue(title);
            answerRow.createCell(2).setCellValue(type);
            answerRow.createCell(3).setCellValue(answer.path("name").asText(""));
            double percentage = answer.path("answerPercentage").asDouble();
            answerRow.createCell(4).setCellValue(percentage + "%");
            if (includeVoterName) {
                answerRow.createCell(5).setCellValue(answer.path("voterName").asText(""));
            }
        }
        return rowNum;
    }

    private void writeUnknownQuestionTypeRow(Sheet sheet, int rowNum, String questionNumber, String title,
                                             String type, boolean includeVoterName, boolean isArabic) {
        Row unknownRow = sheet.createRow(rowNum);
        unknownRow.createCell(0).setCellValue(questionNumber);
        unknownRow.createCell(1).setCellValue(title);
        unknownRow.createCell(2).setCellValue(type);
        String unknownText = isArabic ? ExportConstants.Arabic.UNKNOWN_QUESTION_TYPE : ExportConstants.English.UNKNOWN_QUESTION_TYPE;
        unknownRow.createCell(3).setCellValue(unknownText);
        unknownRow.createCell(4).setCellValue("");
        if (includeVoterName) {
            unknownRow.createCell(5).setCellValue("");
        }
    }

    private CellStyle createHeaderStyle(Sheet sheet) {
        CellStyle style = sheet.getWorkbook().createCellStyle();
        Font font = sheet.getWorkbook().createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private String[] getQuestionHeaders(boolean includeVoterName, boolean isArabic) {
        if (isArabic) {
            return includeVoterName ? ExportConstants.Arabic.QUESTION_HEADERS_WITH_VOTER : ExportConstants.Arabic.QUESTION_HEADERS_WITHOUT_VOTER;
        } else {
            return includeVoterName ? ExportConstants.English.QUESTION_HEADERS_WITH_VOTER : ExportConstants.English.QUESTION_HEADERS_WITHOUT_VOTER;
        }
    }

    private void writeQuestionResultsHeaderToCsv(CSVWriter csvWriter, boolean includeVoterName, boolean isArabic) {
        String[] headers = getQuestionHeaders(includeVoterName, isArabic);
        csvWriter.writeNext(headers);
    }

    private void writeQuestionResultsWithRespondentsToCsv(JsonNode jsonData, CSVWriter csvWriter,
                                                          boolean includeVoterName, boolean isArabic) {
        JsonNode questionResults = jsonData.path("data").path("questionResults");
        if (!questionResults.isArray()) {
            return;
        }
        for (JsonNode question : questionResults) {
            String questionNumber = question.path("questionNumber").asText("");
            String title = question.path("title").asText("");
            String typeStr = question.path("type").asText("");
            QuestionType type = QuestionType.fromString(typeStr);
            if (type.isTextType()) {
                writeTextQuestionToCsv(csvWriter, questionNumber, title, typeStr, question, includeVoterName);
            } else if (type.isMultiAnswerType()) {
                writeMultiAnswerQuestionToCsv(csvWriter, questionNumber, title, typeStr, question, includeVoterName);
            } else {
                writeUnknownQuestionTypeToCsv(csvWriter, questionNumber, title, typeStr, includeVoterName, isArabic);
            }
        }
    }

    private void writeTextQuestionToCsv(CSVWriter csvWriter, String questionNumber, String title,
                                        String type, JsonNode question, boolean includeVoterName) {
        String[] row;
        if (includeVoterName) {
            row = new String[]{
                    questionNumber,
                    title,
                    type,
                    question.path("singleAnswer").asText(""),
                    "",
                    question.path("voterName").asText("")
            };
        } else {
            row = new String[]{
                    questionNumber,
                    title,
                    type,
                    question.path("singleAnswer").asText(""),
                    ""
            };
        }
        csvWriter.writeNext(row);
    }

    private void writeMultiAnswerQuestionToCsv(CSVWriter csvWriter, String questionNumber, String title,
                                               String type, JsonNode question, boolean includeVoterName) {
        JsonNode answers = question.path("answers");
        if (!answers.isArray() || answers.size() == 0) {
            String[] emptyRow;
            if (includeVoterName) {
                emptyRow = new String[]{questionNumber, title, type, "", "", ""};
            } else {
                emptyRow = new String[]{questionNumber, title, type, "", ""};
            }
            csvWriter.writeNext(emptyRow);
            return;
        }
        for (JsonNode answer : answers) {
            String[] row;
            if (includeVoterName) {
                row = new String[]{
                        questionNumber,
                        title,
                        type,
                        answer.path("name").asText(""),
                        answer.path("answerPercentage").asDouble() + "%",
                        answer.path("voterName").asText("")
                };
            } else {
                row = new String[]{
                        questionNumber,
                        title,
                        type,
                        answer.path("name").asText(""),
                        answer.path("answerPercentage").asDouble() + "%"
                };
            }
            csvWriter.writeNext(row);
        }
    }

    private void writeUnknownQuestionTypeToCsv(CSVWriter csvWriter, String questionNumber, String title,
                                               String type, boolean includeVoterName, boolean isArabic) {
        String unknownText = isArabic ? ExportConstants.Arabic.UNKNOWN_QUESTION_TYPE : ExportConstants.English.UNKNOWN_QUESTION_TYPE;
        String[] row;
        if (includeVoterName) {
            row = new String[]{questionNumber, title, type, unknownText, "", ""};
        } else {
            row = new String[]{questionNumber, title, type, unknownText, ""};
        }
        csvWriter.writeNext(row);
    }


    public ResponseEntity<byte[]> export(JsonNode data, String dataType, boolean isExcel, String lang) {
        try {
            boolean isArabic = lang.equalsIgnoreCase("ar");

            byte[] fileData;
            if (isExcel) {
                fileData = dataType.equalsIgnoreCase("creator")
                        ? this.exportCreatorExcel(data, isArabic)
                        : this.exportViewerExcel(data, isArabic);
            } else {
                fileData = dataType.equalsIgnoreCase("creator")
                        ? this.exportCreatorCsv(data, isArabic)
                        : this.exportViewerCsv(data, isArabic);
            }

            String voteTitle = data.path("data").path("voteTitle").asText("export").replaceAll("\\s+", "_");
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String extension = isExcel ? ".xlsx" : ".csv";
            String fileName = dataType + "_" + voteTitle + "_" + timestamp + extension;

            MediaType contentType = isExcel ? MediaType.APPLICATION_OCTET_STREAM : MediaType.valueOf("text/csv");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                    .contentType(contentType)
                    .body(fileData);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
