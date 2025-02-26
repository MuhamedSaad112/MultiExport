package com.election.controller;

import com.election.service.PdfService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pdf")
public class PPdfController {

    private static final Logger logger = LoggerFactory.getLogger(PPdfController.class);
    private final PdfService pdfService;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public PPdfController(PdfService pdfService) {
        this.pdfService = pdfService;
    }

    @PostMapping("/charts")
    public ResponseEntity<ByteArrayResource> getCommitteePdf(@RequestBody String jsonString) {
        logger.info("Received request for Demand Committee PDF (answerPercentage).");

        try {
            JsonNode rootNode = OBJECT_MAPPER.readTree(jsonString);
            String voteTitle = rootNode.path("data").path("voteTitle").asText("Vote");
            ByteArrayResource pdfResource = pdfService.generateDemandCommitteePdf(jsonString);

            String fileName = voteTitle + " Results.pdf";
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");
            headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfResource);
        } catch (Exception e) {
            logger.error("Error generating PDF", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
