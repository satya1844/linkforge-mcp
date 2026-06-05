package com.satya.urlshortener;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.TimeZone;

@SpringBootTest
@ActiveProfiles("test")
class UrlshortenerApplicationTests {

	// ✅ static initializer block runs once before any tests
	static {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
	}

	@Test
	void contextLoads() {
		// This test just checks if the Spring context starts up correctly
	}
}
