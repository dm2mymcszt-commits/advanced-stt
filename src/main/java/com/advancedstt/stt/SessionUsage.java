package com.advancedstt.stt;

import com.advancedstt.Provider;

/**
 * What one session used, measured by the mod.
 *
 * @param audioSeconds      seconds of audio sent (Cartesia bills this, silence included)
 * @param connectionSeconds seconds the connection was open, measured locally
 * @param reportedSeconds   session length reported by the service at the end, or -1 if it did not
 *                          report one (AssemblyAI bills the time the connection stays open)
 */
public record SessionUsage(Provider provider, double audioSeconds, double connectionSeconds, double reportedSeconds) {
	/** Seconds this session is billed for, as far as the mod can tell. */
	public double billableSeconds() {
		return switch (provider) {
			case CARTESIA -> audioSeconds;
			case ASSEMBLYAI -> reportedSeconds >= 0 ? reportedSeconds : connectionSeconds;
		};
	}
}
