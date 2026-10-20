package com.advancedstt.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/** A slider for a number between two limits, in fixed steps. */
final class ValueSlider extends AbstractSliderButton {
	private final double min;
	private final double max;
	private final double step;
	private final DoubleFunction<Component> label;
	private final DoubleConsumer onChange;

	ValueSlider(int width, double min, double max, double step, double initial, DoubleFunction<Component> label,
			DoubleConsumer onChange) {
		super(0, 0, width, 20, Component.empty(), (Math.clamp(initial, min, max) - min) / (max - min));
		this.min = min;
		this.max = max;
		this.step = step;
		this.label = label;
		this.onChange = onChange;
		updateMessage();
	}

	double current() {
		double raw = min + value * (max - min);
		double stepped = Math.round((raw - min) / step) * step + min;
		return Math.clamp(stepped, min, max);
	}

	@Override
	protected void updateMessage() {
		if (label != null) {
			setMessage(label.apply(current()));
		}
	}

	@Override
	protected void applyValue() {
		if (onChange != null) {
			onChange.accept(current());
		}
	}
}
