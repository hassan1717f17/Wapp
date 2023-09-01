package com.example.wapp;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/csv")
public class CsvController {

    private List<String[]> csvData = new ArrayList<>();
    private final RestTemplate restTemplate = new RestTemplate();

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

    @GetMapping("/getTemperature")
    public ResponseEntity<String> getTemperature(@RequestParam double latitude, @RequestParam double longitude) {
        try {
            // Create the NWS API URL for the point.
            String apiUrl = "https://api.weather.gov/points/" + latitude + "," + longitude;

            // Create headers with the desired format (GeoJSON).
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Collections.singletonList(MediaType.valueOf("application/geo+json")));

            // Create a HttpEntity with headers.
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            // Send the GET request to fetch the NWS API point information.
            ResponseEntity<String> responseEntity = restTemplate.exchange(apiUrl, HttpMethod.GET, requestEntity, String.class);

            // Check the response code.
            if (responseEntity.getStatusCode() == HttpStatus.OK) {
                // Parse the JSON response for the point.
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode pointJson = objectMapper.readTree(responseEntity.getBody());

                // Extract the URL for the weather forecast from the point JSON.
                String forecastUrl = pointJson.at("/properties/forecast").asText();

                // Send an HTTP GET request to the forecast URL.
                ResponseEntity<String> forecastResponse = restTemplate.exchange(forecastUrl, HttpMethod.GET, requestEntity, String.class);

                // Check the response code and parse the forecast JSON.
                if (forecastResponse.getStatusCode() == HttpStatus.OK) {
                    String forecastJson = forecastResponse.getBody();

                    // Extract temperature and other relevant data from the forecast JSON.
                    JsonNode temperatureNode = objectMapper.readTree(forecastJson).at("/properties/periods/0/temperature");
                    double temperature = temperatureNode.asDouble();

                    // Extract units (if available).
                    JsonNode unitsNode = objectMapper.readTree(forecastJson).at("/properties/periods/0/temperatureUnit");
                    String units = unitsNode.asText();

                    // Prepare the response message with temperature data.
                    String responseMessage = String.format("Current Temperature: %.1f %s", temperature, units);

                    return ResponseEntity.ok(responseMessage);
                } else {
                    return ResponseEntity.badRequest().body("API Error: " + forecastResponse.getStatusCodeValue());
                }
            } else {
                return ResponseEntity.badRequest().body("API Error: " + responseEntity.getStatusCodeValue());
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

}

