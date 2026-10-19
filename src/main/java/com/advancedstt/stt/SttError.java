package com.advancedstt.stt;

/**
 * Something went wrong with a transcription session. {@code detail} is text from the service or
 * the network layer; it never contains the API key.
 */
public record SttError(Kind kind, String detail) {
	public enum Kind {
		/** The service refused the API key (missing, wrong or revoked). */
		INVALID_KEY,
		/** The account has no credits or balance left. */
		NO_CREDITS,
		/** Too many requests or sessions at once. */
		RATE_LIMITED,
		/** No connection to the service (offline, DNS, firewall, timeout). */
		NETWORK,
		/** The audio could not be sent fast enough; the connection is too slow. */
		AUDIO_BACKLOG,
		/** Any other error reported by the service. */
		SERVICE
	}

	public SttError {
		detail = detail == null ? "" : detail;
	}

	/**
	 * Best guess of the kind from an error text sent by a service. Used when the service only gives
	 * a message, not a code.
	 */
	public static Kind classify(String message) {
		String m = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
		if (m.contains("unauthor") || m.contains("api key") || m.contains("api_key") || m.contains("invalid key")
				|| m.contains("authentication") || m.contains("forbidden") || m.contains("invalid token")) {
			return Kind.INVALID_KEY;
		}
		if (m.contains("insufficient") || m.contains("balance") || m.contains("credit") || m.contains("payment")
				|| m.contains("quota") || m.contains("billing")) {
			return Kind.NO_CREDITS;
		}
		if (m.contains("rate limit") || m.contains("too many") || m.contains("concurren") || m.contains("throttl")) {
			return Kind.RATE_LIMITED;
		}
		return Kind.SERVICE;
	}

	/** Kind for an HTTP status from a refused connection. */
	public static Kind fromHttpStatus(int status) {
		return switch (status) {
			case 401, 403 -> Kind.INVALID_KEY;
			case 402 -> Kind.NO_CREDITS;
			case 429 -> Kind.RATE_LIMITED;
			default -> Kind.SERVICE;
		};
	}
}
