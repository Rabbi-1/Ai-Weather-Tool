package com.dev.Rabbi.AiWeatherTool.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GeocodingService {

    private final RestClient restClient;

    public GeocodingService(
            RestClient.Builder builder,
            @Value("${weather.geocoding.base-url}") String geocodingBaseUrl,
            @Value("${weather.http.user-agent}") String userAgent
    ) {
        this.restClient = builder
                .baseUrl(geocodingBaseUrl)
                .defaultHeader("User-Agent", userAgent)
                .build();
    }

    public GeocodingResult geocode(String location) {
        String query = location == null ? "" : location.trim();
        if (query.isBlank()) {
            return GeocodingResult.invalid("Please provide a city and state, like Dallas, TX.");
        }

        try {
            SearchResult[] results = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", query)
                            .queryParam("countrycodes", "us")
                            .queryParam("format", "jsonv2")
                            .queryParam("addressdetails", 1)
                            .queryParam("limit", 5)
                            .build())
                    .retrieve()
                    .body(SearchResult[].class);

            if (results == null || results.length == 0) {
                return GeocodingResult.invalid("I couldn't find that location. Try a city and state like Seattle, WA.");
            }

            List<SearchResult> supportedResults = Arrays.stream(results)
                    .filter(result -> result.address() != null && extractStateCode(result.address().stateCode()) != null)
                    .toList();

            if (supportedResults.isEmpty()) {
                return GeocodingResult.invalid("I found a match, but it doesn't map cleanly to a U.S. state for alerts.");
            }

            Set<String> distinctPlaces = supportedResults.stream()
                    .map(result -> normalize(result.address().cityName()) + "|" + extractStateCode(result.address().stateCode()))
                    .filter(value -> !value.startsWith("|"))
                    .collect(Collectors.toSet());

            if (distinctPlaces.size() > 1) {
                String suggestions = supportedResults.stream()
                        .limit(3)
                        .map(SearchResult::displayName)
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining("; "));
                return GeocodingResult.invalid("That location is ambiguous. Try being more specific. Matches: " + suggestions);
            }

            SearchResult match = supportedResults.get(0);
            return GeocodingResult.success(
                    Double.parseDouble(match.latitude()),
                    Double.parseDouble(match.longitude()),
                    extractStateCode(match.address().stateCode()),
                    match.displayName()
            );
        } catch (RestClientException | NumberFormatException exception) {
            return GeocodingResult.invalid("I couldn't resolve that location right now. Please try again in a moment.");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    private String extractStateCode(String stateCode) {
        if (stateCode == null || stateCode.isBlank()) {
            return null;
        }
        int separator = stateCode.indexOf('-');
        String normalized = separator >= 0 ? stateCode.substring(separator + 1) : stateCode;
        return normalized.isBlank() ? null : normalized.toUpperCase(Locale.US);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SearchResult(
            @JsonProperty("lat") String latitude,
            @JsonProperty("lon") String longitude,
            @JsonProperty("display_name") String displayName,
            @JsonProperty("address") Address address
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Address(
            @JsonProperty("city") String city,
            @JsonProperty("town") String town,
            @JsonProperty("village") String village,
            @JsonProperty("municipality") String municipality,
            @JsonProperty("ISO3166-2-lvl4") String stateCode
    ) {
        String cityName() {
            if (city != null && !city.isBlank()) {
                return city;
            }
            if (town != null && !town.isBlank()) {
                return town;
            }
            if (village != null && !village.isBlank()) {
                return village;
            }
            return municipality;
        }
    }

    public record GeocodingResult(
            boolean success,
            double latitude,
            double longitude,
            String stateCode,
            String displayName,
            String message
    ) {
        static GeocodingResult success(double latitude, double longitude, String stateCode, String displayName) {
            return new GeocodingResult(true, latitude, longitude, stateCode, displayName, "");
        }

        static GeocodingResult invalid(String message) {
            return new GeocodingResult(false, 0, 0, null, null, message);
        }
    }
}
