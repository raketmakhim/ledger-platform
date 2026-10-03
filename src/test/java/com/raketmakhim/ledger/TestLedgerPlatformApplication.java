package com.raketmakhim.ledger;

import org.springframework.boot.SpringApplication;

public class TestLedgerPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.from(LedgerPlatformApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
