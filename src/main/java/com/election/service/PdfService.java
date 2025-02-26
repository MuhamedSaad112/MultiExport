package com.election.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.*;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.layout.property.UnitValue;
import lombok.extern.slf4j.Slf4j;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

@Slf4j
@Service
public class PdfService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final float HEADER_HEIGHT = 80f;

    private static final Set<String> CHARTABLE_TYPES = Set.of("RATING_STARS", "RATING_RANGE", "RANKING");
    private static final DeviceRgb HEADER_COLOR = new DeviceRgb(79, 29, 123);
    private static final DeviceRgb SECTION_COLOR = new DeviceRgb(44, 62, 80);
    private static final Color BAR_COLOR = new Color(0xa5, 0x4f, 0xe0);

    private static final float CHART_WIDTH = 600f;
    private static final float CHART_HEIGHT = 350f;

    public ByteArrayResource generateDemandCommitteePdf(String jsonString) {
        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(jsonString);
            JsonNode dataNode = rootNode.path("data");

            PdfMetadata metadata = extractMetadata(dataNode);
            Map<String, List<JsonNode>> questionsByType = groupQuestionsByType(dataNode.path("questionResults"));

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 PdfWriter writer = new PdfWriter(baos);
                 PdfDocument pdfDoc = new PdfDocument(writer);
                 Document document = new Document(pdfDoc)) {

                generatePdfContent(document, pdfDoc, metadata, questionsByType);
                document.close();
                return new ByteArrayResource(baos.toByteArray());
            }
        } catch (Exception e) {
            log.error("Error generating PDF: ", e);
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }

    private record PdfMetadata(
            String voteTitle,
            String outputFile,
            String startDate,
            String endDate,
            String startTime,
            String endTime
    ) {}

    private PdfMetadata extractMetadata(JsonNode dataNode) {
        String voteTitle = dataNode.path("voteTitle").asText("Vote");
        String nowStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String outputFile = voteTitle + "_report_" + nowStr + ".pdf";

        return new PdfMetadata(
                voteTitle,
                outputFile,
                dataNode.path("startDate").asText(""),
                dataNode.path("endDate").asText(""),
                dataNode.path("startTime").asText(""),
                dataNode.path("endTime").asText("")
        );
    }

    private Map<String, List<JsonNode>> groupQuestionsByType(JsonNode questionResults) {
        Map<String, List<JsonNode>> questionsByType = new LinkedHashMap<>();
        if (questionResults.isArray()) {
            for (JsonNode question : questionResults) {
                String type = question.path("type").asText("");
                questionsByType.computeIfAbsent(type, k -> new ArrayList<>()).add(question);
            }
        }
        return questionsByType;
    }

    private void generatePdfContent(Document document, PdfDocument pdfDoc,
                                    PdfMetadata metadata, Map<String, List<JsonNode>> questionsByType) throws Exception {
        String headerTitle = metadata.voteTitle() + " Results";
        drawHeader(pdfDoc, document, headerTitle, metadata);
        document.setMargins(50, 30, 20, 30);
        document.add(createSpacer(10));

        for (String chartType : CHARTABLE_TYPES) {
            List<JsonNode> questions = questionsByType.remove(chartType);
            if (questions != null && !questions.isEmpty()) {
                addSectionTitle(document, "Votes by " + formatQuestionType(chartType));
                for (JsonNode question : questions) {
                    if (hasAnswers(question)) {
                        processChartQuestion(document, question);
                    }
                }
            }
        }

        questionsByType.forEach((type, questions) -> {
            if (!questions.isEmpty()) {
                addSectionTitle(document, "Votes by " + formatQuestionType(type));
                processQuestionsInTable(document, questions);
            }
        });
    }

    private String formatQuestionType(String type) {
        if (type == null || type.isBlank()) {
            return "";
        }
        String[] words = type.replace("_", " ").toLowerCase().split("\\s+");
        StringBuilder formatted = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                formatted.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }
        return formatted.toString().trim();
    }

    private void drawHeader(PdfDocument pdfDoc, Document document, String headerText, PdfMetadata metadata) {
        if (pdfDoc.getNumberOfPages() == 0) {
            PdfPage firstPage = pdfDoc.addNewPage();
            PdfCanvas pdfCanvas = new PdfCanvas(firstPage);
            drawHeaderBackground(pdfCanvas, firstPage);
            drawHeaderText(pdfCanvas, firstPage, headerText, metadata);
        }
    }

    private void drawHeaderBackground(PdfCanvas pdfCanvas, PdfPage page) {
        float pageWidth = page.getPageSize().getWidth();
        pdfCanvas.setFillColor(HEADER_COLOR);
        pdfCanvas.rectangle(0, page.getPageSize().getTop() - HEADER_HEIGHT, pageWidth, HEADER_HEIGHT);
        pdfCanvas.fill();
    }

    private void drawHeaderText(PdfCanvas pdfCanvas, PdfPage page, String headerText, PdfMetadata metadata) {
        Rectangle textRect = new Rectangle(
                30,
                page.getPageSize().getTop() - HEADER_HEIGHT + 5,
                page.getPageSize().getWidth() - 60,
                HEADER_HEIGHT - 10
        );

        try (Canvas canvas = new Canvas(pdfCanvas, textRect)) {
            Table headerTable = new Table(UnitValue.createPercentArray(new float[]{50, 50}));
            headerTable.setWidth(UnitValue.createPercentValue(100));

            Cell titleCell = new Cell()
                    .add(new Paragraph(headerText)
                            .setFontSize(14)
                            .setBold()
                            .setFontColor(ColorConstants.WHITE))
                    .setBorder(null)
                    .setTextAlignment(TextAlignment.LEFT);

            ZonedDateTime saTime = ZonedDateTime.now(ZoneId.of("Asia/Riyadh"));
            String createdStr = "Created " + saTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            Cell createdCell = new Cell()
                    .add(new Paragraph(createdStr)
                            .setFontSize(10)
                            .setFontColor(ColorConstants.WHITE))
                    .setBorder(null)
                    .setTextAlignment(TextAlignment.RIGHT);

            headerTable.addCell(titleCell);
            headerTable.addCell(createdCell);

            String rangeInfo = String.format("From %s to %s",
                    metadata.startDate(), metadata.endDate());
            Cell rangeCell = new Cell(1, 2)
                    .add(new Paragraph(rangeInfo)
                            .setFontSize(9)
                            .setFontColor(ColorConstants.WHITE)
                            .setTextAlignment(TextAlignment.CENTER))
                    .setBorder(null)
                    .setPaddingTop(3);

            headerTable.addCell(rangeCell);

            canvas.add(headerTable);
        }
    }

    private Paragraph createSpacer(float height) {
        return new Paragraph("")
                .setHeight(height)
                .setMargin(0)
                .setPadding(0);
    }

    private void addSectionTitle(Document document, String sectionName) {
        Paragraph p = new Paragraph(sectionName)
                .setBold()
                .setFontSize(12)
                .setFontColor(SECTION_COLOR)
                .setMarginTop(20)
                .setMarginBottom(5);
        document.add(p);
    }

    private void processChartQuestion(Document document, JsonNode question) throws Exception {
        Image chartImage = createSingleQuestionBarChart(question);
        chartImage.setMarginTop(0);
        chartImage.setMarginBottom(0);
        document.add(chartImage);
        document.add(createSpacer(10));
    }

    private void processQuestionsInTable(Document document, List<JsonNode> questions) {
        Table table = new Table(UnitValue.createPercentArray(new float[]{30, 70}))
                .useAllAvailableWidth()
                .setMarginTop(10)
                .setMarginBottom(20);

        table.addHeaderCell(createHeaderCell("Title"));
        table.addHeaderCell(createHeaderCell("Answers"));

        for (JsonNode question : questions) {
            String title = question.path("title").asText("--");
            Cell answersCell = new Cell().setPadding(2);

            String singleAnswer = question.path("singleAnswer").asText("");
            if (!singleAnswer.isEmpty()) {
                answersCell.add(new Paragraph(singleAnswer).setFontSize(9));
            }

            String rawType = question.path("type").asText("");
            String normalizedType = rawType.toLowerCase().replace("_", " ").trim();

            if ((normalizedType.contains("multi selection") || normalizedType.contains("multi choice"))
                    && hasAnswers(question)) {
                try {
                    answersCell.add(buildProgressBarAnswers(question.get("answers")));
                } catch (Exception ex) {
                    log.error("Error building progress bar answers", ex);
                    answersCell.add(new Paragraph("Error displaying answers").setFontSize(9));
                }
            } else if (hasAnswers(question)) {
                answersCell.add(buildAnswersTableSortedByCount(question.get("answers")));
            }

            if (singleAnswer.isEmpty() && !hasAnswers(question)) {
                answersCell.add(new Paragraph("--").setFontSize(9));
            }

            table.addCell(createCell(title));
            table.addCell(answersCell);
        }
        document.add(table);
    }

    private Table buildProgressBarAnswers(JsonNode answersArray) throws Exception {
        List<JsonNode> answersList = new ArrayList<>();
        answersArray.forEach(answersList::add);

        answersList.sort((a, b) -> Double.compare(
                b.path("answerPercentage").asDouble(0.0),
                a.path("answerPercentage").asDouble(0.0)
        ));

        Table table = new Table(UnitValue.createPercentArray(new float[]{40, 50, 10}))
                .useAllAvailableWidth();

        for (JsonNode answer : answersList) {
            String name = answer.path("name").asText("--");
            double percentage = answer.path("answerPercentage").asDouble(0.0);

            Image progressBar = createProgressBarImage(percentage, 100, 10);
            String percentageStr = String.format("%.2f%%", percentage);

            table.addCell(new Cell()
                    .add(new Paragraph(name).setFontSize(9))
                    .setBorder(null));
            table.addCell(new Cell()
                    .add(progressBar)
                    .setBorder(null));
            table.addCell(new Cell()
                    .add(new Paragraph(percentageStr).setFontSize(9))
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setBorder(null));
        }
        return table;
    }

    private Image createProgressBarImage(double percentage, int width, int height) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();

        g2.setColor(Color.LIGHT_GRAY);
        g2.fillRect(0, 0, width, height);

        int progressWidth = (int) ((percentage / 100.0) * width);
        g2.setColor(new Color(0xA5, 0x4F, 0xE0));
        g2.fillRect(0, 0, progressWidth, height);

        g2.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);

        return new Image(ImageDataFactory.create(baos.toByteArray()))
                .setAutoScale(false)
                .scaleAbsolute(width, height);
    }

    private Table buildAnswersTableSortedByCount(JsonNode answersArray) {
        Table subTable = new Table(UnitValue.createPercentArray(new float[]{40, 30, 30}))
                .useAllAvailableWidth()
                .setMarginTop(5)
                .setMarginBottom(5);
        subTable.addHeaderCell(createHeaderCell("Option"));
        subTable.addHeaderCell(createHeaderCell("Count"));
        subTable.addHeaderCell(createHeaderCell("Percentage"));

        List<JsonNode> answersList = new ArrayList<>();
        answersArray.forEach(answersList::add);
        answersList.sort((a, b) -> Integer.compare(b.path("answerCount").asInt(0), a.path("answerCount").asInt(0)));

        for (JsonNode answer : answersList) {
            String name = answer.path("name").asText("--");
            int count = answer.path("answerCount").asInt(0);
            double percentage = answer.path("answerPercentage").asDouble(0.0);
            subTable.addCell(createCell(name));
            subTable.addCell(createCell(String.valueOf(count)));
            subTable.addCell(createCell(String.format("%.2f%%", percentage)));
        }
        return subTable;
    }

    private Cell createCell(String text) {
        return new Cell()
                .setPadding(2)
                .add(new Paragraph(text).setFontSize(9));
    }

    private Cell createHeaderCell(String text) {
        return new Cell()
                .setPadding(3)
                .setBackgroundColor(HEADER_COLOR)
                .add(new Paragraph(text)
                        .setFontSize(9)
                        .setBold()
                        .setFontColor(ColorConstants.WHITE));
    }

    private boolean hasAnswers(JsonNode question) {
        return question.has("answers")
                && question.get("answers").isArray()
                && question.get("answers").size() > 0;
    }

    private Image createSingleQuestionBarChart(JsonNode question) throws Exception {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        question.get("answers").forEach(ans ->
                dataset.addValue(
                        ans.path("answerPercentage").asDouble(0.0),
                        "Series",
                        ans.path("name").asText("Option?")
                )
        );

        JFreeChart barChart = ChartFactory.createBarChart("", null, null, dataset,
                PlotOrientation.VERTICAL, false, true, false);
        CategoryPlot plot = barChart.getCategoryPlot();
        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, BAR_COLOR);
        renderer.setMaximumBarWidth(0.05);
        plot.setBackgroundPaint(Color.WHITE);
        plot.setDomainGridlinePaint(Color.LIGHT_GRAY);
        plot.setRangeGridlinePaint(Color.LIGHT_GRAY);

        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setRange(0.0, 100.0);
        rangeAxis.setTickUnit(new NumberTickUnit(10.0));

        CategoryAxis domainAxis = plot.getDomainAxis();
        domainAxis.setTickLabelFont(new Font("SansSerif", Font.PLAIN, 9));

        BufferedImage chartImg = barChart.createBufferedImage((int) CHART_WIDTH, (int) CHART_HEIGHT);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(chartImg, "png", baos);

        Image chartImage = new Image(ImageDataFactory.create(baos.toByteArray()))
                .setAutoScale(false)
                .scaleAbsolute(CHART_WIDTH, CHART_HEIGHT);
        return chartImage;
    }
}


