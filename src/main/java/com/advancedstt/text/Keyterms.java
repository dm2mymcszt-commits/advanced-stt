package com.advancedstt.text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The "words to listen for" list: names and game words the services should spell right. */
public final class Keyterms {
	public static final int MAX_TERMS = 100;

	private Keyterms() {
	}

	/** Splits on commas and new lines, trims, drops blanks and repeats (case-insensitive). */
	public static List<String> parse(String text) {
		Map<String, String> unique = new LinkedHashMap<>();
		if (text != null) {
			for (String part : text.split("[,\\n\\r;]")) {
				String term = part.strip().replaceAll("\\s+", " ");
				if (!term.isEmpty()) {
					unique.putIfAbsent(term.toLowerCase(Locale.ROOT), term);
				}
			}
		}
		List<String> terms = new ArrayList<>(unique.values());
		return terms.size() > MAX_TERMS ? List.copyOf(terms.subList(0, MAX_TERMS)) : List.copyOf(terms);
	}

	public static String format(List<String> terms) {
		return String.join(", ", terms);
	}
}
