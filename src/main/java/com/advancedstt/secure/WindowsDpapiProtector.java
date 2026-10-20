package com.advancedstt.secure;

import com.sun.jna.platform.win32.Crypt32Util;

import java.io.IOException;

/**
 * Windows Data Protection (DPAPI) through JNA, which Minecraft already ships. The data can only be
 * decrypted by the same Windows account on the same machine.
 */
final class WindowsDpapiProtector implements Protector {
	@Override
	public String id() {
		return "dpapi";
	}

	@Override
	public byte[] protect(byte[] plain) throws IOException {
		try {
			return Crypt32Util.cryptProtectData(plain);
		} catch (RuntimeException | LinkageError e) {
			throw new IOException("Windows data protection failed", e);
		}
	}

	@Override
	public byte[] unprotect(byte[] sealed) throws IOException {
		try {
			return Crypt32Util.cryptUnprotectData(sealed);
		} catch (RuntimeException | LinkageError e) {
			throw new IOException("Windows data protection failed", e);
		}
	}

	@Override
	public CredentialStore.Protection protection() {
		return CredentialStore.Protection.WINDOWS_ACCOUNT;
	}

	/** True if DPAPI works here (round trip of a test value). */
	static boolean available() {
		try {
			WindowsDpapiProtector p = new WindowsDpapiProtector();
			byte[] probe = {1, 2, 3, 4};
			return java.util.Arrays.equals(probe, p.unprotect(p.protect(probe)));
		} catch (IOException | RuntimeException | LinkageError e) {
			return false;
		}
	}
}
