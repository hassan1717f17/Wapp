package com.example.wapp;
import com.opencsv.CSVReader;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/csv")
public class CsvController {

    private List<String[]> csvData = new ArrayList<>();

    @PostMapping("/loadcsv")
    public ResponseEntity<String> loadCsv(@RequestParam("file") MultipartFile file) {
        try {
            // Clear existing data
            csvData.clear();

            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body("CSV file is empty");
            }

            // Read the CSV content from the uploaded file
            BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                // Split the CSV line into an array
                String[] row = line.split(",");
                csvData.add(row);
            }

            return ResponseEntity.ok("CSV loaded successfully");
        } catch (IOException e) {
            // Handle IO-related errors
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to read the CSV file");
        } catch (Exception e) {
            // Handle other unexpected errors
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An unexpected error occurred");
        }
    }

    // Implement error handling for other methods similarly

    @GetMapping("/viewcsv")
    public List<String[]> viewCsv() {
        if (csvData.isEmpty()) {
            throw new RuntimeException("CSV data is empty. Load a CSV file first.");
        }
        return csvData;
    }

    @GetMapping("/searchcsv")
    public List<String[]> searchCsv(
            @RequestParam(required = false) String column,
            @RequestParam(required = false) String query
    ) {
        if (csvData.isEmpty()) {
            throw new RuntimeException("CSV data is empty. Load a CSV file first.");
        }

        List<String[]> results = new ArrayList<>();

        for (String[] row : csvData) {
            if (matchesQuery(row, column, query)) {
                results.add(row);
            }
        }

        return results;
    }

    private boolean matchesQuery(String[] row, String column, String query) {
        if (column == null || column.isEmpty()) {
            // Search all columns
            for (String cell : row) {
                if (cell.equals(query)) {
                    return true; // Exact match found
                }
            }
        } else {
            // Search a specific column
            int columnIndex = getColumnIndex(column);
            if (columnIndex >= 0 && columnIndex < row.length && row[columnIndex].equals(query)) {
                return true; // Exact match found in the specified column
            }
        }
        return false;
    }

    private int getColumnIndex(String columnName) {
        // Find the index of the specified column name in the header row (assuming the first row contains headers)
        String[] headers = csvData.get(0);
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].equalsIgnoreCase(columnName)) {
                return i;
            }
        }
        return -1; // Column not found
    }


}
