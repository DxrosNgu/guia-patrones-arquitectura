package com.mercadoinventos.inventory;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.when;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

/** Con un Kafka REAL (Dev Services levanta uno en Docker) y un Postgres de Dev Services. */
@QuarkusTest
class OrderEventsConsumerTest {

    @ConfigProperty(name = "kafka.bootstrap.servers")
    String bootstrapServers;

    @InjectMock
    OrderItemsSource orders;

    private void publish(String key, String value) throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>("orders.events", key, value)).get();
        }
    }

    private static String orderPlaced(String orderId, String itemsJson) {
        String items = itemsJson == null ? "" : ",\"items\":" + itemsJson;
        return "{\"eventId\":\"" + UUID.randomUUID() + "\",\"type\":\"OrderPlaced\",\"aggregateId\":\"" + orderId
                + "\",\"occurredAt\":\"2026-11-16T10:00:00Z\",\"payload\":{\"customerId\":\"ana\"" + items + "}}";
    }

    private static List<String> statusesOf(String orderId) {
        return given().queryParam("orderId", orderId).get("/api/reservations").then().statusCode(200)
                .extract().jsonPath().getList("status");
    }

    @Test
    void reservesStockFromAnEventCarryingTheItems() throws Exception {
        String orderId = UUID.randomUUID().toString();

        publish(orderId, orderPlaced(orderId, "[{\"sku\":\"INV-004\",\"quantity\":2}]"));

        await().atMost(Duration.ofSeconds(20)).until(() -> statusesOf(orderId).contains("RESERVED"));
    }

    @Test
    void rejectsWhenThereIsNotEnoughStock() throws Exception {
        String orderId = UUID.randomUUID().toString();

        publish(orderId, orderPlaced(orderId, "[{\"sku\":\"INV-002\",\"quantity\":1}]"));

        await().atMost(Duration.ofSeconds(20)).until(() -> statusesOf(orderId).contains("REJECTED"));
    }

    @Test
    void aPoisonMessageGoesToTheDeadLetterTopicAndTheConsumerGoesOn() throws Exception {
        String poison = "esto no es JSON " + UUID.randomUUID();
        publish("veneno", poison);
        String orderId = UUID.randomUUID().toString();
        publish(orderId, orderPlaced(orderId, "[{\"sku\":\"INV-001\",\"quantity\":1}]"));

        await().atMost(Duration.ofSeconds(20)).until(() -> statusesOf(orderId).contains("RESERVED"));
        ConsumerRecord<String, String> dead = readDeadLetter(poison);
        assertThat(new String(dead.headers().lastHeader("dead-letter-reason").value(), StandardCharsets.UTF_8))
                .contains("JSON");
    }

    @Test
    void retriesATransientFailureUntilOrdersAnswers() throws Exception {
        String orderId = UUID.randomUUID().toString();
        when(orders.itemsOf(orderId))
                .thenThrow(new TransientException("orders caído"))
                .thenThrow(new TransientException("orders caído"))
                .thenReturn(List.of(new Item("INV-003", 1)));

        publish(orderId, orderPlaced(orderId, null)); // Event Notification: sin ítems

        await().atMost(Duration.ofSeconds(30)).until(() -> statusesOf(orderId).contains("RESERVED"));
        given().get("/api/stock").then().body("sku", hasItem("INV-003"));
    }

    private ConsumerRecord<String, String> readDeadLetter(String value) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of("orders.events.dlq-quarkus"));
            List<ConsumerRecord<String, String>> seen = new ArrayList<>();
            await().atMost(Duration.ofSeconds(20)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(seen::add);
                return seen.stream().anyMatch(r -> value.equals(r.value()));
            });
            return seen.stream().filter(r -> value.equals(r.value())).findFirst().orElseThrow();
        }
    }

}
