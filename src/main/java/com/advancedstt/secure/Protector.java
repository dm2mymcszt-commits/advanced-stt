package com.advancedstt.secure;

import java.io.IOException;

/** Turns a secret into bytes that are safe to write to disk, and back. */
interface Protector {
	/** Short id written in the file, so a file made one way is never read the other way. */
	String id();

	byte[] protect(byte[] plain) throws IOException;

	byte[] unprotect(byte[] sealed) throws IOException;

	CredentialStore.Protection protection();
}
