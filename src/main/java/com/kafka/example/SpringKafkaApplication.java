package com.kafka.example;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StoreQueryParameters;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.QueryableStoreTypes;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.StreamsBuilderFactoryBean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.github.javafaker.Faker;

import reactor.core.publisher.Flux;

@SpringBootApplication
@EnableKafkaStreams
public class SpringKafkaApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringKafkaApplication.class, args);
		List<String> stringList = new ArrayList<String>();
		stringList.add("A");
		stringList.add("B");
		stringList.add("C");
		stringList.add("D");

		Stream<String>  p = stringList.stream();
		List<String> collect = p.filter(string -> string.equalsIgnoreCase("A")).collect(Collectors.toList());
		
	}

	@Bean
	NewTopic hobbit2() {
		return TopicBuilder.name("hobbit2").partitions(15).replicas(3).build();
	}

	@Bean
	NewTopic counts() {
		return TopicBuilder.name("streams-wordcount-output").partitions(6).replicas(3).build();
	}

}

@Component
class Producer {
	private KafkaTemplate<Integer, String> template = null;

	Producer(KafkaTemplate<Integer, String> template) {
		this.template = template;
	}

	Faker faker;

	@EventListener(ApplicationStartedEvent.class)
	public void generate() {
		faker = Faker.instance();

		final Flux<Long> interval = Flux.interval(Duration.ofMillis(1_000));
		final Flux<String> quotes = Flux.fromStream(Stream.generate(() -> faker.hobbit().quote()));
		Flux.zip(interval, quotes).map(it -> template.send("hobbit", faker.random().nextInt(42), it.getT2()))
				.blockLast();
	}
}

@Component
class Consumer {

	@KafkaListener(topics = { "hobbit" }, groupId = "spring-boot-kafka")
	public void consume(ConsumerRecord<Integer, String> record) {
		System.out.println("received = " + record.value() + " with key = " + record.key());
	}

//	@KafkaListener(topics = { "streams-wordcount-output" }, groupId = "spring-boot-kafka")
//	public void consume(ConsumerRecord<String, Long> record) {
//		System.out.println("received = " + record.value() + " with key = " + record.key());
//	}
}

@Component
class Processor {

	@Autowired
	public void process(StreamsBuilder streamsBuilder) {
		final Serde<Integer> integerSerde = Serdes.Integer();
		final Serde<String> stringSerde = Serdes.String();
		final Serde<Long> longSerde = Serdes.Long();

		KStream<Integer, String> textLines = streamsBuilder.stream("hobbit", Consumed.with(integerSerde, stringSerde));

		KTable<String, Long> wordCounts = textLines
				.flatMapValues(value -> Arrays.asList(value.toLowerCase().split("\\W+")))
				.groupBy((key, value) -> value, Grouped.with(stringSerde, stringSerde))
				.count(Materialized.as("counts"));

		wordCounts.toStream().to("streams-wordcount-output", Produced.with(stringSerde, longSerde));
	}
}

@RestController
class RestService {
	private final StreamsBuilderFactoryBean streamsBuilderFactoryBean;

	public RestService(StreamsBuilderFactoryBean streamsBuilderFactoryBean) {
		super();
		this.streamsBuilderFactoryBean = streamsBuilderFactoryBean;
	}

	@GetMapping("/count/{word}")
	public Long getCount(@PathVariable String word) {

		final KafkaStreams kafkaStreams = streamsBuilderFactoryBean.getKafkaStreams();

		final ReadOnlyKeyValueStore<String, Long> counts = kafkaStreams
				.store(StoreQueryParameters.fromNameAndType("counts", QueryableStoreTypes.keyValueStore()));
		return counts.get(word);
	}
}
