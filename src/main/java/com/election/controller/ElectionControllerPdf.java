package com.election.controller;

import com.election.service.ElectionServicePdf;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/election")
@RequiredArgsConstructor
public class ElectionControllerPdf {
    private static final Logger logger = LoggerFactory.getLogger(ElectionControllerPdf.class);
    private final ElectionServicePdf electionService;

    @PostMapping("/generate-pdf")
    public ResponseEntity<ByteArrayResource> generatePdf(@RequestBody String jsonString) {
        String electionName = "election_report";
        try {
            JsonNode dataNode = new ObjectMapper().readTree(jsonString).path("data");
            electionName = dataNode.path("electionName").asText("election_report");
        } catch (Exception e) {
            logger.error("Error reading election name from JSON", e);
        }

        ByteArrayResource pdf = electionService.generatePdf(jsonString);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", electionName + ".pdf");

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdf);
    }
}
