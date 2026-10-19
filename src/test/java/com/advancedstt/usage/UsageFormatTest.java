package com.advancedstt.usage;

import com.advancedstt.Provider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class UsageFormatTest {
	@Test
	void numbers() {
		assertEquals("250,000", UsageFormat.number(Provider.CARTESIA, 250_000));
		assertEquals("0", UsageFormat.number(Provider.CARTESIA, -5));
		assertEquals("$0.0075", UsageFormat.number(Provider.ASSEMBLYAI, 0.0075));
		assertEquals("$0.450", UsageFormat.number(Provider.ASSEMBLYAI, 0.45));
		assertEquals("$49.55", UsageFormat.number(Provider.ASSEMBLYAI, 49.55));
		assertEquals("$0.000", UsageFormat.number(Provider.ASSEMBLYAI, 0));
		assertEquals("1,234 credits", UsageFormat.amount(Provider.CARTESIA, 1234));
	}

	@Test
	void durations() {
		assertEquals("0 s", UsageFormat.duration(0));
		assertEquals("12 s", UsageFormat.duration(12.4));
		assertEquals("6 min 51 s", UsageFormat.duration(411));
		assertEquals("2 h 05 min", UsageFormat.duration(2 * 3600 + 5 * 60 + 30));
		assertEquals("2 min", UsageFormat.duration(120));
		assertEquals("10 min", UsageFormat.duration(600));
		assertEquals("1 h", UsageFormat.duration(3600));
	}

	@Test
	void ratesHaveNoTrailingZeros() {
		assertEquals("3", UsageFormat.plain(3.0));
		assertEquals("0.45", UsageFormat.plain(0.45));
		assertEquals("2.5", UsageFormat.plain(2.50));
	}

	@Test
	void frenchNumbers() {
		assertEquals("100 000", UsageFormat.localize("100,000", "fr_fr"));
		assertEquals("0,0075 $", UsageFormat.localize("$0.0075", "fr_ca"));
		assertEquals("1 234,50 $", UsageFormat.localize("$1,234.50", "fr_fr"));
		assertEquals("0,45", UsageFormat.localize("0.45", "fr_fr"));
		assertEquals("100,000", UsageFormat.localize("100,000", "en_us"));
		assertEquals("$0.0075", UsageFormat.localize("$0.0075", "de_de"));
	}

	@Test
	void clock() {
		assertEquals("0:07", UsageFormat.clock(7_900));
		assertEquals("1:42", UsageFormat.clock(102_000));
	}

	@Test
	void parsesBalancesTheWayPeopleTypeThem() {
		assertEquals(250_000, UsageFormat.parseAmount(Provider.CARTESIA, "250000").getAsDouble());
		assertEquals(250_000, UsageFormat.parseAmount(Provider.CARTESIA, "250,000").getAsDouble());
		assertEquals(250_000, UsageFormat.parseAmount(Provider.CARTESIA, "250 000 credits").getAsDouble());
		assertEquals(250_000, UsageFormat.parseAmount(Provider.CARTESIA, "250\u202f000").getAsDouble());
		assertEquals(42.75, UsageFormat.parseAmount(Provider.ASSEMBLYAI, "$42.75").getAsDouble());
		assertEquals(42.75, UsageFormat.parseAmount(Provider.ASSEMBLYAI, "42,75").getAsDouble());
		assertEquals(1234.5, UsageFormat.parseAmount(Provider.ASSEMBLYAI, "1,234.50").getAsDouble());
		assertFalse(UsageFormat.parseAmount(Provider.ASSEMBLYAI, "fifty").isPresent());
		assertFalse(UsageFormat.parseAmount(Provider.CARTESIA, "").isPresent());
		assertFalse(UsageFormat.parseAmount(Provider.CARTESIA, "-5").isPresent());
		assertFalse(UsageFormat.parseAmount(Provider.CARTESIA, null).isPresent());
	}
}
