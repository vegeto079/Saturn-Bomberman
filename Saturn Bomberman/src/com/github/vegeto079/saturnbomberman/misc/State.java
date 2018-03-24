package com.github.vegeto079.saturnbomberman.misc;

import java.awt.Image;
import java.awt.image.BufferedImage;

import com.github.vegeto079.saturnbomberman.misc.Pictures;

import com.github.vegeto079.ngcommontools.main.Game;

public class State {
	private StateEnum currentState = StateEnum.LOADING;
	private StateEnum nextStateAfterFade = StateEnum.NULL;
	private Image imageToFade = null;

	public enum StateEnum {
		NULL, LOADING, DEBUG, FADE_OUT, MAIN_MENU, NETWORKING, CONTROLS, MATCH_SELECTION, STAGE, GOING_TO_STAGE, END_OF_MATCH;
	}

	public State(StateEnum state, Game game) {
		set(state, game);
	}

	public boolean is(String stateName) {
		return getName().replace("_", " ").equalsIgnoreCase(stateName);
	}

	public boolean is(StateEnum state) {
		return getName().equals(state.name());
	}

	public boolean equals(String stateName) {
		return is(stateName);
	}

	public boolean equals(StateEnum state) {
		return is(state);
	}

	public boolean equalsOneOf(String... states) {
		for (int i = 0; i < states.length; i++)
			if (this.is(states[i]))
				return true;
		return false;
	}

	public boolean equalsOneOf(StateEnum... states) {
		for (int i = 0; i < states.length; i++)
			if (this.is(states[i]))
				return true;
		return false;
	}

	public String getName() {
		return get().name();
	}

	public StateEnum get() {
		return currentState;
	}

	public StateEnum getNextStateAfterFade() {
		return nextStateAfterFade;
	}

	public void setToNextStateAfterFade() {
		currentState = nextStateAfterFade;
		setNextStateAfterFade(StateEnum.NULL, null);
	}

	public boolean set(String stateName, Game game) {
		for (int i = 0; i < StateEnum.values().length; i++)
			if (StateEnum.values()[i].name().replace("_", " ")
					.equalsIgnoreCase(stateName)) {
				set(StateEnum.values()[i], game);
				return true;
			}
		for (int i = 0; i < StateEnum.values().length; i++)
			if (StateEnum.values()[i].name().replace("_", " ").toLowerCase()
					.contains(stateName.replace("_", " ").toLowerCase())) {
				set(StateEnum.values()[i], game);
				return true;
			}
		return false;
	}

	public void set(StateEnum state, Game game) {
		currentState = state;
		game.resetFps();
		game.resetUps();
	}

	public void setNextStateAfterFade(StateEnum state, Game game) {
		BufferedImage bufImage = new BufferedImage(game.getWidth(),
				game.getHeight(), BufferedImage.TYPE_INT_ARGB);
		game.paint(bufImage.createGraphics());
		Image newImageToFade = Pictures.toImage(bufImage);
		nextStateAfterFade = state;
		this.imageToFade = newImageToFade;
		game.tickcounter = 0;
		game.fadecounter = 0;
		game.fadecounterenabled = 1;
		game.resetFps();
		game.resetUps();
		set(StateEnum.FADE_OUT, game);
		bufImage.flush();
	}

	public void setImageToFade(Game game) {
		BufferedImage bufImage = new BufferedImage(game.getWidth(),
				game.getHeight(), BufferedImage.TYPE_INT_RGB);
		game.paint(bufImage.createGraphics());
		Image imageToFade = Pictures.toImage(bufImage);
		setImageToFade(imageToFade);
	}

	public Image getImageToFade() {
		return imageToFade;
	}

	public void setImageToFade(Image imageToFade) {
		this.imageToFade = imageToFade;
	}

	public static int getIndex(StateEnum state) {
		StateEnum[] all = StateEnum.values();
		for (int i = 0; i < all.length; i++)
			if (all[i].name().equals(state.name()))
				return i;
		return -1;
	}

	public int getIndex() {
		return getIndex(get());
	}

	public static String getName(StateEnum state) {
		return state.name();
	}

	public static String getName(int index) {
		return getName(getState(index));
	}

	public static StateEnum getState(int index) {
		return StateEnum.values()[index];
	}

	public void setFromIndex(int index) {
		currentState = getState(index);
	}
}
