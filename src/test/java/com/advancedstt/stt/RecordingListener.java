package com.advancedstt.stt;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Records everything a session reports, from whatever thread it reports it on. */
final class RecordingListener implements SttListener {
	final CountDownLatch opened = new CountDownLatch(1);
	final CountDownLatch closed = new CountDownLatch(1);
	final List<String> events = new CopyOnWriteArrayList<>();
	final List<SttError> errors = new CopyOnWriteArrayList<>();
	final AtomicInteger closeCount = new AtomicInteger();
	volatile SessionUsage usage;

	@Override
	public void onOpen() {
		events.add("open");
		opened.countDown();
	}

	@Override
	public void onTurnUpdate(int turn, String text) {
		events.add("update " + turn + " " + text);
	}

	@Override
	public void onTurnEnd(int turn, String text) {
		events.add("end " + turn + " " + text);
	}

	@Override
	public void onError(SttError error) {
		errors.add(error);
	}

	@Override
	public void onClosed(SessionUsage usage) {
		this.usage = usage;
		closeCount.incrementAndGet();
		closed.countDown();
	}

	boolean awaitClosed(long seconds) throws InterruptedException {
		boolean done = closed.await(seconds, TimeUnit.SECONDS);
		Thread.sleep(150); // give a wrong second close a chance to show up
		return done;
	}
}
