package com.example.consensus;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.testcontainers.service.connection.Ssl;
import org.springframework.context.annotation.Bean;
import org.testcontainers.activemq.ActiveMQContainer;
import org.testcontainers.activemq.ArtemisContainer;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.grafana.LgtmStackContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    ActiveMQContainer activemqContainer() {
        return new ActiveMQContainer(DockerImageName.parse("apache/activemq:latest"));
    }

    @Bean
    @ServiceConnection
    ArtemisContainer artemisContainer() {
        return new ArtemisContainer(DockerImageName.parse("apache/artemis:latest"));
    }

    @Bean
    @ServiceConnection
    CassandraContainer cassandraContainer() {
        return new CassandraContainer(DockerImageName.parse("cassandra:latest"));
    }

    @Bean
    @ServiceConnection
    @Ssl
    ElasticsearchContainer elasticsearchContainer() {
        return new ElasticsearchContainer(DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.3.3"));
    }

    @Bean
    @ServiceConnection
    LgtmStackContainer grafanaLgtmContainer() {
        return new LgtmStackContainer(DockerImageName.parse("grafana/otel-lgtm:latest"));
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka-native:latest"));
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:latest")).withExposedPorts(6379);
    }

}
