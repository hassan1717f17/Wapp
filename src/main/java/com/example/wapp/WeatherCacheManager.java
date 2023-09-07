package com.example.wapp;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.springframework.http.ResponseEntity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
@Component
public class WeatherCacheManager {
    private static final int MAX_CACHE_SIZE = 100;
    private static final int CACHE_EXPIRATION_MINUTES = 10;

    private final LoadingCache<String, ResponseEntity<String>> weatherCache;
    private final CsvController csvController;


    private static final Logger logger = LoggerFactory.getLogger(WeatherCacheManager.class);


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

        ResponseEntity<String> response = csvController.getTemperature(latitude, longitude);

        if (response.getStatusCode().is2xxSuccessful()) {
            logger.info("Weather data fetched successfully for key: {}", key);
            cacheWeatherData(latitude, longitude, response);
            return response;
        } else {
            logger.error("API Error: Failed to fetch weather data for key: {}, Status Code: {}", key, response.getStatusCodeValue());
            return ResponseEntity.badRequest().body("API Error: " + response.getStatusCodeValue());
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
            ResponseEntity<String> cachedResponse = weatherCache.get(cacheKey);
            if (cachedResponse != null) {
                logger.info("Weather data found in cache for key: {}", cacheKey);
                return cachedResponse;
            } else {
                ResponseEntity<String> response = fetchWeatherData(cacheKey);
                logger.info("Fetching weather data for key: {}", cacheKey);
                return response;
            }
        } catch (Exception e) {
            logger.error("Error fetching weather data from cache for key: {}", cacheKey, e);
            throw new RuntimeException("Error fetching weather data from cache", e);
        }
    }

    // Method to invalidate cache for a specific location
    public void invalidateCache(double latitude, double longitude) {
        String cacheKey = createCacheKey(latitude, longitude);
        weatherCache.invalidate(cacheKey);
    }
}
