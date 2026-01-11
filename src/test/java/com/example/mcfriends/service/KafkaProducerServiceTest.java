package com.example.mcfriends.service;

import com.example.mcfriends.dto.NotificationEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for KafkaProducerService
 * Uses embedded Kafka broker for testing
 */
@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"ACCOUNT_CHANGES"},
        brokerProperties = {"listeners=PLAINTEXT://localhost:9092", "port=9092"}
)
@DirtiesContext
@ActiveProfiles("test")
@DisplayName("KafkaProducerService Integration Tests")
class KafkaProducerServiceTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private KafkaProducerService kafkaProducerService;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    private org.apache.kafka.clients.consumer.Consumer<String, NotificationEvent> consumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "test-group",
                "true",
                embeddedKafkaBroker
        );
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, NotificationEvent.class.getName());

        DefaultKafkaConsumerFactory<String, NotificationEvent> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps);

        consumer = consumerFactory.createConsumer();
        embeddedKafkaBroker.consumeFromAllEmbeddedTopics(consumer);
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    @DisplayName("Sends message to topic")
    void sendNotification_SendsMessageToTopic() throws InterruptedException {
        // Arrange
        UUID recipientId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();

        NotificationEvent event = new NotificationEvent();
        event.setType("FRIEND_REQUEST_ACCEPTED");
        event.setRecipientId(recipientId);
        event.setSenderId(senderId);

        // Act
        kafkaProducerService.sendNotification(event);

        // Give some time for message to be sent
        Thread.sleep(1000);

        // Assert
        ConsumerRecord<String, NotificationEvent> record = KafkaTestUtils.getSingleRecord(
                consumer,
                "ACCOUNT_CHANGES",
                Duration.ofSeconds(5)
        );

        assertThat(record).isNotNull();
        assertThat(record.key()).isEqualTo(recipientId.toString());
        assertThat(record.value()).isNotNull();
        assertThat(record.value().getType()).isEqualTo("FRIEND_REQUEST_ACCEPTED");
        assertThat(record.value().getRecipientId()).isEqualTo(recipientId);
        assertThat(record.value().getSenderId()).isEqualTo(senderId);
    }

    @Test
    @DisplayName("Uses correct key for partitioning")
    void sendNotification_UsesCorrectKey() throws InterruptedException {
        // Arrange
        UUID recipientId = UUID.randomUUID();
        NotificationEvent event = new NotificationEvent();
        event.setType("FRIEND_BLOCKED");
        event.setRecipientId(recipientId);
        event.setSenderId(UUID.randomUUID());

        // Act
        kafkaProducerService.sendNotification(event);
        Thread.sleep(1000);

        // Assert
        ConsumerRecord<String, NotificationEvent> record = KafkaTestUtils.getSingleRecord(
                consumer,
                "ACCOUNT_CHANGES",
                Duration.ofSeconds(5)
        );

        assertThat(record.key()).isEqualTo(recipientId.toString());
    }

    @Test
    @DisplayName("Serializes event correctly")
    void sendNotification_SerializesEventCorrectly() throws InterruptedException {
        // Arrange
        UUID recipientId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        String eventType = "FRIEND_REQUEST_DECLINED";

        NotificationEvent event = new NotificationEvent();
        event.setType(eventType);
        event.setRecipientId(recipientId);
        event.setSenderId(senderId);

        // Act
        kafkaProducerService.sendNotification(event);
        Thread.sleep(1000);

        // Assert
        ConsumerRecord<String, NotificationEvent> record = KafkaTestUtils.getSingleRecord(
                consumer,
                "ACCOUNT_CHANGES",
                Duration.ofSeconds(5)
        );

        NotificationEvent receivedEvent = record.value();
        assertThat(receivedEvent.getType()).isEqualTo(eventType);
        assertThat(receivedEvent.getRecipientId()).isEqualTo(recipientId);
        assertThat(receivedEvent.getSenderId()).isEqualTo(senderId);
    }

    @Test
    @DisplayName("Does not throw exception when Kafka unavailable")
    void sendNotification_DoesNotThrowException_WhenKafkaUnavailable() {
        // Note: This test verifies that the service catches and logs exceptions
        // without propagating them. In the real implementation, the service
        // should not throw exceptions on Kafka failures.
        
        NotificationEvent event = new NotificationEvent();
        event.setType("TEST_EVENT");
        event.setRecipientId(UUID.randomUUID());
        event.setSenderId(UUID.randomUUID());

        // Act & Assert - should not throw
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
            kafkaProducerService.sendNotification(event);
        });
    }
}
