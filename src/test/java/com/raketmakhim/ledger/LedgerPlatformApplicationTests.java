package com.raketmakhim.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("integration")
@SpringBootTest
@Testcontainers
class LedgerPlatformApplicationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void contextLoads() {
	}

	@Test
	void seedsOneClearingAccountPerCurrency() {
		List<String> currencies = jdbc.queryForList(
				"SELECT currency FROM account WHERE type = 'SYSTEM' ORDER BY currency", String.class);

		assertThat(currencies).containsExactly("EUR", "GBP", "JPY", "USD");
	}

	@Test
	void rejectsSecondClearingAccountForSameCurrency() {
		assertThatThrownBy(() -> jdbc.update(
				"INSERT INTO account (id, currency, type, status) VALUES (?, 'GBP', 'SYSTEM', 'ACTIVE')",
				UUID.randomUUID()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

}
