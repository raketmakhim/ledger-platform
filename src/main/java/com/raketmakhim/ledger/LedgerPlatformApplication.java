package com.raketmakhim.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// Excluded from coverage: main() only starts Spring, and the integration test
// (LedgerPlatformApplicationTests) already proves the app starts.
@ExcludeFromJacocoGeneratedReport
@SpringBootApplication
public class LedgerPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(LedgerPlatformApplication.class, args);
	}

}
