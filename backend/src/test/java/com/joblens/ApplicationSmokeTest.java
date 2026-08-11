package com.joblens;

import static org.assertj.core.api.Assertions.assertThat;

import com.joblens.analysis.provider.AnalysisProvider;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationSmokeTest {

    @Value("${local.server.port}")
    private int port;

    private final AnalysisProvider analysisProvider;

    ApplicationSmokeTest(@org.springframework.beans.factory.annotation.Autowired AnalysisProvider analysisProvider) {
        this.analysisProvider = analysisProvider;
    }

    @Test
    void healthEndpointReportsUp() throws Exception {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"status\":\"UP\"");
            assertThat(response.body())
                    .as("health details must stay hidden so no internal component information leaks")
                    .doesNotContain("\"components\"");
        }
    }

    @Test
    void defaultAnalysisProviderKeepsDocumentsOnHost() {
        assertThat(analysisProvider.id()).isEqualTo("fake");
        assertThat(analysisProvider.sendsContentOffHost())
                .as("the default configuration must not be able to send document content to a third party")
                .isFalse();
    }
}
