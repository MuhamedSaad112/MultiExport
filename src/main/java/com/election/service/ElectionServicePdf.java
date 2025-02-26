package com.election.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceGray;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.layout.property.UnitValue;
import com.itextpdf.layout.renderer.CellRenderer;
import com.itextpdf.layout.renderer.DrawContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ElectionServicePdf {

    private static final Logger logger = LoggerFactory.getLogger(ElectionServicePdf.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DeviceRgb HEADER_BG = new DeviceRgb(0x4F, 0x01, 0x8B);
    private static final DeviceRgb TITLE_COLOR = new DeviceRgb(44, 62, 80);
    private static final DeviceRgb BAR_BG = new DeviceRgb(0xEE, 0xEE, 0xEE);
    private static final DeviceGray INSIGHTS_BG_GRAY = new DeviceGray(0.9f);
    private static final DeviceRgb STAT_LABEL_COLOR = new DeviceRgb(0x8D, 0x94, 0x98);

    public ByteArrayResource generatePdf(String jsonString) {
        logger.info("Starting PDF generation");
        try {
            JsonNode rootNode = objectMapper.readTree(jsonString);
            JsonNode dataNode = rootNode.has("data") ? rootNode.path("data") : rootNode;

            String electionName = dataNode.findValue("electionName") != null ? dataNode.findValue("electionName").asText("") : "";
            String electionDescription = dataNode.findValue("electionDescription") != null ? dataNode.findValue("electionDescription").asText("") : "";
            String endDate = dataNode.findValue("end-date") != null ? dataNode.findValue("end-date").asText("") : "";
            String endTime = dataNode.findValue("end-time") != null ? dataNode.findValue("end-time").asText("") : "";

            JsonNode insightsNode = dataNode.findValue("insights") != null ? dataNode.findValue("insights") : objectMapper.createObjectNode();
            int allVotersCount = insightsNode.has("allVotersCount") ? insightsNode.path("allVotersCount").asInt(1) : 1;

            LocalDateTime nowInRiyadh = LocalDateTime.now(ZoneId.of("Asia/Riyadh"));
            String timestamp = nowInRiyadh.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (PdfWriter writer = new PdfWriter(baos);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document doc = new Document(pdf)) {

                pdf.addNewPage();
                Table header = new Table(new float[]{1})
                        .setWidth(UnitValue.createPercentValue(100))
                        .setBorder(Border.NO_BORDER);
                Cell headerCell = new Cell()
                        .setBorder(Border.NO_BORDER)
                        .setBackgroundColor(HEADER_BG)
                        .setPadding(20);
                Table headerContent = new Table(new float[]{50, 50})
                        .setWidth(UnitValue.createPercentValue(100));
                headerContent.addCell(new Cell()
                        .setBorder(Border.NO_BORDER)
                        .setTextAlignment(TextAlignment.LEFT)
                        .setPadding(5)
                        .setMargin(0)
                        .add(new Paragraph("Election Result")
                                .setFontSize(14)
                                .setBold()
                                .setFontColor(ColorConstants.WHITE)));
                headerContent.addCell(new Cell()
                        .setBorder(Border.NO_BORDER)
                        .setTextAlignment(TextAlignment.RIGHT)
                        .setPadding(5)
                        .setMargin(0)
                        .add(new Paragraph("Created: " + timestamp)
                                .setFontSize(10)
                                .setBold()
                                .setFontColor(ColorConstants.WHITE)));
                headerCell.add(headerContent);
                header.addCell(headerCell);
                doc.add(header);

                doc.add(new Paragraph(electionName)
                        .setBold()
                        .setFontSize(16)
                        .setTextAlignment(TextAlignment.CENTER));
                doc.add(new Paragraph(electionDescription)
                        .setFontSize(10)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setFontColor(ColorConstants.DARK_GRAY));
                if (!endDate.isEmpty() || !endTime.isEmpty()) {
                    doc.add(new Paragraph("End Date: " + endDate + " | End Time: " + endTime)
                            .setFontSize(12)
                            .setTextAlignment(TextAlignment.CENTER));
                }
                doc.add(new Paragraph("\nInsights")
                        .setBold()
                        .setFontSize(14)
                        .setFontColor(TITLE_COLOR)
                        .setTextAlignment(TextAlignment.LEFT));
                Table stats = new Table(new float[]{1, 1})
                        .setWidth(UnitValue.createPercentValue(100))
                        .setMarginTop(5);
                stats.addCell(createStatCell(insightsNode.has("totalCandidates") ? insightsNode.path("totalCandidates").asText("") : "", "Total Candidates"));
                stats.addCell(createStatCell(insightsNode.has("allVotersCount") ? insightsNode.path("allVotersCount").asText("") : "", "All Participants"));
                stats.addCell(createStatCell(insightsNode.has("completionRate") ? insightsNode.path("completionRate").asText("") + "%" : "", "Completion Rate"));
                stats.addCell(createStatCell(insightsNode.has("submittedVotesCount") ? insightsNode.path("submittedVotesCount").asText("") : "", "Submitted Votes"));
                doc.add(stats);

                doc.add(new Paragraph("\nResults Summary")
                        .setBold()
                        .setFontSize(14));
                JsonNode results = dataNode.findValue("resultsSummary") != null ? dataNode.findValue("resultsSummary") : objectMapper.createArrayNode();
                if (results.isArray()) {
                    List<JsonNode> candidates = new ArrayList<>();
                    results.forEach(candidates::add);
                    candidates.sort(Comparator.comparingInt(a -> -a.path("numberOfVoters").asInt(0)));
                    drawResultBars(doc, candidates, allVotersCount);
                }
            }
            logger.info("PDF generated successfully");
            return new ByteArrayResource(baos.toByteArray());
        } catch (Exception e) {
            logger.error("Error generating PDF", e);
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }

    private Cell createStatCell(String value, String label) {
        Cell cell = new Cell()
                .setBackgroundColor(INSIGHTS_BG_GRAY)
                .setBorder(new SolidBorder(ColorConstants.WHITE, 2))
                .setPadding(15)
                .setMargin(0);
        cell.add(new Paragraph(value)
                .setBold()
                .setFontSize(12)
                .setFontColor(ColorConstants.BLACK));
        cell.add(new Paragraph(label)
                .setFontSize(10)
                .setFontColor(STAT_LABEL_COLOR));
        return cell;
    }

    private float calculateRatio(int votes, int allVotersCount) {
        return allVotersCount <= 0 ? 0f : Math.min(((float) votes / allVotersCount) * 100f, 100f);
    }

    private void drawResultBars(Document doc, List<JsonNode> candidates, int allVotersCount) {
        for (JsonNode candidate : candidates) {
            String name = candidate.findValue("candidateName") != null ? candidate.findValue("candidateName").asText("") : "";
            int votes = candidate.has("numberOfVoters") ? candidate.path("numberOfVoters").asInt(0) : 0;
            float ratio = calculateRatio(votes, allVotersCount);
            Table row = new Table(new float[]{1})
                    .setWidth(UnitValue.createPercentValue(100))
                    .setMarginBottom(10);
            Cell barCell = new Cell()
                    .setBorder(new SolidBorder(ColorConstants.GRAY, 1))
                    .setHeight(30)
                    .add(new Paragraph(name)
                            .setTextAlignment(TextAlignment.LEFT)
                            .setBold());
            barCell.setNextRenderer(new BarRenderer(barCell, ratio, votes));
            row.addCell(barCell);
            doc.add(row);
        }
    }

    private static class BarRenderer extends CellRenderer {
        private final float ratio;
        private final int votes;

        public BarRenderer(Cell cell, float ratio, int votes) {
            super(cell);
            this.ratio = ratio;
            this.votes = votes;
        }

        @Override
        public void draw(DrawContext context) {
            Rectangle rect = getOccupiedAreaBBox();
            PdfCanvas pdfCanvas = context.getCanvas();
            float width = (ratio / 100f) * rect.getWidth();
            pdfCanvas.saveState();
            pdfCanvas.setFillColor(BAR_BG);
            pdfCanvas.rectangle(rect.getLeft(), rect.getBottom(), width, rect.getHeight());
            pdfCanvas.fill();
            pdfCanvas.restoreState();
            Canvas canvasModel = new Canvas(pdfCanvas, context.getDocument(), rect);
            canvasModel.showTextAligned(new Paragraph(votes + " votes").setFontSize(12),
                    rect.getRight() - 5,
                    rect.getBottom() + rect.getHeight() / 2,
                    TextAlignment.RIGHT);
            canvasModel.close();
            super.draw(context);
        }
    }
}
