package com.advancedstt.usage;

import com.advancedstt.Provider;

import java.util.Locale;

/** Numbers for the usage screen. Units (credits, dollars) follow each service's own billing. */
public final class UsageFormat {
	private UsageFormat() {
	}

	/** An amount without its unit word: "1,234" credits, or "$0.0075". */
	public static String number(Provider provider, double value) {
		double v = Math.max(0, value);
		return switch (provider) {
			case CARTESIA -> String.format(Locale.ROOT, "%,.0f", v);
			case ASSEMBLYAI -> {
				if (v > 0 && v < 0.01) {
					yield String.format(Locale.ROOT, "$%.4f", v);
				}
				yield v < 10 ? String.format(Locale.ROOT, "$%.3f", v) : String.format(Locale.ROOT, "$%,.2f", v);
			}
		};
	}

	/** An amount with its unit, for logs: "1,234 credits" or "$0.0075". */
	public static String amount(Provider provider, double value) {
		return provider == Provider.CARTESIA ? number(provider, value) + " credits" : number(provider, value);
	}

	/** "12 s", "2 min", "6 min 51 s", "1 h", "2 h 05 min". */
	public static String duration(double seconds) {
		long total = Math.max(0, Math.round(seconds));
		long hours = total / 3600;
		long minutes = (total % 3600) / 60;
		long secs = total % 60;
		if (hours > 0) {
			return minutes == 0 ? hours + " h" : String.format(Locale.ROOT, "%d h %02d min", hours, minutes);
		}
		if (minutes > 0) {
			return secs == 0 ? minutes + " min" : String.format(Locale.ROOT, "%d min %02d s", minutes, secs);
		}
		return secs + " s";
	}

	/**
	 * Writes a number from this class the way the game's language does. French: "100 000" credits
	 * and "0,45 $" (in English "100,000" could be misread as a hundred in French).
	 */
	public static String localize(String number, String languageCode) {
		if (languageCode == null || !languageCode.startsWith("fr")) {
			return number;
		}
		boolean dollars = number.startsWith("$");
		String digits = (dollars ? number.substring(1) : number).replace(',', ' ').replace('.', ',');
		return dollars ? digits + " $" : digits;
	}

	/** A rate as short as it can be written: "3", "0.45", "2.5". */
	public static String plain(double value) {
		return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
	}

	/**
	 * Reads a balance typed by the player. Credits are whole numbers ("250 000", "250,000"); dollars
	 * may use a dot or a comma for cents ("42.75", "42,75"). Returns empty for anything else.
	 */
	public static java.util.OptionalDouble parseAmount(Provider provider, String text) {
		if (text == null) {
			return java.util.OptionalDouble.empty();
		}
		String s = text.strip().toLowerCase(Locale.ROOT)
				.replace("credits", "").replace("crédits", "").replace("$", "").replace("usd", "")
				.replaceAll("[\\s\u00a0\u202f']", "");
		if (s.isEmpty()) {
			return java.util.OptionalDouble.empty();
		}
		if (provider == Provider.CARTESIA) {
			s = s.replace(",", "").replace(".", "");
		} else if (s.contains(",") && s.contains(".")) {
			s = s.replace(",", "");
		} else {
			s = s.replace(",", ".");
		}
		if (!s.matches("\\d+(\\.\\d+)?")) {
			return java.util.OptionalDouble.empty();
		}
		try {
			double value = Double.parseDouble(s);
			return value > 1e12 ? java.util.OptionalDouble.empty() : java.util.OptionalDouble.of(value);
		} catch (NumberFormatException e) {
			return java.util.OptionalDouble.empty();
		}
	}

	/** "0:07", "1:42" for the microphone timer. */
	public static String clock(long millis) {
		long total = Math.max(0, millis / 1000);
		return String.format(Locale.ROOT, "%d:%02d", total / 60, total % 60);
	}
}
