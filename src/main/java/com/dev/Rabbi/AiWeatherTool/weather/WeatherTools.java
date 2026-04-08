package com.dev.Rabbi.AiWeatherTool.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class WeatherTools {

    private final RestClient restClient;
    private final GeocodingService geocodingService;

    public WeatherTools(
            RestClient.Builder builder,
            GeocodingService geocodingService,
            @Value("${weather.http.user-agent}") String userAgent
    ) {
        this.restClient = builder
                .baseUrl("https://api.weather.gov")
                .defaultHeader("Accept", "application/geo+json")
                .defaultHeader("User-Agent", userAgent)
                .build();
        this.geocodingService = geocodingService;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Points(@JsonProperty("properties") Props properties) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Props(@JsonProperty("forecast") String forecast) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Forecast(@JsonProperty("properties") Props properties) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Props(@JsonProperty("periods") List<Period> periods) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Period(@JsonProperty("number") Integer number, @JsonProperty("name") String name,
                             @JsonProperty("startTime") String startTime, @JsonProperty("endTime") String endTime,
                             @JsonProperty("isDaytime") Boolean isDayTime, @JsonProperty("temperature") Integer temperature,
                             @JsonProperty("temperatureUnit") String temperatureUnit,
                             @JsonProperty("temperatureTrend") String temperatureTrend,
                             @JsonProperty("probabilityOfPrecipitation") Precipitation probabilityOfPrecipitation,
                             @JsonProperty("windSpeed") String windSpeed, @JsonProperty("windDirection") String windDirection,
                             @JsonProperty("icon") String icon, @JsonProperty("shortForecast") String shortForecast,
                             @JsonProperty("detailedForecast") String detailedForecast) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Precipitation(@JsonProperty("value") Integer value) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Alert(@JsonProperty("features") List<Feature> features) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Feature(@JsonProperty("properties") Properties properties) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Properties(@JsonProperty("event") String event, @JsonProperty("areaDesc") String areaDesc,
                                 @JsonProperty("severity") String severity, @JsonProperty("description") String description,
                                 @JsonProperty("instruction") String instruction) {
        }
    }

    @Tool(description = "Get weather forecast for a specific latitude/longitude")
    public String getWeatherForecastByLocation(double latitude, double longitude) {
        return getForecastForCoordinates(latitude, longitude, null, "your selected coordinates");
    }

    @Tool(description = "Get weather forecast for a U.S. city and state, for example Dallas, TX or Seattle, WA")
    public String getWeatherForecastByCity(@ToolParam(description = "U.S. city and state, like Dallas, TX") String location) {
        GeocodingService.GeocodingResult geocodingResult = geocodingService.geocode(location);
        if (!geocodingResult.success()) {
            return geocodingResult.message();
        }

        return getForecastForCoordinates(
                geocodingResult.latitude(),
                geocodingResult.longitude(),
                geocodingResult.stateCode(),
                geocodingResult.displayName()
        );
    }

    @Tool(description = "Get weather alerts for a US state. Input is a two-letter US state code such as CA or NY.")
    public String getAlerts(@ToolParam(description = "Two-letter US state code, for example CA or NY") String state) {
        if (state == null || state.isBlank()) {
            return "Please provide a two-letter U.S. state code, like CA or NY.";
        }

        try {
            Alert alert = restClient.get()
                    .uri("/alerts/active/area/{state}", state.trim().toUpperCase())
                    .retrieve()
                    .body(Alert.class);

            if (alert == null || alert.features() == null || alert.features().isEmpty()) {
                return "There are no active weather alerts for " + state.trim().toUpperCase() + " right now.";
            }

            return formatAlerts(alert.features());
        } catch (RestClientException exception) {
            return "I couldn't load weather alerts right now. Please try again in a moment.";
        }
    }

    private String getForecastForCoordinates(double latitude, double longitude, String stateCode, String label) {
        try {
            Points points = restClient.get()
                    .uri("/points/{latitude},{longitude}", latitude, longitude)
                    .retrieve()
                    .body(Points.class);

            if (points == null || points.properties() == null || points.properties().forecast() == null) {
                return "I couldn't find a forecast for that location.";
            }

            Forecast forecast = restClient.get()
                    .uri(points.properties().forecast())
                    .retrieve()
                    .body(Forecast.class);

            if (forecast == null || forecast.properties() == null || forecast.properties().periods() == null
                    || forecast.properties().periods().isEmpty()) {
                return "I found the area, but the forecast details are unavailable right now.";
            }

            List<Forecast.Period> periods = forecast.properties().periods();
            String alerts = stateCode == null ? null : summarizeAlerts(stateCode);
            return formatForecast(label, latitude, longitude, periods, alerts);
        } catch (RestClientException exception) {
            return "I couldn't load the weather forecast right now. Please try again in a moment.";
        }
    }

    private String formatForecast(String label, double latitude, double longitude, List<Forecast.Period> periods, String alerts) {
        Forecast.Period current = periods.get(0);
        List<Forecast.Period> nextPeriods = periods.stream().limit(4).toList();

        List<String> sections = new ArrayList<>();
        sections.add("Weather for " + label);
        sections.add(String.format("Headline: %s, %s %s at %.4f, %.4f.",
                safe(current.shortForecast()),
                safe(current.temperature()),
                safe(current.temperatureUnit()),
                latitude,
                longitude));
        sections.add(String.format("Now/Next: %s with winds %s %s. %s",
                current.name(),
                safe(current.windSpeed()),
                safe(current.windDirection()),
                safe(current.detailedForecast())));

        String outlook = nextPeriods.stream()
                .map(this::formatPeriodLine)
                .collect(Collectors.joining("\n"));
        sections.add("Upcoming periods:\n" + outlook);

        if (alerts != null && !alerts.isBlank()) {
            sections.add(alerts);
        }

        return sections.stream().filter(Objects::nonNull).collect(Collectors.joining("\n\n"));
    }

    private String formatPeriodLine(Forecast.Period period) {
        String precipitation = "";
        if (period.probabilityOfPrecipitation() != null && period.probabilityOfPrecipitation().value() != null) {
            precipitation = ", precipitation " + period.probabilityOfPrecipitation().value() + "%";
        }

        return String.format("- %s: %s, %s %s, wind %s %s%s.",
                safe(period.name()),
                safe(period.shortForecast()),
                safe(period.temperature()),
                safe(period.temperatureUnit()),
                safe(period.windSpeed()),
                safe(period.windDirection()),
                precipitation);
    }

    private String summarizeAlerts(String stateCode) {
        try {
            Alert alert = restClient.get()
                    .uri("/alerts/active/area/{state}", stateCode)
                    .retrieve()
                    .body(Alert.class);

            if (alert == null || alert.features() == null || alert.features().isEmpty()) {
                return "Alerts: No active weather alerts for " + stateCode + " right now.";
            }

            return "Alerts:\n" + alert.features().stream()
                    .limit(3)
                    .map(feature -> String.format("- %s (%s): %s",
                            safe(feature.properties().event()),
                            safe(feature.properties().severity()),
                            safe(feature.properties().areaDesc())))
                    .collect(Collectors.joining("\n"));
        } catch (RestClientException exception) {
            return "Alerts: Unable to load alerts right now.";
        }
    }

    private String formatAlerts(List<Alert.Feature> features) {
        return features.stream()
                .limit(5)
                .map(feature -> String.format("""
                        Event: %s
                        Area: %s
                        Severity: %s
                        Description: %s
                        Instructions: %s
                        """,
                        safe(feature.properties().event()),
                        safe(feature.properties().areaDesc()),
                        safe(feature.properties().severity()),
                        safe(feature.properties().description()),
                        safe(feature.properties().instruction())))
                .collect(Collectors.joining("\n"));
    }

    private String safe(Object value) {
        return value == null ? "Unavailable" : value.toString();
    }
}
