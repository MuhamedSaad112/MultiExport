package com.election.controller;

import com.election.service.ElectionExportService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/election/export")
public class ElectionExportController {

    private final ElectionExportService electionExportService;

    @PostMapping("/creator/excel")
    public ResponseEntity<byte[]> exportCreatorExcel(@RequestBody JsonNode data,
                                                     @RequestParam(value = "lang", defaultValue = "en") String lang) {
        return electionExportService.export(data, "creator", true, lang);
    }

    @PostMapping("/viewer/excel")
    public ResponseEntity<byte[]> exportViewerExcel(@RequestBody JsonNode data,
                                                    @RequestParam(value = "lang", defaultValue = "en") String lang) {
        return electionExportService.export(data, "viewer", true, lang);
    }

    @PostMapping("/creator/csv")
    public ResponseEntity<byte[]> exportCreatorCsv(@RequestBody JsonNode data,
                                                   @RequestParam(value = "lang", defaultValue = "en") String lang) {
        return electionExportService.export(data, "creator", false, lang);
    }

    @PostMapping("/viewer/csv")
    public ResponseEntity<byte[]> exportViewerCsv(@RequestBody JsonNode data,
                                                  @RequestParam(value = "lang", defaultValue = "en") String lang) {
        return electionExportService.export(data, "viewer", false, lang);
    }


}
