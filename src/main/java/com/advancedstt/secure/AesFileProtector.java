package com.advancedstt.secure;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Set;

/**
 * AES-256-GCM with a random key kept in its own file, readable only by the current user where the
 * file system allows it. Used when Windows data protection is not available (macOS, Linux). This
 * keeps keys out of plain text, but anyone who can read both files as this user can read the keys.
 */
final class AesFileProtector implements Protector {
	private static final int KEY_BYTES = 32;
	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;
	private static final SecureRandom RANDOM = new SecureRandom();

	private final Path keyFile;

	AesFileProtector(Path keyFile) {
		this.keyFile = keyFile;
	}

	@Override
	public String id() {
		return "aes-gcm";
	}

	@Override
	public CredentialStore.Protection protection() {
		return CredentialStore.Protection.LOCAL_FILE;
	}

	@Override
	public byte[] protect(byte[] plain) throws IOException {
		try {
			byte[] iv = new byte[IV_BYTES];
			RANDOM.nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(true), "AES"), new GCMParameterSpec(TAG_BITS, iv));
			byte[] sealed = cipher.doFinal(plain);
			return ByteBuffer.allocate(IV_BYTES + sealed.length).put(iv).put(sealed).array();
		} catch (GeneralSecurityException e) {
			throw new IOException("Encryption failed", e);
		}
	}

	@Override
	public byte[] unprotect(byte[] data) throws IOException {
		if (data.length <= IV_BYTES) {
			throw new IOException("Damaged key data");
		}
		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(false), "AES"),
					new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
			return cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES);
		} catch (GeneralSecurityException e) {
			throw new IOException("Decryption failed", e);
		}
	}

	private byte[] key(boolean create) throws IOException {
		if (Files.isRegularFile(keyFile)) {
			byte[] key = Files.readAllBytes(keyFile);
			if (key.length == KEY_BYTES) {
				return key;
			}
			if (!create) {
				throw new IOException("Damaged key file");
			}
		} else if (!create) {
			throw new IOException("Key file missing");
		}
		byte[] key = new byte[KEY_BYTES];
		RANDOM.nextBytes(key);
		Files.createDirectories(keyFile.getParent());
		Files.deleteIfExists(keyFile);
		createOwnerOnly(keyFile);
		Files.write(keyFile, key);
		return key;
	}

	/** Creates an empty file that only the owner can read and write, where the OS supports it. */
	static void createOwnerOnly(Path file) throws IOException {
		if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
			Set<PosixFilePermission> ownerOnly = PosixFilePermissions.fromString("rw-------");
			Files.createFile(file, PosixFilePermissions.asFileAttribute(ownerOnly));
		} else {
			Files.createFile(file);
		}
	}
}
