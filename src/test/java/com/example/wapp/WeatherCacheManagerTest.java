package com.example.wapp;
import com.example.wapp.CsvController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class WeatherCacheManagerTest {

    @Mock
    private CsvController csvController;

    private WeatherCacheManager weatherCacheManager;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this); // Initialize mocks
        weatherCacheManager = new WeatherCacheManager(csvController);
    }

    @Test
    public void testWeatherDataCache() throws Exception {
        // Mock data for latitude and longitude
        double latitude = 42.0;
        double longitude = -73.5;

        // Mock response entity with weather data
        ResponseEntity<String> mockResponse = ResponseEntity.ok("Mock Weather Data");

        // Mock CsvController to return the response entity
        when(csvController.getTemperature(latitude, longitude)).thenReturn(mockResponse);

        // Fetch weather data for the first time, it should be fetched from CsvController
        ResponseEntity<String> firstFetch = weatherCacheManager.getWeatherData(latitude, longitude);
        assertEquals(HttpStatus.OK, firstFetch.getStatusCode());
        assertEquals("Mock Weather Data", firstFetch.getBody());

        // Fetch weather data for the second time, it should be retrieved from the cache
        ResponseEntity<String> secondFetch = weatherCacheManager.getWeatherData(latitude, longitude);
        assertEquals(HttpStatus.OK, secondFetch.getStatusCode());
        assertEquals("Mock Weather Data", secondFetch.getBody());

        // Verify that CsvController.getTemperature was called only once
        verify(csvController, times(1)).getTemperature(latitude, longitude);

        // Invalidate the cache for the location
        weatherCacheManager.invalidateCache(latitude, longitude);

        // Fetch weather data again after eviction, it should be fetched from CsvController
        ResponseEntity<String> thirdFetch = weatherCacheManager.getWeatherData(latitude, longitude);
        assertEquals(HttpStatus.OK, thirdFetch.getStatusCode());
        assertEquals("Mock Weather Data", thirdFetch.getBody());

        // Verify that CsvController.getTemperature was called again after eviction
        verify(csvController, times(2)).getTemperature(latitude, longitude);
    }

    // Additional tests for cache eviction policy can be added here
}
