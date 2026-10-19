package com.advancedstt.usage;

import com.advancedstt.Provider;
import com.advancedstt.config.JsonFiles;
import com.advancedstt.config.SttConfig;
import com.advancedstt.stt.SessionUsage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * The mod's own record of what it used. Neither service lets a normal API key read the real
 * balance, so everything here is an ESTIMATE: the balance the player typed in, minus what the mod
 * measured since then, priced with the rates in the settings.
 *
 * <p>Cartesia amounts are credits; AssemblyAI amounts are US dollars.
 */
public final class UsageTracker {
	public static final class ProviderUsage {
		/** Balance the player typed in, or -1 if they never did. */
		public double startingBalance = -1;
		public long balanceSetAtMs;
		/** Estimated spend since the balance was typed in (credits or dollars). */
		public double usedSinceBalance;
		/** Billable seconds measured, all time. */
		public double totalSeconds;
		/** Estimated spend, all time (credits or dollars). */
		public double totalUsed;
		public long sessions;
	}

	public static final class Data {
		public int version = 1;
		public Map<String, ProviderUsage> providers = new LinkedHashMap<>();
	}

	private final Path file;
	private Data data = new Data();

	public UsageTracker(Path file) {
		this.file = file;
	}

	public UsageTracker load() {
		Data loaded = JsonFiles.read(file, Data.class);
		data = loaded == null ? new Data() : loaded;
		if (data.providers == null) {
			data.providers = new LinkedHashMap<>();
		}
		return this;
	}

	public void save() throws IOException {
		JsonFiles.write(file, data);
	}

	public ProviderUsage get(Provider provider) {
		return data.providers.computeIfAbsent(provider.id(), id -> new ProviderUsage());
	}

	/** Estimated cost of {@code seconds} of billable time, in the provider's unit. */
	public static double cost(Provider provider, double seconds, SttConfig config) {
		return switch (provider) {
			case CARTESIA -> seconds * config.cartesiaCreditsPerSecond;
			case ASSEMBLYAI -> seconds / 3600.0 * config.assemblyAiDollarsPerHour;
		};
	}

	/** Adds a finished session. Returns its estimated cost. */
	public double record(SessionUsage usage, SttConfig config) {
		double seconds = Math.max(0, usage.billableSeconds());
		double cost = cost(usage.provider(), seconds, config);
		ProviderUsage p = get(usage.provider());
		p.totalSeconds += seconds;
		p.totalUsed += cost;
		p.sessions++;
		if (p.startingBalance >= 0) {
			p.usedSinceBalance += cost;
		}
		return cost;
	}

	public void setStartingBalance(Provider provider, double balance, long nowMs) {
		ProviderUsage p = get(provider);
		p.startingBalance = Math.max(0, balance);
		p.balanceSetAtMs = nowMs;
		p.usedSinceBalance = 0;
	}

	public void clearStartingBalance(Provider provider) {
		ProviderUsage p = get(provider);
		p.startingBalance = -1;
		p.balanceSetAtMs = 0;
		p.usedSinceBalance = 0;
	}

	/** Estimated balance left, if the player typed in a starting balance. */
	public OptionalDouble estimatedRemaining(Provider provider) {
		ProviderUsage p = get(provider);
		if (p.startingBalance < 0) {
			return OptionalDouble.empty();
		}
		return OptionalDouble.of(Math.max(0, p.startingBalance - p.usedSinceBalance));
	}
}
