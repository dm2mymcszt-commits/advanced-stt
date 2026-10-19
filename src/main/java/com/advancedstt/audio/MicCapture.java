package com.advancedstt.audio;

import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;
import org.lwjgl.openal.ALUtil;

import java.util.List;
import java.util.function.Consumer;

/**
 * Records the microphone through OpenAL, which Minecraft already uses for sound (the same way voice
 * chat mods do it). Delivers 100 ms chunks of 16 kHz mono 16-bit little-endian PCM on its own
 * thread. Nothing is ever written to disk.
 */
public final class MicCapture implements Microphone {
	public static final int SAMPLE_RATE = 16_000;
	/** 100 ms per chunk: AssemblyAI wants 50 to 1000 ms per message. */
	public static final int CHUNK_SAMPLES = SAMPLE_RATE / 10;
	private static final int FALLBACK_RATE = 48_000;
	private static final int POLL_MS = 20;

	private final String deviceName;
	private final Consumer<byte[]> sink;
	private volatile boolean running;
	private volatile float level;
	private Thread thread;
	private long device;
	private int deviceRate;

	/** @param deviceName OpenAL capture device, or null/empty for the system default */
	public MicCapture(String deviceName, Consumer<byte[]> sink) {
		this.deviceName = deviceName == null || deviceName.isBlank() ? null : deviceName;
		this.sink = sink;
	}

	/** Names of the capture devices OpenAL can see. */
	public static List<String> listDevices() {
		try {
			List<String> names = ALUtil.getStringList(0L, ALC11.ALC_CAPTURE_DEVICE_SPECIFIER);
			return names == null ? List.of() : List.copyOf(names);
		} catch (RuntimeException | LinkageError e) {
			return List.of();
		}
	}

	/** Opens the device and starts recording. Throws if no microphone can be opened. */
	@Override
	public synchronized void start() throws MicException {
		if (running) {
			return;
		}
		device = open(deviceName, SAMPLE_RATE);
		deviceRate = SAMPLE_RATE;
		if (device == 0L) {
			device = open(deviceName, FALLBACK_RATE);
			deviceRate = FALLBACK_RATE;
		}
		if (device == 0L && deviceName != null) {
			throw new MicException("The microphone \"" + deviceName + "\" could not be opened.");
		}
		if (device == 0L) {
			throw new MicException("No microphone could be opened.");
		}
		ALC11.alcCaptureStart(device);
		running = true;
		long handle = device;
		int rate = deviceRate;
		thread = new Thread(() -> loop(handle, rate), "advanced-stt-microphone");
		thread.setDaemon(true);
		thread.start();
	}

	/** Stops recording and closes the device. The last partial chunk is padded with silence. */
	@Override
	public synchronized void stop() {
		if (!running) {
			return;
		}
		running = false;
		Thread t = thread;
		thread = null;
		if (t != null) {
			try {
				t.join(1_000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		level = 0;
	}

	public boolean isRunning() {
		return running;
	}

	/** Loudness of the last chunk, 0 to 1. */
	@Override
	public float level() {
		return level;
	}

	private static long open(String name, int rate) {
		try {
			// One second of buffer: the thread reads it every 20 ms.
			return ALC11.alcCaptureOpenDevice(name, rate, AL10.AL_FORMAT_MONO16, rate);
		} catch (RuntimeException | LinkageError e) {
			return 0L;
		}
	}

	private void loop(long handle, int rate) {
		int factor = rate / SAMPLE_RATE; // 1, or 3 for 48 kHz
		short[] read = new short[rate]; // up to one second
		short[] chunk = new short[CHUNK_SAMPLES];
		int filled = 0;
		try {
			while (running) {
				int available = ALC10.alcGetInteger(handle, ALC11.ALC_CAPTURE_SAMPLES);
				// Keep whole groups of samples so 48 kHz divides cleanly.
				available -= available % factor;
				if (available <= 0) {
					sleep();
					continue;
				}
				available = Math.min(available, read.length);
				ALC11.alcCaptureSamples(handle, read, available);
				for (int i = 0; i < available; i += factor) {
					chunk[filled++] = factor == 1 ? read[i] : average(read, i, factor);
					if (filled == chunk.length) {
						emit(chunk, filled);
						filled = 0;
					}
				}
			}
			if (filled > 0) {
				java.util.Arrays.fill(chunk, filled, chunk.length, (short) 0);
				emit(chunk, chunk.length);
			}
		} catch (RuntimeException | LinkageError e) {
			running = false;
		} finally {
			try {
				ALC11.alcCaptureStop(handle);
				ALC11.alcCaptureCloseDevice(handle);
			} catch (RuntimeException | LinkageError ignored) {
				// already gone
			}
		}
	}

	private static short average(short[] samples, int start, int count) {
		int sum = 0;
		for (int i = 0; i < count; i++) {
			sum += samples[start + i];
		}
		return (short) (sum / count);
	}

	private void emit(short[] samples, int count) {
		byte[] pcm = new byte[count * 2];
		double energy = 0;
		for (int i = 0; i < count; i++) {
			short s = samples[i];
			pcm[i * 2] = (byte) s;
			pcm[i * 2 + 1] = (byte) (s >> 8);
			energy += (double) s * s;
		}
		level = (float) Math.min(1.0, Math.sqrt(energy / count) / 8_000.0);
		try {
			sink.accept(pcm);
		} catch (RuntimeException ignored) {
			// the session handles its own errors
		}
	}

	private static void sleep() {
		try {
			Thread.sleep(POLL_MS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/** The microphone could not be used. */
	public static final class MicException extends Exception {
		public MicException(String message) {
			super(message);
		}
	}

	/** For the settings screen: the default device name, if OpenAL reports one. */
	public static String defaultDeviceName() {
		try {
			String name = ALC10.alcGetString(0L, ALC11.ALC_CAPTURE_DEFAULT_DEVICE_SPECIFIER);
			return name == null ? "" : name;
		} catch (RuntimeException | LinkageError e) {
			return "";
		}
	}
}
