package com.github.vegeto079.saturnbomberman.misc;

import java.awt.Button;
import java.awt.event.KeyEvent;

import com.github.vegeto079.ngcommontools.main.JoystickHandler.JoystickListener;
import com.github.vegeto079.saturnbomberman.main.MainBomberman;


/**
 * Custom {@link JoystickListener} for {@link MainBomberman} joystick
 * implementation.
 * 
 * @author Nathan
 * @version 1.0: Started tracking version.
 * @version 1.1: {@link #key(boolean, int)} added, all key pressed route here
 *          now.
 */
public class BombermanJoystickListener extends JoystickListener {
	MainBomberman game = null;

	public BombermanJoystickListener(MainBomberman game) {
		this.game = game;
	}

	@SuppressWarnings("deprecation")
	public void key(boolean press, int key) {
		Button a = new Button("null");
		KeyEvent keyEvent;
		keyEvent = new KeyEvent(a, 1, 20, 1, key);
		if (press)
			game.keyPressed(keyEvent);
		else
			game.keyReleased(keyEvent);
	}

	public void pressedUp(int joystick, boolean pressed) {
		if (pressed)
			if (joystick == 0 || joystick > 1)
				key(true, KeyEvent.VK_UP);
			else
				key(true, KeyEvent.VK_W);
		else if (joystick == 0 || joystick > 1)
			key(false, KeyEvent.VK_UP);
		else
			key(false, KeyEvent.VK_W);
	}

	public void pressedDown(int joystick, boolean pressed) {
		if (pressed)
			if (joystick == 0 || joystick > 1)
				key(true, KeyEvent.VK_DOWN);
			else
				key(true, KeyEvent.VK_S);
		else if (joystick == 0 || joystick > 1)
			key(false, KeyEvent.VK_DOWN);
		else
			key(false, KeyEvent.VK_S);
	}

	public void pressedLeft(int joystick, boolean pressed) {
		if (pressed)
			if (joystick == 0 || joystick > 1)
				key(true, KeyEvent.VK_LEFT);
			else
				key(true, KeyEvent.VK_A);
		else if (joystick == 0 || joystick > 1)
			key(false, KeyEvent.VK_LEFT);
		else
			key(false, KeyEvent.VK_A);
	}

	public void pressedRight(int joystick, boolean pressed) {
		if (pressed)
			if (joystick == 0 || joystick > 1)
				key(true, KeyEvent.VK_RIGHT);
			else
				key(true, KeyEvent.VK_D);
		else if (joystick == 0 || joystick > 1)
			key(false, KeyEvent.VK_RIGHT);
		else
			key(false, KeyEvent.VK_D);
	}

	public void pressedL1(int joystick, boolean pressed) {
		if (pressed)
			if (joystick == 0 || joystick > 1)
				key(true, KeyEvent.VK_V);
			else
				key(true, KeyEvent.VK_Q);
		else if (joystick == 0 || joystick > 1)
			key(false, KeyEvent.VK_V);
		else
			key(false, KeyEvent.VK_Q);
	}

	public void pressedR1(int joystick, boolean pressed) {
		if (pressed)
			if (joystick == 0 || joystick > 1)
				key(true, KeyEvent.VK_B);
			else
				key(true, KeyEvent.VK_E);
		else if (joystick == 0 || joystick > 1)
			key(false, KeyEvent.VK_B);
		else
			key(false, KeyEvent.VK_E);
	}

	public void pressedL2(int joystick, boolean pressed) {
		if (pressed)
			if (joystick == 0 || joystick > 1)
				key(true, KeyEvent.VK_V);
			else
				key(true, KeyEvent.VK_Q);
		else if (joystick == 0 || joystick > 1)
			key(false, KeyEvent.VK_V);
		else
			key(false, KeyEvent.VK_Q);
	}

	public void pressedR2(int joystick, boolean pressed) {
		if (pressed)
			if (joystick == 0 || joystick > 1)
				key(true, KeyEvent.VK_B);
			else
				key(true, KeyEvent.VK_E);
		else if (joystick == 0 || joystick > 1)
			key(false, KeyEvent.VK_B);
		else
			key(false, KeyEvent.VK_E);
	}

	public void pressedButton(int joystick, int button, boolean pressed) {
		if (pressed) {
			if (button == 1)
				if (joystick == 0 || joystick > 1)
					key(true, KeyEvent.VK_C);
				else
					key(true, KeyEvent.VK_3);
			else if (button == 2)
				if (joystick == 0 || joystick > 1)
					key(true, KeyEvent.VK_X);
				else
					key(true, KeyEvent.VK_1);
			else if (button == 3)
				if (joystick == 0 || joystick > 1)
					key(true, KeyEvent.VK_Z);
				else
					key(true, KeyEvent.VK_2);
			else if (button == 5)
				if (joystick == 0 || joystick > 1)
					key(true, KeyEvent.VK_V);
				else
					key(true, KeyEvent.VK_Q);
			else if (button == 6)
				if (joystick == 0 || joystick > 1)
					key(true, KeyEvent.VK_B);
				else
					key(true, KeyEvent.VK_E);
			else if (button == 7)
				if (joystick == 0 || joystick > 1)
					key(true, KeyEvent.VK_ESCAPE);
				else
					key(true, KeyEvent.VK_ESCAPE);
		} else {
			if (button == 1)
				if (joystick == 0 || joystick > 1)
					key(false, KeyEvent.VK_C);
				else
					key(false, KeyEvent.VK_3);
			else if (button == 2)
				if (joystick == 0 || joystick > 1)
					key(false, KeyEvent.VK_X);
				else
					key(false, KeyEvent.VK_1);
			else if (button == 3)
				if (joystick == 0 || joystick > 1)
					key(false, KeyEvent.VK_Z);
				else
					key(false, KeyEvent.VK_2);
			else if (button == 5)
				if (joystick == 0 || joystick > 1)
					key(false, KeyEvent.VK_V);
				else
					key(false, KeyEvent.VK_Q);
			else if (button == 6)
				if (joystick == 0 || joystick > 1)
					key(false, KeyEvent.VK_B);
				else
					key(false, KeyEvent.VK_E);
			else if (button == 7)
				if (joystick == 0 || joystick > 1)
					key(false, KeyEvent.VK_ESCAPE);
				else
					key(false, KeyEvent.VK_ESCAPE);
		}
	}

}
