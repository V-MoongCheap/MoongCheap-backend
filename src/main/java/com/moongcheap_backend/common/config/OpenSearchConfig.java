package com.moongcheap_backend.common.config;

import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Timeout;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenSearchConfig {

    @Value("${opensearch.host:localhost}")
    private String host;

    @Value("${opensearch.port:9200}")
    private int port;

    @Value("${opensearch.scheme:http}")
    private String scheme;

    @Bean(destroyMethod = "close")
    public PoolingAsyncClientConnectionManager connectionManager() {
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
            .setConnectTimeout(Timeout.ofSeconds(3))
            .setSocketTimeout(Timeout.ofSeconds(5))
            .build();
        return PoolingAsyncClientConnectionManagerBuilder.create()
            .setMaxConnTotal(10)
            .setMaxConnPerRoute(5)
            .setDefaultConnectionConfig(connectionConfig)
            .build();
    }

    @Bean
    public OpenSearchClient openSearchClient(
        PoolingAsyncClientConnectionManager connectionManager) {
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(5, TimeUnit.SECONDS)
            .setResponseTimeout(5, TimeUnit.SECONDS)
            .build();
        HttpHost httpHost = new HttpHost(scheme, host, port);
        OpenSearchTransport transport = ApacheHttpClient5TransportBuilder
            .builder(httpHost)
            .setHttpClientConfigCallback(builder -> builder
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig))
            .setMapper(new JacksonJsonpMapper())
            .build();
        return new OpenSearchClient(transport);
    }
}
