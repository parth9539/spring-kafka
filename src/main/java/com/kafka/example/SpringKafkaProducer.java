package com.kafka.example;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class SpringKafkaProducer {

	private KafkaTemplate<Integer, String> template = null;

	SpringKafkaProducer(KafkaTemplate<Integer, String> template) {
		this.template = template;
	}

	public void generate(String data, Integer key) {
		template.send("hobbit", key, data);
	}

}
