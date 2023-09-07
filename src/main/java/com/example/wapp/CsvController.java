package com.example.wapp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/csv")
public class CsvController {

    private WeatherCacheManager weatherCacheManager;

    private static final Logger logger = LoggerFactory.getLogger(WeatherCacheManager.class);

    @Autowired
    public void setWeatherCacheManager(@Lazy WeatherCacheManager weatherCacheManager) {
        this.weatherCacheManager = weatherCacheManager;
    }

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

    @GetMapping("/viewcsv")
    public ResponseEntity<List<String[]>> viewCsv() {
        if (csvData.isEmpty()) {
            return ResponseEntity.badRequest().body(Collections.singletonList(new String[]{"CSV data is empty. Load a CSV file first."}));
        }
        return ResponseEntity.ok(csvData);
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

    // Helper method to check if a row matches a query in a specific column
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

    // Helper method to find the index of a column by name
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
            // Use the WeatherCacheManager to get weather data based on coordinates
            ResponseEntity<String> weatherData = weatherCacheManager.getWeatherData(latitude, longitude);

            if (weatherData.getStatusCode().is2xxSuccessful()) {
                // If weather data is found in the cache, return it directly
                return weatherData;
            } else {
                // If weather data is not found in the cache, fetch it from the NWS API
                return fetchWeatherInfo(latitude, longitude);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/getTemperatureFromCSV")
    public ResponseEntity<List<String>> getTemperatureFromCSV() {
        try {
            List<String> temperatureData = new ArrayList<>();

            if (csvData.isEmpty()) {
                return ResponseEntity.badRequest().body(Collections.singletonList("CSV data is empty. Load a CSV file first."));
            }

            // Skip the first row, which contains headers
            for (int i = 1; i < csvData.size(); i++) {
                String[] row = csvData.get(i);

                if (row.length >= 2) {
                    double latitude = Double.parseDouble(row[1]);
                    double longitude = Double.parseDouble(row[2]);

                    // Use the WeatherCacheManager to get weather data based on coordinates
                    ResponseEntity<String> weatherData = weatherCacheManager.getWeatherData(latitude, longitude);

                    if (weatherData.getStatusCode().is2xxSuccessful()) {
                        // If weather data is found in the cache, add it to the response list
                        temperatureData.add(weatherData.getBody());
                    } else {
                        // If weather data is not found in the cache, fetch it from the NWS API
                        ResponseEntity<String> weatherInfoResponse = fetchWeatherInfo(latitude, longitude);

                        if (weatherInfoResponse.getStatusCode().is2xxSuccessful()) {
                            // Add the weather info to the response list
                            temperatureData.add(weatherInfoResponse.getBody());

                            // Cache the weather data for future use
                            weatherCacheManager.cacheWeatherData(latitude, longitude, weatherInfoResponse);
                        } else {
                            // Handle the case where fetching data fails
                            temperatureData.add("API Error: " + weatherInfoResponse.getStatusCodeValue());
                        }
                    }
                }
            }

            return ResponseEntity.ok(temperatureData);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.singletonList("An unexpected error occurred"));
        }
    }




    // Helper method to fetch weather information from the NWS API
    private ResponseEntity<String> fetchWeatherInfo(double latitude, double longitude) {
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

                    // Extract date and time.
                    JsonNode dateNode = objectMapper.readTree(forecastJson).at("/properties/periods/0/startTime");
                    String startTimeStr = dateNode.asText();

                    // Parse the ISO-8601 timestamp.
                    ZonedDateTime startTime = ZonedDateTime.parse(startTimeStr);

                    // Format the date and time in a more human-readable format.
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", Locale.US);
                    String formattedTime = startTime.format(formatter);
                    // Prepare the response message.
                    String responseMessage = String.format("Date and Time: %s, Current Temperature: %.1f %s", formattedTime, temperature, units);

                    // Return the weather information as a successful response
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






