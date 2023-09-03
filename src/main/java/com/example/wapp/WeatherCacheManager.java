package com.example.wapp;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.springframework.http.ResponseEntity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
@Component
public class WeatherCacheManager {
    private static final int MAX_CACHE_SIZE = 100;
    private static final int CACHE_EXPIRATION_MINUTES = 10;

    private final LoadingCache<String, ResponseEntity<String>> weatherCache;
    private final CsvController csvController;

    public WeatherCacheManager(@Lazy CsvController csvController) {
        this.csvController = csvController;
        this.weatherCache = CacheBuilder.newBuilder()
                .maximumSize(MAX_CACHE_SIZE)
                .expireAfterWrite(CACHE_EXPIRATION_MINUTES, TimeUnit.MINUTES)
                .build(new CacheLoader<String, ResponseEntity<String>>() {
                    @Override
                    public ResponseEntity<String> load(String key) {
                        // Implement logic to fetch weather data and return it as a ResponseEntity
                        // This method will be called when data is not found in the cache
                        return fetchWeatherData(key);
                    }
                });
    }

    public void cacheWeatherData(double latitude, double longitude, ResponseEntity<String> weatherData) {
        String cacheKey = createCacheKey(latitude, longitude);
        weatherCache.put(cacheKey, weatherData);
    }

    // Method to fetch weather data if not found in cache
    private ResponseEntity<String> fetchWeatherData(String key) {
        double latitude = parseLatitudeFromKey(key);
        double longitude = parseLongitudeFromKey(key);

        // Check if weather data is available in the cache
        ResponseEntity<String> cachedResponse = getWeatherData(latitude, longitude);

        if (cachedResponse.getStatusCode().is2xxSuccessful()) {
            // If data is found in the cache, return it
            return cachedResponse;
        } else {
            // If not found in cache, fetch data from the API
            ResponseEntity<String> response = csvController.getTemperature(latitude, longitude);

            // Check if the response is successful (2xx status code)
            if (response.getStatusCode().is2xxSuccessful()) {
                // Cache the weather data for future use
                cacheWeatherData(latitude, longitude, response);

                return response; // Weather data fetched successfully
            } else {
                // Handle the case where fetching data fails
                throw new RuntimeException("Failed to fetch weather data");
            }
        }
    }

    // Helper method to create a cache key from latitude and longitude
    private String createCacheKey(double latitude, double longitude) {
        // Create a cache key in the format "lat,long"
        return String.format("%.6f,%.6f", latitude, longitude);
    }

    // Helper method to extract latitude from the cache key
    private double parseLatitudeFromKey(String key) {
        String[] parts = key.split(",");
        return Double.parseDouble(parts[0]);
    }

    // Helper method to extract longitude from the cache key
    private double parseLongitudeFromKey(String key) {
        String[] parts = key.split(",");
        return Double.parseDouble(parts[1]);
    }

    // Method to get weather data from cache or fetch it if not found
    public ResponseEntity<String> getWeatherData(double latitude, double longitude) {
        String cacheKey = createCacheKey(latitude, longitude);
        try {
            return weatherCache.get(cacheKey);
        } catch (Exception e) {
            // Handle cache-related exceptions or errors
            throw new RuntimeException("Error fetching weather data from cache", e);
        }
    }

    // Method to invalidate cache for a specific location
    public void invalidateCache(double latitude, double longitude) {
        String cacheKey = createCacheKey(latitude, longitude);
        weatherCache.invalidate(cacheKey);
    }
}
