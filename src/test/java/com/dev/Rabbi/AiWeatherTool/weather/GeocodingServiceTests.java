package com.dev.Rabbi.AiWeatherTool.weather;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeocodingServiceTests {

    @Test
    void resolvesCityStateToCoordinates() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(UriComponentsBuilder.fromUriString("https://example.com/search")
                .queryParam("q", "New York, NY")
                .queryParam("countrycodes", "us")
                .queryParam("format", "jsonv2")
                .queryParam("addressdetails", 1)
                .queryParam("limit", 5)
                .build()
                .toUri()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{
                          "lat": "40.7127",
                          "lon": "-74.0060",
                          "display_name": "New York, New York, United States",
                          "address": {
                            "city": "New York",
                            "ISO3166-2-lvl4": "US-NY"
                          }
                        }]
                        """, MediaType.APPLICATION_JSON));

        GeocodingService service = new GeocodingService(builder, "https://example.com", "test-agent");

        GeocodingService.GeocodingResult result = service.geocode("New York, NY");

        assertThat(result.success()).isTrue();
        assertThat(result.latitude()).isEqualTo(40.7127);
        assertThat(result.longitude()).isEqualTo(-74.0060);
        assertThat(result.stateCode()).isEqualTo("NY");
        server.verify();
    }

    @Test
    void rejectsAmbiguousLocations() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        server.expect(requestTo(UriComponentsBuilder.fromUriString("https://example.com/search")
                .queryParam("q", "Springfield")
                .queryParam("countrycodes", "us")
                .queryParam("format", "jsonv2")
                .queryParam("addressdetails", 1)
                .queryParam("limit", 5)
                .build()
                .toUri()))
                .andRespond(withSuccess("""
                        [
                          {
                            "lat": "39.78",
                            "lon": "-89.64",
                            "display_name": "Springfield, Illinois, United States",
                            "address": {"city": "Springfield", "ISO3166-2-lvl4": "US-IL"}
                          },
                          {
                            "lat": "44.04",
                            "lon": "-123.02",
                            "display_name": "Springfield, Oregon, United States",
                            "address": {"city": "Springfield", "ISO3166-2-lvl4": "US-OR"}
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        GeocodingService service = new GeocodingService(builder, "https://example.com", "test-agent");

        GeocodingService.GeocodingResult result = service.geocode("Springfield");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("ambiguous");
        server.verify();
    }
}
