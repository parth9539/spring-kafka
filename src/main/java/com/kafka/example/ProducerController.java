package com.kafka.example;

import org.apache.commons.lang3.RandomUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProducerController {
	private SpringKafkaProducer springKafkaProducer;

	public ProducerController(SpringKafkaProducer springKafkaProducer) {
		super();
		this.springKafkaProducer = springKafkaProducer;
	}



	@PostMapping("/data/{data}")
	public void feeder(@PathVariable String data) {
		springKafkaProducer.generate(data, RandomUtils.nextInt());
	}
}
