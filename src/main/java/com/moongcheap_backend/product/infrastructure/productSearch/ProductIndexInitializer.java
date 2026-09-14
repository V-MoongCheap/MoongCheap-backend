package com.moongcheap_backend.product.infrastructure.productSearch;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.opensearch.indices.IndexSettings;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "opensearch.auto-create-indices", havingValue = "true")
public class ProductIndexInitializer implements CommandLineRunner {

    private static final String INDEX_NAME = "product_catalog_v1";
    private static final String ALIAS_NAME = "product_catalog";
    private static final String SETTINGS_RESOURCE = "opensearch/product-catalog-index.json";

    private final OpenSearchClient openSearchClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    @Override
    public void run(String... args) throws Exception {
        try {
            initIndex();
        } catch (ConnectException | SocketTimeoutException |
                 NoRouteToHostException | UnknownHostException e) {
            log.warn("OpenSearch 시작 시 연결 실패, 서킷 브레이커 OPEN 전환: {}", e.getMessage());
            circuitBreakerRegistry.circuitBreaker("opensearch").transitionToOpenState();
        }
    }

    private void initIndex() throws Exception {
        boolean indexExists = openSearchClient.indices()
            .exists(e -> e.index(INDEX_NAME))
            .value();
        if (indexExists) {
            log.info("OpenSearch index '{}' already exists, skipping creation.", INDEX_NAME);
            ensureAlias();
            return;
        }

        IndexDefinition body = loadIndexDefinition();
        openSearchClient.indices().create(c -> c
            .index(INDEX_NAME)
            .settings(body.settings())
            .mappings(body.mappings())
            .aliases(ALIAS_NAME, a -> a)
        );
        log.info("Created OpenSearch index '{}' with alias '{}'.", INDEX_NAME, ALIAS_NAME);
    }

    private IndexDefinition loadIndexDefinition() throws Exception {
        JsonpMapper mapper = openSearchClient._transport().jsonpMapper();
        JsonObject json;
        try (
            InputStream in = new ClassPathResource(SETTINGS_RESOURCE).getInputStream();
            Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
            JsonReader jsonReader = Json.createReader(reader)
        ) {
            json = jsonReader.readObject();
        }
        IndexSettings settings = deserialize(mapper, json.get("settings"),
            IndexSettings._DESERIALIZER);
        TypeMapping mappings = deserialize(mapper, json.get("mappings"),
            TypeMapping._DESERIALIZER);
        return new IndexDefinition(settings, mappings);
    }

    private <T> T deserialize(JsonpMapper mapper, JsonValue value,
        JsonpDeserializer<T> deserializer) {
        try (JsonParser parser = mapper.jsonProvider()
            .createParser(new StringReader(value.toString()))) {
            return deserializer.deserialize(parser, mapper);
        }
    }

    private void ensureAlias() throws Exception {
        boolean aliasExists = openSearchClient.indices()
            .existsAlias(e -> e.name(ALIAS_NAME).index(INDEX_NAME))
            .value();
        if (aliasExists) {
            return;
        }
        openSearchClient.indices().putAlias(p -> p.index(INDEX_NAME).name(ALIAS_NAME));
        log.info("Attached alias '{}' to existing index '{}'.", ALIAS_NAME, INDEX_NAME);
    }

    private record IndexDefinition(IndexSettings settings, TypeMapping mappings) {

    }
}
