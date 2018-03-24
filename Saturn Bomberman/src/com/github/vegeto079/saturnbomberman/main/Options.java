package com.github.vegeto079.saturnbomberman.main;

import java.awt.event.KeyEvent;

/**
 * 
 * @author Nathan
 * @version 1.0: Started tracking version.
 * @version 1.1: Added {@link Multiplayer}.
 */
public class Options {
	public int[] players = new int[8];
	public Controls[] controllers = new Controls[2];
	public Multiplayer multiplayer = new Multiplayer();

	public boolean battleOptionsFinished = false;
	public boolean rulesOptionsFinished = false;
	public boolean bombersOptionsFinished = false;
	public boolean teamOptionsFinished = false;

	public boolean wideMode = false;
	public boolean teamMode = false;
	public int numberOfBattles = 3;
	// public double time = 0.1d;//set to have quick game
	public double time = 3;
	public boolean shuffle = false;
	public boolean noDraw = true;
	public boolean devil = false;
	public boolean madBomber = false;
	public boolean bonusGame = false;
	// public boolean canBombSelf = false;
	public boolean canBombSelf = true;
	public int comLevel = 1;
	public boolean kickedBombsMoveOtherBombs = false;

	public Options() {
		players[0] = 1;
		players[1] = 1;
		players[2] = 2;
		players[3] = 2;
		players[4] = 2;
		players[5] = 2;
		players[6] = 2;
		players[7] = 2;
		controllers[0] = new Controls(1);
		controllers[1] = new Controls(2);
	}

	public static class Controls {
		public int upButton = -1;
		public int downButton = -1;
		public int leftButton = -1;
		public int rightButton = -1;
		public int AButton = -1;
		public int BButton = -1;
		public int CButton = -1;
		public int LButton = -1;
		public int RButton = -1;

		public Controls(int controller) {
			if (controller == 1) {
				upButton = KeyEvent.VK_UP;
				downButton = KeyEvent.VK_DOWN;
				leftButton = KeyEvent.VK_LEFT;
				rightButton = KeyEvent.VK_RIGHT;
				AButton = KeyEvent.VK_Z;
				BButton = KeyEvent.VK_X;
				CButton = KeyEvent.VK_C;
				LButton = KeyEvent.VK_V;
				RButton = KeyEvent.VK_B;
			} else if (controller == 2) {
				upButton = KeyEvent.VK_W;
				downButton = KeyEvent.VK_S;
				leftButton = KeyEvent.VK_A;
				rightButton = KeyEvent.VK_D;
				AButton = KeyEvent.VK_1;
				BButton = KeyEvent.VK_2;
				CButton = KeyEvent.VK_3;
				LButton = KeyEvent.VK_Q;
				RButton = KeyEvent.VK_E;
			}
		}

		public int[] getButtonArray() {
			return new int[] { AButton, BButton, CButton, upButton, downButton,
					leftButton, rightButton, LButton, RButton };
		}

		public static String[] getStringArray() {
			return new String[] { "A", "B", "C", "UP", "DOWN", "LEFT", "RIGHT", "L", "R" };
		}
	}

	public class Multiplayer {
		public String username = null;
		public boolean useP2P = false;
		public int maxPlayers = 8;
		public int localPlayers = 1;
		public boolean useAIIntermediaryForAI = false;
		public boolean useAIIntermediaryForPlayers = false;
	}
}
