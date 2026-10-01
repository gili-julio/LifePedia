package com.lifepedia.api;

import org.springframework.boot.SpringApplication;

public class TestLifepediaApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(LifepediaApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
