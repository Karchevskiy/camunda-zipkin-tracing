# Injects brave trace context from kafka connectors (if used)

Hard forked io.zipkin.brave:brave-instrumentation-kafka-clients:5.9.0

adds KAFKA_PAYLOAD_CLASS_TAG = "kafka.payload.class"
Remove body on TracingConsumer#clearHeaders(Headers headers)

Key dependency

    <dependency>
      <groupId>org.apache.kafka</groupId>
      <artifactId>kafka-clients</artifactId>
    </dependency>

Compatible with 2.3.0 (back comp up to 2.1.0)

