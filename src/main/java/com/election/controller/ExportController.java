package com.election.controller;

import com.election.service.ExportService;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/export")
public class ExportController {

    private static final Logger logger = LoggerFactory.getLogger(ExportController.class);
    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @PostMapping("/creator/excel")
    public ResponseEntity<byte[]> exportCreatorExcel(@RequestBody JsonNode data,
                                                     @RequestParam(value = "lang", defaultValue = "en") String lang) {
        return exportService.export(data, "creator", true, lang);
    }

    @PostMapping("/viewer/excel")
    public ResponseEntity<byte[]> exportViewerExcel(@RequestBody JsonNode data,
                                                    @RequestParam(value = "lang", defaultValue = "en") String lang) {
        return exportService.export(data, "viewer", true, lang);
    }

    @PostMapping("/creator/csv")
    public ResponseEntity<byte[]> exportCreatorCsv(@RequestBody JsonNode data,
                                                   @RequestParam(value = "lang", defaultValue = "en") String lang) {
        return exportService.export(data, "creator", false, lang);
    }

    @PostMapping("/viewer/csv")
    public ResponseEntity<byte[]> exportViewerCsv(@RequestBody JsonNode data,
                                                  @RequestParam(value = "lang", defaultValue = "en") String lang) {
        return exportService.export(data, "viewer", false, lang);
    }


}
