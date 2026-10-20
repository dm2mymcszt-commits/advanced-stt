package com.advancedstt;

import com.advancedstt.client.AdvancedStt;
import net.fabricmc.api.ClientModInitializer;

/** Fabric entry point. The mod only runs on the client. */
public final class AdvancedSttClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		AdvancedStt.init();
	}
}
