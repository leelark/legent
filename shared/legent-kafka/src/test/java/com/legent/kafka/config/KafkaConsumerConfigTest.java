package com.legent.kafka.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class KafkaConsumerConfigTest {

    @Test
    void consumerFactory_usesNarrowTrustedPackagesAndIgnoresTypeHeaders() {
        KafkaConsumerConfig config = new KafkaConsumerConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
        ReflectionTestUtils.setField(config, "groupId", "test-group");
        ReflectionTestUtils.setField(config, "trustedPackages", "java.lang,java.util,com.legent.kafka.model");
        ReflectionTestUtils.setField(config, "valueDefaultType", "com.legent.kafka.model.EventEnvelope");
        ReflectionTestUtils.setField(config, "useTypeInfoHeaders", false);

        DefaultKafkaConsumerFactory<String, Object> factory =
                (DefaultKafkaConsumerFactory<String, Object>) config.consumerFactory();
        Map<String, Object> props = factory.getConfigurationProperties();

        assertEquals("localhost:9092", props.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
        assertEquals("java.lang,java.util,com.legent.kafka.model", props.get(JsonDeserializer.TRUSTED_PACKAGES));
        assertEquals("com.legent.kafka.model.EventEnvelope", props.get(JsonDeserializer.VALUE_DEFAULT_TYPE));
        assertEquals(false, props.get(JsonDeserializer.USE_TYPE_INFO_HEADERS));
        assertFalse(String.valueOf(props.get(JsonDeserializer.TRUSTED_PACKAGES)).contains("*"));
    }

    @Test
    void kafkaErrorHandler_usesRecoveringHandlerForDlqPublishing() {
        KafkaConsumerConfig config = new KafkaConsumerConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
        ReflectionTestUtils.setField(config, "groupId", "test-group");
        ReflectionTestUtils.setField(config, "trustedPackages", "java.lang,java.util,com.legent.kafka.model");
        ReflectionTestUtils.setField(config, "valueDefaultType", "com.legent.kafka.model.EventEnvelope");
        ReflectionTestUtils.setField(config, "useTypeInfoHeaders", false);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);

        DefaultErrorHandler errorHandler = config.kafkaErrorHandler(kafkaTemplate);

        assertNotNull(errorHandler);
        assertNotNull(config.kafkaListenerContainerFactory(errorHandler));
    }
}
