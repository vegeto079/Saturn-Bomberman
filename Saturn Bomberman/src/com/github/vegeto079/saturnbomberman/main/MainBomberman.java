package com.github.vegeto079.saturnbomberman.main;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.KeyEvent;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;

import javax.imageio.ImageIO;

import com.github.vegeto079.saturnbomberman.main.Character.Animation;
import com.github.vegeto079.saturnbomberman.misc.BombermanJoystickListener;
import com.github.vegeto079.saturnbomberman.misc.Constants;
import com.github.vegeto079.saturnbomberman.misc.Draw;
import com.github.vegeto079.saturnbomberman.misc.Pictures;
import com.github.vegeto079.saturnbomberman.misc.State;
import com.github.vegeto079.saturnbomberman.misc.State.StateEnum;
import com.github.vegeto079.saturnbomberman.networking.Network;
import com.github.vegeto079.saturnbomberman.objects.Player;
import com.github.vegeto079.saturnbomberman.objects.Weight;
import com.github.vegeto079.saturnbomberman.objects.Bomb.BombType;
import com.github.vegeto079.saturnbomberman.objects.Player.PlayerComparator;

import com.github.vegeto079.ngcommontools.main.*;
import com.github.vegeto079.ngcommontools.main.Logger.*;
import com.github.vegeto079.ngcommontools.audio.*;
import com.github.vegeto079.ngcommontools.networking.*;

//TODO: Known bugs:
//Sometimes the 'we're dead' message doesn't get sent over network and they stay alive forever

//TODO: Features:
//When using network, use AI as an intermediate for all networked players. Meaning, in the time in between actual positions given from the clients, the AI plays for them (MINUS doing actions like setting bombs, kicking things, etc). This way they move more fluid. Should take into account the direction headed by the player, requiring the AI to move somewhere in that direction. By the time we get another position update from the client, the AI won't have moved much, but it should look a little better. Could make this an option, since it could ruin metagame play.

/**
 * Saturn Bomberman
 * 
 * @author Nathan
 * @version 1.0: Started tracking version.
 * @version 1.01: Added p1isUsingController and p2isUsingController to determine
 *          whether or not to allow line bombs.
 * @version 1.02: {@link #keyPressed(int)} now will return without pressing a
 *          key if the key is already in {@link #keysPressed}. This is to
 *          prevent the OS from sending many key presses when the key is held
 *          down. Subsequently, p1isUsingController and p2isUsingController
 *          variables have been removed, as their use was to exclude keyboard
 *          players from using line bombs due to the OS's multi-presses.
 * @version 1.03: Time we wait until we switch from the stage to end of match
 *          {@link State}s changed from 7.5sec to 10sec to accommodate the
 *          2.5sec wait in saying "I won!" and playing the winning animation at
 *          endgame.
 * @version 1.04: End-game screen where we resize the images no longer has the
 *          colors changed to the appropriate gray - that was taking a lot of
 *          CPU time due to getRGB() and setRGB(). There's separate images for
 *          that part I still need to get anyway, so this is just temporary.
 * @version 1.041: {@link #tickMainMenu(Graphics2D)} now doesn't run until
 *          {@link #tickMainMenu()} has run once, so we don't flash the
 *          beginning screen before {@link #fadeIn()} has run and properly set
 *          the values.
 * @version 1.05: Added {@link #currentTimeMillis()} and changed all
 *          {@link System#currentTimeMillis()} references to it.
 */
@SuppressWarnings("serial")
public class MainBomberman extends Game {
	MainBomberman ourGame = null;
	public Pictures pictures = null;
	public Draw draw = null;
	public State state = new State(StateEnum.NULL, this);
	MusicHandler music = null;
	public SoundHandler sound = null;
	public Options options = null;
	public ArrayList<Player> players = null;
	public Stage stage = null;
	public Point mouseLocation = new Point(-1, -1);
	public JoystickHandler joystickHandler = null;
	public Network network = null;

	RenderingHints renderingHints = new RenderingHints(RenderingHints.KEY_RENDERING,
			RenderingHints.VALUE_RENDER_QUALITY);

	public Font defaultFont = null;
	public Font font = null;

	public int benchmarkStatus = -1;
	public ArrayList<Integer> benchmarkResults = new ArrayList<Integer>();
	public boolean debug = false;
	public boolean loading = true;

	public String[] temp = new String[] { "", "" };
	public long[] counter = new long[] { 0, 0, 0, 0, 0, 0, 0, 0 };

	private final int HIGHEST_FADE_COUNTER = 150;
	private final int LOWEST_FADE_COUNTER = 0;
	private float opacity = 1;

	public int chatButton = KeyEvent.VK_T;

	private Thread picturesThread = null;

	public ArrayList<com.github.vegeto079.saturnbomberman.objects.Object> objects = new ArrayList<com.github.vegeto079.saturnbomberman.objects.Object>();

	public boolean mousePressed = false;
	public ArrayList<Integer> keysPressed = new ArrayList<Integer>();

	public boolean freezeAI = false;

	public MainBomberman(Logger logger, String[] args, int ticksPerSecond, int paintTicksPerSecond, String title,
			int width, int height) {
		super(logger, args, ticksPerSecond, paintTicksPerSecond, title, width, height);
		draw = new Draw(this);
		state.set(StateEnum.LOADING, this);
		ourGame = this;
		ourGame.startThread();
	}

	public static void main(String[] args) {
		new MainBomberman(new Logger(false, false, true, true), args, 60, 70, "Bomberman", 640, 448);
	}

	/**
	 * @return {@link System#currentTimeMillis()} if {@link Network#client} is
	 *         <b>null</b>. Otherwise, {@link Client#currentTimeMillis()}.
	 */
	public long currentTimeMillis() {
		if (network == null || network.client == null)
			return System.currentTimeMillis();
		else
			return network.client.currentTimeMillis();
	}

	public void tickLoading(Graphics2D g) {
		try {
			if (defaultFont != null)
				g.setFont(defaultFont);
			draw.textCenteredWithTexturedImage(g, Color.RED, false, getHeight() / 2 - 27 / 2, "LOADING...");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void tickLoading() {
		if (pictures != null && pictures.mainMenu.size() == 7 && !pictures.initialized) {
			counter[7] = -1;
			state.set(StateEnum.MAIN_MENU, this);
		}
	}

	public void tickDebug(Graphics2D g) {
		draw.whiteRectangle(g, -5, -5, getWidth() + 10, getHeight() + 10);
		if (pictures != null)
			try {
				int counter = tickcounter;
				ArrayList<ArrayList<ArrayList<BufferedImage>>> imagesToDraw = pictures.characters;
				int x = 0, y = 0, maxX = 0, maxY = 0, defaultX = 0, lastMaxY = 0;
				for (ArrayList<ArrayList<BufferedImage>> imagePacks : imagesToDraw) {
					for (ArrayList<BufferedImage> drawImagesList : imagePacks) {
						if (counter > 0) {
							counter--;
							continue;
						}
						lastMaxY = maxY;
						maxY = -1;
						BufferedImage lastImage = null;
						for (BufferedImage image : drawImagesList) {
							if (lastImage != null)
								x += lastImage.getWidth();
							if (y + image.getHeight() > maxY) {
								if (maxY == -1) {
									x = defaultX;
									y = lastMaxY + 1;
								}
								maxY = y + image.getHeight();
							}
							if (x + image.getWidth() > maxX)
								maxX = x + image.getWidth();
							if (y + image.getHeight() > getHeight()) {
								y = 0;
								maxY = image.getHeight();
								x = maxX + 1;
								defaultX = maxX + 1;
							}
							g.drawImage(image, x, y, this);
							// g.drawString("" + count, x + image.getWidth() /
							// 2, y + 3);
							lastImage = image;
						}
					}
				}
			} catch (Exception ignored) {
			}
	}

	public void tickDebug() {
		tickcounter--;
		if (tickcounter < 0)
			tickcounter = 0;
	}

	public void tickMainMenu() {
		if (!pictures.startedInit) {
			pictures.startedInit = true;
			picturesThread = new Thread(pictures.new PicturesThread(), "Picture-Loading");
			picturesThread.start();
		} else if (counter[7] == -1) {
			network = new Network(this);
			for (int i = 0; i < args.length; i++)
				if (args[i].startsWith("server:")) {
					network.overrideMasterServerIP = args[i].split(":")[1];
					logger.log(LogLevel.WARNING, "Set override master server IP to " + network.overrideMasterServerIP);
				}
			counter[7] = 0;
		}
		if (pictures.initialized) {
			picturesThread = null;
		}
		if (!temp[1].equals("Played Intro")) {
			if (music.play("Intro"))
				temp[1] = "Played Intro";
		}
		fadecounterenabled = 0;
		if (fadecounter != -2) {
			if (fadecounter <= HIGHEST_FADE_COUNTER)
				fadeIn();
			if (fadecounter > HIGHEST_FADE_COUNTER)
				fadecounter = HIGHEST_FADE_COUNTER + 1;
		}
		if (currentTimeMillis() - 80 > counter[2]) {
			counter[2] = currentTimeMillis();
			if (counter[3] + 1 > 13)
				counter[3] = 0;
			else
				counter[3]++;
		}
		if (fadecounter == -2)
			if (counter[7] == 0)
				if (pictures.initialized)
					loading = false;
		joystickHandler.setPollTime(2500);
	}

	public void tickMainMenu(Graphics2D g) {
		if (joystickHandler.getPollTime() != 2500)
			return; // tickMainMenu() hasn't run yet, avoid flashing fade
		boolean foundNull = false;
		for (int i = 0; i < pictures.mainMenu.size(); i++)
			if (pictures.mainMenu.get(i) == null)
				foundNull = true;
		if (pictures.mainMenu == null || pictures.mainMenu.size() < 7 || foundNull) {
			Tools.displayDialog("Error getting pictures, are file permissions OK where we are?",
					"Try right clicking the .jar file, clicking \"open with\", then click Java.");
			System.exit(0);
		}
		g.drawImage(pictures.mainMenu.get(0), 0, 0, this);
		int[] toDraw = { 1, 2, 3, 3, 3, 4, 5, 5, 4, 3, 3, 3, 2, 1 };
		if (counter[3] != -1)
			g.drawImage(pictures.mainMenu.get(toDraw[(int) counter[3]]), 0, 0, this);
		g.drawImage(pictures.mainMenu.get(pictures.mainMenu.size() - 1), 43, 18, this);
		if (fadecounter == -2) {
			if (pictures.initialized && joystickHandler != null && joystickHandler.getConnectedJoysticksAmt() > 0) {
				float percent = .5f;
				if (tickcounter < 50)
					percent = .5f + (float) tickcounter / 50f * .3f;
				else
					percent = .5f + .6f - (float) tickcounter / 50f * .3f;
				g.drawImage(Pictures.changeOpacity(pictures.xbox360controller, percent), 0, 0, this);
				if (joystickHandler.getConnectedJoysticksAmt() > 1)
					g.drawImage(Pictures.changeOpacity(pictures.xbox360controller, percent),
							getWidth() - pictures.xbox360controller.getWidth(), 0, this);
			}
			if (counter[7] == 0) {
				if (pictures.initialized)
					draw.textCenteredWithTexturedImage(g, Color.RED, tickcounter > 90, getHeight() - getHeight() / 3,
							Constants.MAIN_MENU_OPTIONS[0]);
			} else if (counter[7] == 1) {
				for (int i = 1; i < Constants.MAIN_MENU_OPTIONS.length; i++)
					draw.textCenteredWithTexturedImage(g, Color.YELLOW, tickcounter > 90 && counter[1] == i - 1,
							(getHeight() - getHeight() / 3) + (27 * (i - 1)) - 25, Constants.MAIN_MENU_OPTIONS[i]);
				draw.dinoSelector(
						g, pictures, (int) counter[3] / 4, getWidth() / 2
								- (Tools.getTextWidth(g, Constants.MAIN_MENU_OPTIONS[(int) counter[1] + 1]) / 2) - 35,
						(getHeight() - getHeight() / 3) - 25 + (27 * ((int) counter[1] - 1)) - 5);
			}
		}
		if (fadecounter != -2 && opacity == 0)
			opacity = 1;
		// tickFade(g);
	}

	public void tickMainMenu(int keyCode) {
		if (fadecounter == -2) {
			if (counter[7] == 0) {
				if (pictures.initialized)
					if (keyCode == options.controllers[0].CButton) {
						sound.play("Select");
						tickcounter = 90;
						counter[1] = 0;
						counter[7] = 1;
					}
			} else if (counter[7] == 1) {
				if (keyCode == options.controllers[0].downButton || keyCode == options.controllers[0].upButton) {
					long tempCounter = upAndDownMenuResult(counter[1], keyCode, Constants.MAIN_MENU_OPTIONS.length - 1,
							false, false);
					if (tempCounter != counter[1]) {
						sound.play("Tick");
						counter[1] = tempCounter;
						tickcounter = 90;
					}
				} else if (keyCode == options.controllers[0].upButton) {
					sound.play("Tick");
					if (counter[1] - 1 < 0)
						counter[1] = Constants.MAIN_MENU_OPTIONS.length - 2;
					else
						counter[1]--;
					tickcounter = 90;
				} else if (keyCode == options.controllers[0].AButton || keyCode == options.controllers[0].CButton) {
					if (counter[1] != 4)
						sound.play("Select");
					if (counter[1] == 0) {
						// quick play
						// TODO: adjust quick play settings if necessary
						setPause(Pause.PAINT);
						int playerAmt = 0;
						int aiAmt = 8;
						options.battleOptionsFinished = true;
						options.rulesOptionsFinished = true;
						options.bombersOptionsFinished = true;
						options.comLevel = 5;
						options.shuffle = false;
						options.time = 3f;
						counter[0] = 0;
						counter[1] = 0;
						counter[2] = currentTimeMillis();
						counter[3] = 0;
						counter[4] = 0;
						counter[5] = 0;
						counter[6] = 0;
						counter[7] = 0;
						players = new ArrayList<Player>();
						while (objects.size() > 0)
							objects.remove(0);
						int playersAdded = 0;
						for (int i = 0; i < playerAmt + aiAmt; i++) {
							players.add(new Player(this));
							Character c = new Character(pictures, i);
							players.get(i).setIndex(i);
							players.get(i).character = c;
							if (playersAdded < playerAmt) {
								players.get(i).isCpu = false;
								players.get(i).isLocal = true;
								playersAdded++;
							} else
								players.get(i).isCpu = true;
						}
						for (int i = 0; i < players.size(); i++)
							objects.add(players.get(i));
						stage = new Stage(1, this);
						state.setNextStateAfterFade(StateEnum.GOING_TO_STAGE, this);
						setPause(Pause.NONE);
					} else if (counter[1] == 1) {
						// hot seat
						setPause(Pause.PAINT);
						state.setNextStateAfterFade(StateEnum.MATCH_SELECTION, this);
						counter[0] = 0;
						counter[1] = 0;
						counter[2] = 0;
						counter[3] = 0;
						setPause(Pause.NONE);
					} else if (counter[1] == 2) {
						// network
						setPause(Pause.PAINT);
						network.setRandomCursor();
						state.setNextStateAfterFade(StateEnum.NETWORKING, this);
						if (options.multiplayer.username == null)
							counter[0] = 0;
						else
							counter[0] = 1;
						counter[1] = 0;
						counter[2] = 0;
						counter[3] = -1;
						setPause(Pause.NONE);
					} else if (counter[1] == 3) {
						// controls
						setPause(Pause.PAINT);
						counter[2] = 0;
						state.setNextStateAfterFade(StateEnum.CONTROLS, this);
						counter[1] = 2;
						counter[3] = 0;
						counter[6] = -1;
						setPause(Pause.NONE);
					}
					if (counter[1] == 4)
						System.exit(0);
				} else if (keyCode == options.controllers[0].BButton) {
					sound.play("Back");
					tickcounter = 90;
					counter[1] = 0;
					counter[7] = 0;
				}
			}
		}
	}

	public void tickNetworking() {
		fadecounterenabled = 0;
		if (fadecounter != -2) {
			if (fadecounter <= HIGHEST_FADE_COUNTER)
				fadeIn();
			if (fadecounter > HIGHEST_FADE_COUNTER)
				fadecounter = HIGHEST_FADE_COUNTER + 1;
		}
		if (!temp[1].equals("Played Networking")) {
			temp[1] = "Played Networking";
			music.loop("Networking Music " + network.getRandomCursor());
		}
		// TODO
		if (counter[0] == 2) {
			if (!network.masterServer.isConnected() && !network.masterServer.isConnecting()) {
				if (temp[0].startsWith("Result:")) {
					if (!Boolean.parseBoolean(temp[0].split(":")[1])) {
						logger.log(LogLevel.WARNING, "Could not connect to Master Server :(");
						counter[0] = 1;
						loading = false;
						String newIP = Tools.displayInputDialog("Could not connect to Master Server!",
								"If you want to try a different IP, type it now.", "Otherwise, leave this blank.");
						if (newIP != null && !newIP.equals("")) {
							network.overrideMasterServerIP = newIP;
							// Tools.displayDialog(
							// "IP set to: " + network.overrideMasterServerIP);
						}
					}
				} else if (temp[0].equals("")) {
					counter[2] = 0;
					loading = true;
					Thread masterServerConnection = new Thread(new Runnable() {
						public void run() {
							temp[0] = "Result:" + network.masterServer.connect();
						}
					});
					masterServerConnection.start();
					temp[0] = "Connecting to Master Server...";
				} else {
					temp[0] = network.masterServer.getConnectingMessage();
				}
			} else if (network.masterServer.isConnected()) {
				temp[0] = null;
				loading = false;
				if (counter[1] == 1) {
					// host
					resetUps();
					counter[0] = 3;
				} else if (counter[1] == 2) {
					// client
					resetUps();
					counter[0] = 5;
					counter[1] = 0;
					counter[4] = -1;
				}
			} else {
				temp[0] = network.masterServer.getConnectingMessage();
			}
		} else if (counter[0] == 4)
			network.server.setClientConnectingInfo(network.getConnectedClientAmt(), options.multiplayer.maxPlayers);
		else if (counter[0] == 5) {
			ArrayList<String[]> potentialServers = network.masterServer.getClientInfo();
			ArrayList<String[]> servers = new ArrayList<String[]>();
			if (potentialServers != null)
				for (int i = 0; i < potentialServers.size(); i++)
					if (potentialServers.get(i)[3].equals("true"))
						servers.add(potentialServers.get(i));
			if (servers.size() == 0 && counter[4] != -1)
				counter[4] = -1;
		} else if (counter[0] == 6) {
			if (network.client == null) {
				loading = true;
				ArrayList<String[]> potentialServers = network.masterServer.getClientInfo();
				ArrayList<String[]> servers = new ArrayList<String[]>();
				if (potentialServers != null)
					for (int i = 0; i < potentialServers.size(); i++)
						if (potentialServers.get(i)[3].equals("true"))
							servers.add(potentialServers.get(i));
				network.startClient(servers.get((int) counter[4])[1]);
			} else if (network.client.isConnected()) {
				logger.log(LogLevel.NORMAL, "Connected to Server successfully!");
				loading = false;
				counter[0] = 7;
				counter[1] = 0;
			} else {
				if (counter[5] < 300)
					counter[5]++;
				else {
					logger.log(LogLevel.ERROR, "Fatal error in connecting to Client :( not connected or connecting!");
					network.client.disconnect();
					network.client = null;
					counter[0] = 5;
					counter[1] = 0;
					counter[4] = -1;
					counter[5] = 0;
					loading = false;
				}
			}
		}
		if (counter[3] == -1 && sound.isPlaying("Cursor Choose"))
			sound.stop("Cursor Choose");
		if (counter[2] >= 59)
			counter[2] = 0;
		else
			counter[2]++;
		if (counter[3] > -1)
			if (counter[3] >= 34) {
				setPause(Pause.PAINT);
				counter[3] = -1;
				counter[2] = 15;
				if (counter[0] == 0) {
					// choose username
					options.multiplayer.username = temp[0];
					temp[0] = null;
					counter[0] = 1;
				} else if (counter[0] == 1) {
					if (counter[1] == 1) {
						// host
						temp[0] = "";
						counter[0] = 2;
					} else if (counter[1] == 2) {
						// client
						temp[0] = "";
						counter[0] = 2;
					} else if (counter[1] == 3) {
						// back
						state.setNextStateAfterFade(StateEnum.MAIN_MENU, this);
						counter[1] = 2;
						counter[7] = 1;
					}
				} else if (counter[0] == 3) {
					// host options
					if (counter[1] == 2) {
						// create game
						counter[0] = 4;
						counter[1] = 0;
						network.startServer();
					} else if (counter[1] == 3) {
						// back
						network.masterServer.disconnect();
						counter[0] = 1;
						counter[1] = 0;
					}
				} else if (counter[0] == 4) {
					// host running
					if (counter[1] == 0) {
						network.server.closeIncomingClientConnection();
						int playerCount = 1; // skip ourselves
						if (options.multiplayer.localPlayers == 2)
							playerCount++;
						for (int i = 0; i < network.connectedClients.size(); i++) {
							if (network.connectedClients.get(i)[0].equals(options.multiplayer.username))
								continue;
							if (i > 0 && options.multiplayer.localPlayers == 2
									&& network.connectedClients.get(i - 1)[0].equals(options.multiplayer.username))
								continue;
							network.server.sendMessageToClient("SERVER:PLAYER" + Client.SPLITTER + playerCount,
									network.connectedClients.get(i)[0]);
							playerCount++;
							if (Integer.parseInt(network.connectedClients.get(i)[1]) == 2)
								playerCount++;
						}
						network.server.sendMessageToAllClients("SERVER:START");
						setPause(Pause.PAINT);
						for (int i = 0; i < counter.length; i++)
							counter[i] = 0;
						for (int i = 0; i < options.players.length; i++)
							if (i < network.getTotalPlayersAmt())
								options.players[i] = 1;
							else
								options.players[i] = 2;
						state.setNextStateAfterFade(StateEnum.MATCH_SELECTION, this);
						network.inputTurn = 0;
						network.myInputTurnP1 = 0;
						if (options.multiplayer.localPlayers == 2)
							network.myInputTurnP2 = 1;
						setPause(Pause.NONE);
					} else if (counter[1] == 1) {
						// back
						network.server.disconnect();
						network.server.setClientConnectingInfo(-1, -1);
						counter[0] = 3;
						counter[1] = 0;
					}
				} else if (counter[0] == 5) {
					// client server list
					if (counter[1] == 0) {
						// select game
						ArrayList<String[]> potentialServers = network.masterServer.getClientInfo();
						ArrayList<String[]> servers = new ArrayList<String[]>();
						if (potentialServers != null)
							for (int i = 0; i < potentialServers.size(); i++)
								if (potentialServers.get(i)[3].equals("true"))
									servers.add(potentialServers.get(i));
						if (servers.size() > 0) {
							counter[1] = 3;
							if (counter[4] == -1)
								counter[4] = 0;
						}
					} else if (counter[1] == 1) {
						// join
						if (counter[4] != -1) {
							ArrayList<String[]> potentialServers = network.masterServer.getClientInfo();
							ArrayList<String[]> servers = new ArrayList<String[]>();
							if (potentialServers != null)
								for (int i = 0; i < potentialServers.size(); i++)
									if (potentialServers.get(i)[3].equals("true"))
										servers.add(potentialServers.get(i));
							int availableSlots = Integer.parseInt(servers.get((int) counter[4])[5])
									- Integer.parseInt(servers.get((int) counter[4])[4]);
							if (availableSlots >= options.multiplayer.localPlayers) {
								if (Boolean.parseBoolean(servers.get((int) counter[4])[2])
										&& !network.masterServer.weCanP2P())
									logger.log(LogLevel.WARNING,
											"Cannot join game: it requires P2P connectivity and we don't have that.. gotta open ports or something.");
								else
									// clear to join game
									counter[0] = 6;
							} else
								logger.log(LogLevel.WARNING, "Cannot join game: not enough open slots.");
						}
					} else if (counter[1] == 2) {
						// back
						network.masterServer.disconnect();
						counter[0] = 1;
						counter[1] = 2;
					} else if (counter[1] >= 3) {
						// selecting a game
						counter[1] = 1;
					}
				} else if (counter[0] == 7) {
					// client connected to server
					if (counter[1] == 0) {
						// back
						network.client.disconnect();
						network.client = null;
						counter[0] = 5;
						counter[4] = -1;
					}
				}
				setPause(Pause.NONE);
			} else
				counter[3]++;
	}

	public void tickNetworking(Graphics2D g) {
		Stroke stroke = g.getStroke();
		ArrayList<Integer[]> backgroundOffsets = new ArrayList<Integer[]>();
		backgroundOffsets.add(new Integer[] { -30, 15 });
		backgroundOffsets.add(new Integer[] { -5, -5 });
		backgroundOffsets.add(new Integer[] { -5, -5 });
		ArrayList<Color> boxColors = new ArrayList<Color>();
		boxColors.add(new Color(0, 114, 226));
		boxColors.add(new Color(226, 0, 0));
		boxColors.add(new Color(0, 226, 0));
		ArrayList<Integer[]> cursorOffsets = new ArrayList<Integer[]>();
		cursorOffsets.add(new Integer[] { 0, 0, 0, 0 });
		cursorOffsets.add(new Integer[] { -3, 5, -10, 1 });
		cursorOffsets.add(new Integer[] { -20, 0, -8, -10 });
		g.drawImage(pictures.networkingBackground.get(network.getRandomCursor()).get(0),
				backgroundOffsets.get(network.getRandomCursor())[0],
				backgroundOffsets.get(network.getRandomCursor())[1], this);
		draw.blackRectangle(g, .7f);
		if (options.multiplayer.username != null) {
			int textWidth = Tools.getTextWidth(g, options.multiplayer.username);
			int textHeight = Tools.getTextHeight(g, options.multiplayer.username);
			g.setColor(boxColors.get(network.getRandomCursor()));
			g.setStroke(new BasicStroke(3));
			draw.blackRectangle(g, 5, getHeight() - textHeight - 10, textWidth + 10, textHeight + 5);
			g.drawRect(5, getHeight() - textHeight - 10, textWidth + 10, textHeight + 5);
			g.setColor(Color.WHITE);
			g.drawString(options.multiplayer.username, 10, getHeight() - 10);
		}
		if (!network.masterServer.isNull()) {
			String text = (network.masterServer.isConnected()
					? (network.masterServer.weCanP2P() ? "NAT: Open" : "NAT: Closed")
					: "No Master Server");
			int textWidth = Tools.getTextWidth(g, text);
			int textHeight = Tools.getTextHeight(g, text);
			g.setColor(boxColors.get(network.getRandomCursor()));
			g.setStroke(new BasicStroke(3));
			draw.blackRectangle(g, getWidth() - Tools.getTextWidth(g, text) - 15, getHeight() - textHeight - 10,
					textWidth + 10, textHeight + 5);
			g.drawRect(getWidth() - Tools.getTextWidth(g, text) - 15, getHeight() - textHeight - 10, textWidth + 10,
					textHeight + 5);
			g.setColor(Color.WHITE);
			if (network.masterServer.isConnected())
				if (network.masterServer.weCanP2P())
					g.setColor(new Color(0, 1, 0, .5f));
				else
					g.setColor(new Color(1, 0, 0, .5f));
			else
				g.setColor(new Color(0, 0, 1, .5f));
			g.drawString(text, getWidth() - Tools.getTextWidth(g, text) - 10, getHeight() - 10);
		}
		int cursorAnim = 0, cursorX = 0, cursorY = 0;
		if (counter[3] != -1) {
			int picSize = pictures.networkingCursor.get(1 + (int) network.getRandomCursor() * 2).size();
			int maxCursorAnim = 35 / picSize;
			cursorAnim = -1;
			for (int i = 0; i < picSize; i++)
				if (counter[3] > maxCursorAnim * (i + 1))
					cursorAnim = i + 1;
			if (cursorAnim == picSize)
				cursorAnim--;
			if (cursorAnim == -1)
				cursorAnim = 0;
		} else {
			int picSize = pictures.networkingCursor.get(0 + (int) network.getRandomCursor() * 2).size();
			int maxCursorAnim = 60 / picSize;
			for (int i = 0; i < picSize; i++)
				if (counter[2] < maxCursorAnim * (i + 1)) {
					cursorAnim = i;
					// System.out.println("cursorAnim: " + cursorAnim);
					break;
				}
		}
		g.setColor(Color.WHITE);
		int textHeight = Tools.getTextHeight(g, "ENTER USERNAME");
		if (counter[0] == 0) {
			cursorX = getWidth() / 2 - 18;
			cursorY = 44;
			String username = "ENTER USERNAME";
			int textWidth = Tools.getTextWidth(g, username);
			draw.blackRectangle(g, getWidth() / 2 - textWidth / 2 - 25, 150 - textHeight - 20, textWidth + 50,
					textHeight + 45);
			g.drawString(username, getWidth() / 2 - textWidth / 2, 140);

			g.setColor(boxColors.get((int) network.getRandomCursor()));
			g.setStroke(new BasicStroke(3));
			g.drawRect(getWidth() / 2 - textWidth / 2 - 25, 150 - textHeight - 20, textWidth + 50, textHeight + 45);

			textWidth = Tools.getTextWidth(g, "WWWWWWWWWWWWW");
			draw.blackRectangle(g, getWidth() / 2 - textWidth / 2 - 25, 250 - textHeight - 7, textWidth + 50,
					textHeight + 20);
			g.drawRect(getWidth() / 2 - textWidth / 2 - 25, 250 - textHeight - 7, textWidth + 50, textHeight + 20);

			g.setColor(Color.WHITE);
			if (temp[0] != null) {
				g.drawString(temp[0], getWidth() / 2 - Tools.getTextWidth(g, temp[0]) / 2, 250);
				if (temp[0].length() < 14 && (tickcounter >= 75 || (tickcounter >= 25 && tickcounter < 50))) {
					g.drawString("_", getWidth() / 2 + Tools.getTextWidth(g, temp[0]) / 2, 250);
					g.drawString("_", getWidth() / 2 + Tools.getTextWidth(g, temp[0]) / 2 + 3, 250);
				}
			}

			Font smallFont = font.deriveFont(font.getSize() * .5f);
			g.setFont(smallFont);
			String escape = "PRESS ESCAPE/ENTER TO CONTINUE";
			textWidth = Tools.getTextWidth(g, smallFont, escape);
			g.drawString(escape, getWidth() / 2 - textWidth / 2, 160);
		} else if (counter[0] == 1) {
			String[] choices = { "LOCAL PLAYERS: ", "HOST", "CLIENT", "BACK" };
			for (int i = 0; i < choices.length; i++) {
				int textWidth = Tools.getTextWidth(g, choices[i]);
				int y = 70 + i * 100;
				int addWidth = 0;
				if (i == 0)
					addWidth += 10;

				g.setColor(boxColors.get((int) network.getRandomCursor()));
				g.setStroke(new BasicStroke(3));
				draw.blackRectangle(g, getWidth() / 2 - textWidth / 2 - 25, y - textHeight - 20,
						textWidth + 50 + addWidth, textHeight + 45);
				g.drawRect(getWidth() / 2 - textWidth / 2 - 25, y - textHeight - 20, textWidth + 50 + addWidth,
						textHeight + 45);

				String say = choices[i];
				if (i == 0)
					say += "" + options.multiplayer.localPlayers;
				g.setColor(Color.WHITE);
				g.drawString(say, getWidth() / 2 - textWidth / 2, y);

				if (counter[1] == i) {
					cursorX = getWidth() / 2 - textWidth / 2 - 70;
					cursorY = y - textHeight - 16;
				}
			}
		} else if (counter[0] == 2) {
			String connecting = temp[0];
			int textWidth = Tools.getTextWidth(g, connecting);
			g.setColor(boxColors.get((int) network.getRandomCursor()));
			g.setStroke(new BasicStroke(3));
			draw.blackRectangle(g, getWidth() / 2 - textWidth / 2 - 25, getHeight() / 2 - textHeight - 20,
					textWidth + 50, textHeight + 45);
			g.drawRect(getWidth() / 2 - textWidth / 2 - 25, getHeight() / 2 - textHeight - 20, textWidth + 50,
					textHeight + 45);
			g.setColor(Color.WHITE);
			g.drawString(connecting, getWidth() / 2 - textWidth / 2, getHeight() / 2);
			cursorX = getWidth() / 2 - 18;
			cursorY = getHeight() / 2 - 106;
		} else if (counter[0] == 3) {
			String[] choices = { "HOSTING OPTIONS", "FORCE P2P CONNECTIONS: ", "MAX PLAYERS: ", "CREATE GAME", "BACK" };
			for (int i = 0; i < choices.length; i++) {
				int textWidth = Tools.getTextWidth(g, choices[i]);
				if (i == 1)
					textWidth += 50;
				else if (i == 2)
					textWidth += 20;
				int y = 50 + i * 70;
				if (i > 2)
					y += 30;

				g.setColor(boxColors.get((int) network.getRandomCursor()));
				g.setStroke(new BasicStroke(3));
				draw.blackRectangle(g, getWidth() / 2 - textWidth / 2 - 25, y - textHeight - 5, textWidth + 50,
						textHeight + 15);
				g.drawRect(getWidth() / 2 - textWidth / 2 - 25, y - textHeight - 5, textWidth + 50, textHeight + 15);

				g.setColor(Color.WHITE);
				String say = choices[i];
				if (i == 1)
					say += (options.multiplayer.useP2P ? "YES" : "NO");
				else if (i == 2)
					say += "" + options.multiplayer.maxPlayers;
				g.drawString(say, getWidth() / 2 - textWidth / 2, y);

				if (counter[1] == i - 1) {
					cursorX = getWidth() / 2 - textWidth / 2 - 70;
					cursorY = y - textHeight - 16;
				}
				if (!network.masterServer.weCanP2P() && i == 1) {
					draw.blackRectangle(g, getWidth() / 2 - textWidth / 2 - 26, y - textHeight - 6, textWidth + 53,
							textHeight + 18, .8f);
				}
			}
		} else if (counter[0] == 4) {
			String[] choices = { "WAITING FOR CLIENTS", "CLIENTS CONNECTED: " };
			for (int i = 0; i < choices.length; i++) {
				int textWidth = Tools.getTextWidth(g, choices[i]);
				if (i == 1)
					textWidth += 50;
				int y = 50 + i * 58;

				g.setColor(boxColors.get((int) network.getRandomCursor()));
				g.setStroke(new BasicStroke(3));
				draw.blackRectangle(g, getWidth() / 2 - textWidth / 2 - 25, y - textHeight - 5, textWidth + 50,
						textHeight + 15);
				g.drawRect(getWidth() / 2 - textWidth / 2 - 25, y - textHeight - 5, textWidth + 50, textHeight + 15);

				g.setColor(Color.WHITE);
				String say = choices[i];
				if (i == 1)
					try {
						say += "" + network.getConnectedClientAmt() + "/" + options.multiplayer.maxPlayers;
					} catch (Exception e) {// server not set up yet?
					}
				g.drawString(say, getWidth() / 2 - textWidth / 2, y);
			}
			g.setColor(boxColors.get((int) network.getRandomCursor()));
			g.setStroke(new BasicStroke(3));
			for (int i = 0; i < 2; i++) {
				draw.blackRectangle(g, 425, 162 + 112 * i, 175, 75);
				g.drawRect(425, 162 + 112 * i, 175, 75);
			}
			int startX = 50, startY = 140, width = 310, height = 258;
			float adj = 1.6f, adj2 = 1.3f, adj3 = 2.7f;
			draw.blackRectangle(g, startX, startY, width, height);
			g.drawRect(startX, startY, width, height);
			g.setStroke(new BasicStroke(1));
			g.drawRect(width / 2, startY, 100, 20);
			g.drawLine(startX, startY + 50, startX + width, startY + 50);
			g.drawLine(width - (int) ((float) startX * adj), startY + 50, width - (int) ((float) startX * adj),
					startY + height);
			g.drawLine(width - (int) (((float) startX * adj) / adj3), startY + 50,
					width - (int) (((float) startX * adj) / adj3), startY + height);
			g.setColor(Color.WHITE);

			g.setFont(defaultFont.deriveFont(12.5f));
			g.drawString("Client List", (startX + width / 2) - Tools.getTextWidth(g, "Client List") / 2, startY + 15);
			g.drawString("Username",
					((width - (int) ((float) startX * adj)) / 2 + startX / 2) - Tools.getTextWidth(g, "Username") / 2,
					startY + 40);
			g.drawString("Players",
					width - (int) ((float) startX * adj * adj2) + 50 - Tools.getTextWidth(g, "Players") / 2,
					startY + 40);
			g.drawString("Ping",
					width - (int) (((float) startX * adj * adj2) / adj3) + 50 - Tools.getTextWidth(g, "Ping") / 2,
					startY + 40);
			try {
				for (int i = 0; i < network.connectedClients.size(); i++) {
					String username = network.connectedClients.get(i)[0];
					String players = network.connectedClients.get(i)[1];
					String ping = "" + network.server.getClientPing(username);
					g.drawString(username, ((width - (int) ((float) startX * adj)) / 2 + startX / 2)
							- Tools.getTextWidth(g, username) / 2, startY + 70 + 25 * i);
					g.drawString(players,
							width - (int) ((float) startX * adj * adj2) + 50 - Tools.getTextWidth(g, players) / 2,
							startY + 70 + 25 * i);
					g.drawString(ping,
							width - (int) (((float) startX * adj * adj2) / adj3) + 50 - Tools.getTextWidth(g, ping) / 2,
							startY + 70 + 25 * i);
				}
			} catch (Exception e) {
				// Could throw an Exception if a Client is added/removed while
				// running this. Just ignore it, next tick will correct it.
			}
			g.setFont(font);
			g.drawString("START", 512 - Tools.getTextWidth(g, "START") / 2, 194);
			g.drawString("GAME", 512 - Tools.getTextWidth(g, "GAME") / 2, 224);
			if (network.connectedClients.size() == options.multiplayer.localPlayers)
				draw.blackRectangle(g, 424, 161, 178, 78, .75f);
			g.drawString("BACK", 512 - Tools.getTextWidth(g, "BACK") / 2, 311 + textHeight / 2);

			cursorX = 380;
			cursorY = 168 + (int) counter[1] * 114;
		} else if (counter[0] == 5) {
			draw.blackRectangle(g, 50, 50, 300, 300);
			g.setColor(boxColors.get((int) network.getRandomCursor()));
			g.setStroke(new BasicStroke(3));
			g.drawRect(50, 50, 300, 300);
			g.setStroke(new BasicStroke(1));
			g.drawRect(150, 50, 100, 20);
			g.drawLine(50, 100, 350, 100);
			g.drawRect(200, 100, 75, 250);
			// for(int i=0;i<3;i++) {g.drawRect(50,100 + i * 100,300,50);}

			g.setColor(Color.WHITE);
			g.setFont(defaultFont.deriveFont(12.5f));
			g.drawString("Server List", 200 - Tools.getTextWidth(g, "Server List") / 2, 65);
			g.drawString("Host", 125 - Tools.getTextWidth(g, "Host") / 2, 90);
			g.drawString("P2P", 237 - Tools.getTextWidth(g, "P2P") / 2, 90);
			g.drawString("Players", 312 - Tools.getTextWidth(g, "Players") / 2, 90);

			ArrayList<String[]> potentialServers = network.masterServer.getClientInfo();
			ArrayList<String[]> servers = new ArrayList<String[]>();
			if (potentialServers != null) {
				for (int i = 0; i < potentialServers.size(); i++)
					if (potentialServers.get(i)[3].equals("true"))
						servers.add(potentialServers.get(i));
				for (int i = 0; i < servers.size(); i++) {
					// counter[4] = 1;
					if (counter[4] == i) {
						Color c = boxColors.get((int) network.getRandomCursor());
						g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 122));
						g.fillRect(51, 105 + 25 * i, 299, 20);
						g.setColor(Color.WHITE);
					}
					String[] server = servers.get(i);
					g.drawString(server[0], 125 - Tools.getTextWidth(g, server[0]) / 2, 120 + 25 * i);
					g.drawString(server[2], 237 - Tools.getTextWidth(g, server[2]) / 2, 120 + 25 * i);
					String players = server[4] + " / " + server[5];
					g.drawString(players, 312 - Tools.getTextWidth(g, players) / 2, 120 + 25 * i);
				}
			}

			g.setColor(boxColors.get((int) network.getRandomCursor()));
			g.setStroke(new BasicStroke(3));
			for (int i = 0; i < 3; i++) {
				draw.blackRectangle(g, 425, 50 + 112 * i, 175, 75);
				g.drawRect(425, 50 + 112 * i, 175, 75);
			}

			g.setFont(font);
			g.setColor(Color.WHITE);

			g.drawString("SELECT", 512 - Tools.getTextWidth(g, "SELECT") / 2, 80);
			g.drawString("GAME", 512 - Tools.getTextWidth(g, "GAME") / 2, 110);
			if (servers.size() == 0)
				draw.blackRectangle(g, 424, 49, 178, 78, .75f);

			g.drawString("JOIN", 512 - Tools.getTextWidth(g, "JOIN") / 2, 199 + textHeight / 2);
			if (counter[4] == -1)
				draw.blackRectangle(g, 424, 161, 178, 78, .75f);

			g.drawString("BACK", 512 - Tools.getTextWidth(g, "BACK") / 2, 311 + textHeight / 2);

			if (counter[1] >= 0 && counter[1] <= 2) {
				cursorX = 380;
				cursorY = 54 + (int) counter[1] * 114;
			} else {
				cursorX = 5;
				cursorY = 150;
			}
		} else if (counter[0] == 6) {
			String connecting = "CONNECTING TO SERVER...";
			int textWidth = Tools.getTextWidth(g, connecting);
			g.setColor(boxColors.get((int) network.getRandomCursor()));
			g.setStroke(new BasicStroke(3));
			draw.blackRectangle(g, getWidth() / 2 - textWidth / 2 - 25, getHeight() / 2 - textHeight - 20,
					textWidth + 50, textHeight + 45);
			g.drawRect(getWidth() / 2 - textWidth / 2 - 25, getHeight() / 2 - textHeight - 20, textWidth + 50,
					textHeight + 45);
			g.setColor(Color.WHITE);
			g.drawString(connecting, getWidth() / 2 - textWidth / 2, getHeight() / 2);
			cursorX = getWidth() / 2 - 18;
			cursorY = getHeight() / 2 - 106;
		} else if (counter[0] == 7) {
			String[] choices = { "WAITING FOR HOST", "CLIENTS CONNECTED: " };
			for (int i = 0; i < choices.length; i++) {
				int textWidth = Tools.getTextWidth(g, choices[i]);
				if (i == 1)
					textWidth += 50;
				int y = 50 + i * 58;

				g.setColor(boxColors.get((int) network.getRandomCursor()));
				g.setStroke(new BasicStroke(3));
				draw.blackRectangle(g, getWidth() / 2 - textWidth / 2 - 25, y - textHeight - 5, textWidth + 50,
						textHeight + 15);
				g.drawRect(getWidth() / 2 - textWidth / 2 - 25, y - textHeight - 5, textWidth + 50, textHeight + 15);

				g.setColor(Color.WHITE);
				String say = choices[i];
				if (i == 1)
					say += "" + network.getConnectedClientAmt() + "/" + network.serverMaxClients;
				g.drawString(say, getWidth() / 2 - textWidth / 2, y);
			}
			g.setColor(boxColors.get((int) network.getRandomCursor()));
			g.setStroke(new BasicStroke(3));
			draw.blackRectangle(g, 425, 274, 175, 75);
			g.drawRect(425, 274, 175, 75);

			int startX = 50, startY = 140, width = 310, height = 258;
			float adj = 1.6f, adj2 = 1.3f, adj3 = 2.7f;
			draw.blackRectangle(g, startX, startY, width, height);
			g.drawRect(startX, startY, width, height);
			g.setStroke(new BasicStroke(1));
			g.drawRect(width / 2, startY, 100, 20);
			g.drawLine(startX, startY + 50, startX + width, startY + 50);
			g.drawLine(width - (int) ((float) startX * adj), startY + 50, width - (int) ((float) startX * adj),
					startY + height);
			g.drawLine(width - (int) (((float) startX * adj) / adj3), startY + 50,
					width - (int) (((float) startX * adj) / adj3), startY + height);
			g.setColor(Color.WHITE);

			g.setFont(defaultFont.deriveFont(12.5f));
			g.drawString("Client List", (startX + width / 2) - Tools.getTextWidth(g, "Client List") / 2, startY + 15);
			g.drawString("Username",
					((width - (int) ((float) startX * adj)) / 2 + startX / 2) - Tools.getTextWidth(g, "Username") / 2,
					startY + 40);
			g.drawString("Players",
					width - (int) ((float) startX * adj * adj2) + 50 - Tools.getTextWidth(g, "Players") / 2,
					startY + 40);
			g.drawString("Ping",
					width - (int) (((float) startX * adj * adj2) / adj3) + 50 - Tools.getTextWidth(g, "Ping") / 2,
					startY + 40);
			try {
				for (int i = 0; i < network.connectedClients.size(); i++) {
					String username = network.connectedClients.get(i)[0];
					String players = network.connectedClients.get(i)[1];
					String ping = network.connectedClients.get(i)[2];
					g.drawString(username, ((width - (int) ((float) startX * adj)) / 2 + startX / 2)
							- Tools.getTextWidth(g, username) / 2, startY + 70 + 25 * i);
					g.drawString(players,
							width - (int) ((float) startX * adj * adj2) + 50 - Tools.getTextWidth(g, players) / 2,
							startY + 70 + 25 * i);
					g.drawString(ping,
							width - (int) (((float) startX * adj * adj2) / adj3) + 50 - Tools.getTextWidth(g, ping) / 2,
							startY + 70 + 25 * i);
				}
			} catch (Exception e) {
				// Could throw an Exception if a Client is added/removed while
				// running this. Just ignore it, next tick will correct it.
			}
			g.setFont(font);
			g.drawString("BACK", 512 - Tools.getTextWidth(g, "BACK") / 2, 311 + textHeight / 2);

			cursorX = 380;
			cursorY = 282;
		}
		try {
			if (counter[3] != -1)
				g.drawImage(pictures.networkingCursor.get(1 + (int) network.getRandomCursor() * 2).get(cursorAnim),
						cursorX - 32 + cursorOffsets.get((int) network.getRandomCursor())[2],
						cursorY + cursorOffsets.get((int) network.getRandomCursor())[3], this);
			else {
				g.drawImage(pictures.networkingCursor.get(0 + (int) network.getRandomCursor() * 2).get(cursorAnim),
						cursorX + cursorOffsets.get((int) network.getRandomCursor())[0],
						cursorY + cursorOffsets.get((int) network.getRandomCursor())[1], this);
			}
		} catch (Exception e) {
			// e.printStackTrace();
			// ignore temporary problems w/ cursor
		}
		g.setStroke(stroke);
		if (fadecounter != -2 && opacity == 0)
			opacity = 1;
	}

	public void tickNetworking(int keyCode) {
		if (counter[0] == 0) {
			// Select username
			if (keyCode == KeyEvent.VK_ESCAPE || keyCode == KeyEvent.VK_ENTER) {
				if (temp[0].length() < 3)
					logger.log(LogLevel.WARNING, "Cannot continue unless username is at least 3 characters long.");
				else if (!sound.isPlaying("Cursor Choose " + network.getRandomCursor())) {
					sound.play("Cursor Choose " + network.getRandomCursor());
					counter[3] = 0;
				}
			} else if (keyCode == KeyEvent.VK_BACK_SPACE) {
				char[] name = temp[0].toCharArray();
				if (name.length > 0) {
					String tempString = "";
					for (int i = 0; i < name.length - 1; i++)
						tempString += name[i];
					temp[0] = tempString;
				}
			} else {
				if (temp[0].length() < 14) {
					String key = KeyEvent.getKeyText(keyCode);
					if (key.length() == 1)
						temp[0] += key;
					else
						logger.log(LogLevel.WARNING, "Can only place letters and numbers in username.");
				} else
					logger.log(LogLevel.WARNING, "Max amount of characters in username.");
			}
		} else if (counter[0] == 1) {
			// Host, Client, Local Players, Back
			counter[1] = upAndDownMenuResult(counter[1], keyCode, 4, true, true);
			if (keyCode == options.controllers[0].leftButton) {
				if (counter[1] == 0)
					if (options.multiplayer.localPlayers == 2)
						options.multiplayer.localPlayers = 1;
			} else if (keyCode == options.controllers[0].rightButton) {
				if (counter[1] == 0)
					if (options.multiplayer.localPlayers == 1) {
						options.multiplayer.localPlayers = 2;
						if (options.multiplayer.maxPlayers == 2)
							options.multiplayer.maxPlayers = 3;
					}
			} else if (keyCode == options.controllers[0].BButton) {
				counter[1] = 3;
				tickNetworking(options.controllers[0].CButton);
			} else if (keyCode == options.controllers[0].AButton || keyCode == options.controllers[0].CButton) {
				if (!sound.isPlaying("Cursor Choose " + network.getRandomCursor())) {
					sound.play("Cursor Choose " + network.getRandomCursor());
					counter[3] = 0;
				}
			}
		} else if (counter[0] == 2) {
			// Connecting to master server
		} else if (counter[0] == 3) {
			// Host options menu
			counter[1] = upAndDownMenuResult(counter[1], keyCode, 4, true, true);
			if (keyCode == options.controllers[0].leftButton) {
				if (counter[1] == 0) {
					if (network.masterServer.weCanP2P()) {
						options.multiplayer.useP2P = !options.multiplayer.useP2P;
						sound.play("Tick");
					} else {
						logger.log(LogLevel.WARNING,
								"Cannot force P2P connections because we are not eligible for one. Maybe our ports aren't open or something.");
					}
				} else if (counter[1] == 1) {
					if (options.multiplayer.maxPlayers > 1 + options.multiplayer.localPlayers) {
						options.multiplayer.maxPlayers--;
						sound.play("Tick");
					}
				}
			} else if (keyCode == options.controllers[0].rightButton) {
				if (counter[1] == 0) {
					if (network.masterServer.weCanP2P()) {
						options.multiplayer.useP2P = !options.multiplayer.useP2P;
						sound.play("Tick");
					} else {
						logger.log(LogLevel.WARNING,
								"Cannot force P2P connections because we are not eligible for one. Maybe our ports aren't open or something.");
					}
				} else if (counter[1] == 1) {
					if (options.multiplayer.maxPlayers < 8) {
						options.multiplayer.maxPlayers++;
						sound.play("Tick");
					}
				}
			} else if (keyCode == options.controllers[0].BButton) {
				counter[1] = 3;
				tickNetworking(options.controllers[0].CButton);
			} else if (keyCode == options.controllers[0].AButton || keyCode == options.controllers[0].CButton) {
				if (!sound.isPlaying("Cursor Choose " + network.getRandomCursor())) {
					sound.play("Cursor Choose " + network.getRandomCursor());
					counter[3] = 0;
				}
			}
		} else if (counter[0] == 4) {
			// waiting for clients
			counter[1] = upAndDownMenuResult(counter[1], keyCode, 2, true, true);
			if (keyCode == options.controllers[0].BButton) {
				counter[1] = 1;
				tickNetworking(options.controllers[0].CButton);
			} else if (keyCode == options.controllers[0].AButton || keyCode == options.controllers[0].CButton) {
				if (counter[1] == 0 && network.connectedClients.size() == options.multiplayer.localPlayers)
					return;
				if (!sound.isPlaying("Cursor Choose " + network.getRandomCursor())) {
					if (randomCount == 0)
						Tools.random(this, "Don't have zero randomCount!");
					sound.play("Cursor Choose " + network.getRandomCursor());
					counter[3] = 0;
				}
			}
		} else if (counter[0] == 5) {
			// client server list
			ArrayList<String[]> potentialServers = network.masterServer.getClientInfo();
			ArrayList<String[]> servers = new ArrayList<String[]>();
			if (potentialServers != null)
				for (int i = 0; i < potentialServers.size(); i++)
					if (potentialServers.get(i)[3].equals("true"))
						servers.add(potentialServers.get(i));
			if (keyCode == options.controllers[0].upButton) {
				long counter1Before = counter[1];
				if (counter[1] > 2) {
					if (counter[1] > 3)
						counter[1]--;
					else
						counter[1] = 2 + servers.size();
					counter[4] = counter[1] - 3;
				} else {
					if (counter[1] > 0)
						counter[1]--;
					else
						counter[1] = 2;
				}
				if (counter1Before != counter[1])
					sound.play("Tick");
			} else if (keyCode == options.controllers[0].downButton) {
				long counter1Before = counter[1];
				if (counter[1] > 2) {
					if (counter[1] < 2 + servers.size())
						counter[1]++;
					else
						counter[1] = 3;
					counter[4] = counter[1] - 3;
				} else {
					if (counter[1] < 2)
						counter[1]++;
					else
						counter[1] = 0;
				}
				if (counter1Before != counter[1])
					sound.play("Tick");
			} else if (keyCode == options.controllers[0].AButton || keyCode == options.controllers[0].CButton) {
				if (!sound.isPlaying("Cursor Choose " + network.getRandomCursor())) {
					sound.play("Cursor Choose " + network.getRandomCursor());
					counter[3] = 0;
				}
			}
		} else if (counter[0] == 7) {
			// client connected to server
			if (keyCode == options.controllers[0].AButton || keyCode == options.controllers[0].BButton
					|| keyCode == options.controllers[0].CButton)
				if (counter[1] == 0)
					if (!sound.isPlaying("Cursor Choose " + network.getRandomCursor())) {
						sound.play("Cursor Choose " + network.getRandomCursor());
						counter[3] = 0;
					}
		}
	}

	public void tickControls() {
		fadecounterenabled = 0;
		if (fadecounter != -2) {
			if (fadecounter <= HIGHEST_FADE_COUNTER)
				fadeIn();
			if (fadecounter > HIGHEST_FADE_COUNTER)
				fadecounter = HIGHEST_FADE_COUNTER + 1;
		}
		if (!temp[1].equals("Played Controls")) {
			temp[1] = "Played Controls";
			music.loop("Options");
		}
		if (counter[2] > 159)
			counter[2] = 0;
		else
			counter[2]++;
		if (counter[2] < 20)
			counter[3] = 0;
		else if (counter[2] < 40)
			counter[3] = 1;
		else if (counter[2] < 60)
			counter[3] = 2;
		else if (counter[2] < 80)
			counter[3] = 3;
		else if (counter[2] < 100)
			counter[3] = 0;
		else if (counter[2] < 120)
			counter[3] = 1;
		else if (counter[2] < 140)
			counter[3] = 2;
		else if (counter[2] <= 160)
			counter[3] = 3;
	}

	public static Image getImageFromArray(int[] pixels, int width, int height) {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		WritableRaster raster = (WritableRaster) image.getData();
		raster.setPixels(0, 0, width, height, pixels);
		image.setData(raster);
		return image;
	}

	public void controlBg(BufferedImage img, Graphics2D g) {
		byte[] pixels = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
		// final byte[] pixels = ((DataBufferByte)
		// img.getRaster().getDataBuffer())
		// .getData();
		final boolean hasAlphaChannel = img.getAlphaRaster() != null;
		int[][] result = new int[img.getHeight()][img.getWidth()];
		if (hasAlphaChannel) {
			final int pixelLength = 4;
			for (int pixel = 0, row = 0, col = 0; pixel < pixels.length; pixel += pixelLength) {
				int argb = 0;
				argb += (((int) pixels[pixel] & 0xff) << 24); // alpha
				argb += ((int) pixels[pixel + 1] & 0xff); // blue
				argb += (((int) pixels[pixel + 2] & 0xff) << 8); // green
				argb += (((int) pixels[pixel + 3] & 0xff) << 16); // red
				result[row][col] = argb;
				pixels[pixel] = (byte) (((int) pixels[pixel] & 0xff) << 24);
				pixels[pixel + 1] = (byte) (((int) pixels[pixel + 1] & 0xff));
				pixels[pixel + 2] = (byte) (((int) pixels[pixel + 2] & 0xff) >> 8);
				pixels[pixel + 3] = (byte) (((int) pixels[pixel + 3] & 0xff) << 16);
				col++;
				if (col == img.getWidth()) {
					col = 0;
					row++;
				}
			}
		} else {
			final int pixelLength = 3;
			for (int pixel = 0, row = 0, col = 0; pixel < pixels.length; pixel += pixelLength) {
				int argb = 0;
				argb += -16777216; // 255 alpha
				argb += ((int) pixels[pixel] & 0xff); // blue
				argb += (((int) pixels[pixel + 1] & 0xff) << 8); // green
				argb += (((int) pixels[pixel + 2] & 0xff) << 16); // red
				result[row][col] = argb;
				pixels[pixel + 1] = (byte) (((int) pixels[pixel] & 0xff));
				pixels[pixel + 2] = (byte) (((int) pixels[pixel + 1] & 0xff) >> 8);
				pixels[pixel + 3] = (byte) (((int) pixels[pixel + 2] & 0xff) << 16);
				col++;
				if (col == img.getWidth()) {
					col = 0;
					row++;
				}
			}
		}
		ByteArrayInputStream stream = new ByteArrayInputStream(pixels);
		BufferedImage bufferedImage = null;
		try {
			bufferedImage = ImageIO.read(stream);
		} catch (IOException e) {
			e.printStackTrace();
		}
		g.drawImage(bufferedImage, 0, 0, this);
		/*
		 * int change = 20 * 4; for (int i = 0; i < img.getWidth(); i++) for (int j = 0;
		 * j < img.getHeight(); j++) { for (int thisCounter = (int) (counter[2] / 2) -
		 * change * 5; thisCounter < img .getHeight() + change; thisCounter += change) {
		 * int diff = (int) (Math.abs(thisCounter - j) * .5); if (diff <= change / 4) {
		 * long width = img.getWidth(); while (width > diff) width -= diff + 1; long x =
		 * i + width + diff; if (x >= 0 && x < img.getWidth()) img.setRGB(i, j,
		 * pictures.optionsMenu.getRGB((int) x, j)); else img.setRGB(i, j,
		 * pictures.optionsMenu.getRGB( (int) (x - img.getWidth()), j)); } } } return
		 * img;
		 */
	}

	int[][] controlsBgImage = null;
	BufferedImage[] controlsBgImages = new BufferedImage[81];

	public void tickControls(Graphics2D g) {
		if (counter[2] < 0)
			return;
		if (controlsBgImage == null)
			controlsBgImage = new int[pictures.optionsMenu.getWidth()][pictures.optionsMenu.getHeight()];
		BufferedImage img = new BufferedImage(pictures.optionsMenu.getWidth(), pictures.optionsMenu.getHeight(),
				BufferedImage.TYPE_INT_ARGB);
		int width = img.getWidth(), height = img.getHeight();
		WritableRaster raster = pictures.optionsMenu.getRaster();
		int[] pixels = null;
		raster.getPixels(0, 0, width, height, pixels);
		// pixels = pictures.optionsMenu.getRGB(0, 0, width, height, pixels, 0,
		// width);
		int half = (int) counter[2] / 2;
		BufferedImage test = null;
		try {
			test = controlsBgImages[half];
		} catch (Exception e) {
			counter[2] = 0;
		}
		if (test != null) {
			img = test;
		} else {
			for (int j = 0; j < height; j++)
				for (int i = 0; i < width; i++) {
					for (int thisCounter = half - 400; thisCounter < height + 40; thisCounter += 80) {
						int diff = (int) (Math.abs(thisCounter - j) * .5);
						if (diff <= 20) {
							long w = width - (diff + 1) * 30;
							while (w > diff)
								w -= diff + 1;
							long x = i + w + diff;
							if (x >= 0 && x < width)
								pixels = raster.getPixel((int) x, j, pixels);
							else
								pixels = raster.getPixel((int) x - width, j, pixels);
							Color color = new Color(pixels[0], pixels[1], pixels[2], pixels[3]);
							img.setRGB(i, j, color.getRGB());
							// if (x >= 0 && x < width)
							// img.setRGB(i, j, pixels[(int) x + j * width]);
							// else
							// img.setRGB(i, j, pixels[(int) x - width + j *
							// width]);
						}
					}
				}
			controlsBgImages[half] = img;
		}
		// controlBg(pictures.optionsMenu, g);
		g.drawImage(img, 0, 0, this);

		String[] tempOptionsOne = Tools.changeArraySafely(Constants.CONTROLS_MENU_OPTIONS);
		String[] tempOptionsTwo = Tools.changeArraySafely(Constants.CONTROLS_MENU_OPTIONS);

		draw.textCenteredWithTexturedImage(g, Color.RED, false, 40, tempOptionsOne[0]);
		draw.textWithTexturedImage(g, Color.RED, false, 50, 105,
				tempOptionsOne[1].replaceAll("\\(.*\\)", "(" + "one" + ")"));
		draw.textWithTexturedImage(g, Color.RED, false,
				getWidth() - Tools.getTextWidth(g, tempOptionsTwo[1].replaceAll("\\(.*\\)", "(" + "two" + ")")) - 25,
				105, tempOptionsTwo[1].replaceAll("\\(.*\\)", "(" + "two" + ")"));

		int[] keysTwo = { options.controllers[1].upButton, options.controllers[1].downButton,
				options.controllers[1].leftButton, options.controllers[1].rightButton, options.controllers[1].AButton,
				options.controllers[1].BButton, options.controllers[1].CButton, options.controllers[1].LButton,
				options.controllers[1].RButton };
		for (int i = 2; i < tempOptionsTwo.length - 2; i++) {
			tempOptionsTwo[i] = tempOptionsTwo[i].replaceAll("\\(.*\\)",
					"(" + KeyEvent.getKeyText(keysTwo[i - 2]) + ")");
			tempOptionsTwo[i] = tempOptionsTwo[i].split(":")[1] + ": " + tempOptionsTwo[i].split(":")[0];
		}
		for (int i = 2; i < tempOptionsTwo.length - 2; i++) {
			draw.textWithTexturedImage(g, Color.YELLOW,
					tickcounter > 90 && counter[1] == i && counter[6] <= -1 && counter[0] == 1,
					getWidth() - Tools.getTextWidth(g, tempOptionsTwo[i]) - 25, 105 + 27 * (i - 1), tempOptionsTwo[i]);
		}

		int[] keys = { options.controllers[0].upButton, options.controllers[0].downButton,
				options.controllers[0].leftButton, options.controllers[0].rightButton, options.controllers[0].AButton,
				options.controllers[0].BButton, options.controllers[0].CButton, options.controllers[0].LButton,
				options.controllers[0].RButton, chatButton };
		for (int i = 2; i < tempOptionsOne.length - 1; i++)
			tempOptionsOne[i] = tempOptionsOne[i].replaceAll("\\(.*\\)", "(" + KeyEvent.getKeyText(keys[i - 2]) + ")");

		for (int i = 2; i < tempOptionsOne.length - 2; i++) {
			draw.textWithTexturedImage(g, Color.YELLOW,
					tickcounter > 90 && counter[1] == i && counter[6] <= -1 && counter[0] == 0, 50, 105 + 27 * (i - 1),
					tempOptionsOne[i]);
		}
		for (int i = tempOptionsOne.length - 2; i < tempOptionsOne.length; i++) {
			draw.textCenteredWithTexturedImage(g, Color.YELLOW, tickcounter > 90 && counter[1] == i && counter[6] <= -1,
					105 + 27 * (i - 1), tempOptionsOne[i]);
		}
		for (int i = 0; i < tempOptionsOne.length; i++)
			tempOptionsOne[i] = tempOptionsOne[i].replace("(", "").replace(")", "");
		if (counter[1] >= 2 && counter[1] <= tempOptionsOne.length - 3 && counter[0] == 0)
			draw.dinoSelector(g, pictures, (int) counter[3], 50 - 35, 105 + 27 * ((int) counter[1] - 2) - 5);
		else if (counter[1] >= 2 && counter[1] <= tempOptionsOne.length - 3 && counter[0] == 1)
			draw.dinoSelector(g, pictures, (int) counter[3], getWidth() - 47, 105 + 27 * ((int) counter[1] - 2) - 5);
		else if (counter[1] >= tempOptionsOne.length - 2 && counter[1] <= tempOptionsOne.length) {
			draw.dinoSelector(g, pictures, (int) counter[3],
					getWidth() / 2 - Tools.getTextWidth(g, tempOptionsOne[(int) counter[1]]) / 2 - 35,
					105 + 27 * ((int) counter[1] - 2) - 5);
			draw.dinoSelector(g, pictures, (int) counter[3],
					getWidth() / 2 + Tools.getTextWidth(g, tempOptionsOne[(int) counter[1]]) / 2 + 3,
					105 + 27 * ((int) counter[1] - 2) - 5);
		}
		if (counter[6] > -1) {
			draw.blackRectangle(g, .9f);
			String text = "Press a key or Escape to cancel";
			draw.textCenteredWithTexturedImage(g, Color.YELLOW, tickcounter > 90, getHeight() / 2 - 27 / 2, text);
		}

		if (fadecounter != -2 && opacity == 0)
			opacity = 1;
		// tickFade(g);
	}

	public void tickControls(int keyCode) {
		if (counter[6] > -1) {
			if (keyCode != KeyEvent.VK_ESCAPE) {
				int otherButtonWithThisCode = -1;
				if (options.controllers[0].upButton == keyCode)
					otherButtonWithThisCode = 0;
				else if (options.controllers[0].downButton == keyCode)
					otherButtonWithThisCode = 1;
				else if (options.controllers[0].leftButton == keyCode)
					otherButtonWithThisCode = 2;
				else if (options.controllers[0].rightButton == keyCode)
					otherButtonWithThisCode = 3;
				else if (options.controllers[0].AButton == keyCode)
					otherButtonWithThisCode = 4;
				else if (options.controllers[0].BButton == keyCode)
					otherButtonWithThisCode = 5;
				else if (options.controllers[0].CButton == keyCode)
					otherButtonWithThisCode = 6;
				else if (options.controllers[0].LButton == keyCode)
					otherButtonWithThisCode = 7;
				else if (options.controllers[0].RButton == keyCode)
					otherButtonWithThisCode = 8;
				else if (chatButton == keyCode)
					otherButtonWithThisCode = 9;
				else if (options.controllers[1].upButton == keyCode)
					otherButtonWithThisCode = 10;
				else if (options.controllers[1].downButton == keyCode)
					otherButtonWithThisCode = 11;
				else if (options.controllers[1].leftButton == keyCode)
					otherButtonWithThisCode = 12;
				else if (options.controllers[1].rightButton == keyCode)
					otherButtonWithThisCode = 13;
				else if (options.controllers[1].AButton == keyCode)
					otherButtonWithThisCode = 14;
				else if (options.controllers[1].BButton == keyCode)
					otherButtonWithThisCode = 15;
				else if (options.controllers[1].CButton == keyCode)
					otherButtonWithThisCode = 16;
				else if (options.controllers[1].LButton == keyCode)
					otherButtonWithThisCode = 17;
				else if (options.controllers[1].RButton == keyCode)
					otherButtonWithThisCode = 18;
				String buttonName = null;
				if (counter[6] == 2) {
					setOtherButton(keyCode, options.controllers[(int) counter[0]].upButton, otherButtonWithThisCode);
					options.controllers[(int) counter[0]].upButton = keyCode;
					buttonName = "Up";
				} else if (counter[6] == 3) {
					setOtherButton(keyCode, options.controllers[(int) counter[0]].downButton, otherButtonWithThisCode);
					options.controllers[(int) counter[0]].downButton = keyCode;
					buttonName = "Down";
				} else if (counter[6] == 4) {
					setOtherButton(keyCode, options.controllers[(int) counter[0]].leftButton, otherButtonWithThisCode);
					options.controllers[(int) counter[0]].leftButton = keyCode;
					buttonName = "Left";
				} else if (counter[6] == 5) {
					setOtherButton(keyCode, options.controllers[(int) counter[0]].rightButton, otherButtonWithThisCode);
					options.controllers[(int) counter[0]].rightButton = keyCode;
					buttonName = "Right";
				} else if (counter[6] == 6) {
					setOtherButton(keyCode, options.controllers[(int) counter[0]].AButton, otherButtonWithThisCode);
					options.controllers[(int) counter[0]].AButton = keyCode;
					buttonName = "A";
				} else if (counter[6] == 7) {
					setOtherButton(keyCode, options.controllers[(int) counter[0]].BButton, otherButtonWithThisCode);
					options.controllers[(int) counter[0]].BButton = keyCode;
					buttonName = "B";
				} else if (counter[6] == 8) {
					setOtherButton(keyCode, options.controllers[(int) counter[0]].CButton, otherButtonWithThisCode);
					options.controllers[(int) counter[0]].CButton = keyCode;
					buttonName = "C";
				} else if (counter[6] == 9) {
					setOtherButton(keyCode, options.controllers[(int) counter[0]].LButton, otherButtonWithThisCode);
					options.controllers[(int) counter[0]].LButton = keyCode;
					buttonName = "L";
				} else if (counter[6] == 10) {
					setOtherButton(keyCode, options.controllers[(int) counter[0]].RButton, otherButtonWithThisCode);
					options.controllers[(int) counter[0]].RButton = keyCode;
					buttonName = "R";
				} else if (counter[6] == 11) {
					setOtherButton(keyCode, chatButton, otherButtonWithThisCode);
					chatButton = keyCode;
					buttonName = "Chat";
				}
				if (buttonName != null)
					logger.log(LogLevel.WARNING, "Switched Controller #" + (counter[0] + 1) + " " + buttonName
							+ " to \"" + KeyEvent.getKeyText(keyCode) + "\".");
			}
			counter[6] = -1;
			return;
		}
		if (keyCode == options.controllers[0].downButton || keyCode == options.controllers[0].upButton) {
			long tempCounter = upAndDownMenuResult(counter[1], keyCode, Constants.CONTROLS_MENU_OPTIONS.length, false,
					false);
			if (tempCounter < 2)
				tempCounter = 2;
			if (tempCounter != counter[1] && tempCounter != 0) {
				sound.play("Tick");
				counter[1] = tempCounter;
				tickcounter = 90;
			}
		} else if (keyCode == options.controllers[0].AButton || keyCode == options.controllers[0].CButton) {
			if (counter[1] == Constants.CONTROLS_MENU_OPTIONS.length - 1) {
				setPause(Pause.PAINT);
				counter[1] = 3;
				state.setNextStateAfterFade(StateEnum.MAIN_MENU, this);
				for (int i = 0; i < controlsBgImages.length; i++)
					if (controlsBgImages[i] != null) {
						controlsBgImages[i].flush();
						controlsBgImages[i] = null;
					}
				setPause(Pause.NONE);
			} else {
				counter[6] = counter[1];
			}
		} else if (keyCode == options.controllers[0].BButton) {
			counter[1] = Constants.CONTROLS_MENU_OPTIONS.length - 1;
			tickControls(options.controllers[0].CButton);
		} else if (keyCode == options.controllers[0].leftButton) {
			if (counter[0] == 1) {
				sound.play("Tick");
				counter[0] = 0;
				tickcounter = 90;
			}
		} else if (keyCode == options.controllers[0].rightButton) {
			if (counter[0] == 0) {
				sound.play("Tick");
				counter[0] = 1;
				tickcounter = 90;
			}
		}
	}

	public void tickMatchSelection() {
		if (!temp[1].equals("Played Selection")) {
			temp[1] = "Played Selection";
			music.loop("Selection");
		}
		fadecounterenabled = 0;
		if (fadecounter != -2) {
			if (fadecounter <= HIGHEST_FADE_COUNTER)
				fadeIn();
			if (fadecounter > HIGHEST_FADE_COUNTER)
				fadecounter = HIGHEST_FADE_COUNTER + 1;
		}
		counter[2]++;
		if (counter[2] > 260) {
			counter[2] = 0;
		}
		if (counter[0] == 11 && players.size() == 0) {
			for (int i = 0; i < options.players.length; i++) {
				if (options.players[i] == 1) {
					players.add(new Player(this));
					if (!network.isPlayingOnline() || i == network.myInputTurnP1 || i == network.myInputTurnP2)
						players.get(players.size() - 1).isLocal = true;
				} else if (options.players[i] == 2) {
					Player p = new Player(this);
					p.isCpu = true;
					players.add(p);
				}
				players.get(players.size() - 1).setIndex(players.size() - 1);
			}
		} else if (counter[0] == 11 && counter[5] != network.inputTurn
				&& (counter[5] == network.myInputTurnP1 || counter[5] == network.myInputTurnP2))
			network.inputTurn = (int) counter[5];
		if (counter[0] == 1 || counter[0] == 4 || counter[0] == 7 || counter[0] == 10 || counter[0] == 13
				|| counter[0] == 16) {
			counter[4]++;
			if ((counter[4] > 25 && counter[0] != 16) || (counter[4] > 50 && counter[0] == 16)) {
				if (counter[0] == 1) {
					counter[0] = 2;
					counter[1] = 0;
					if (options.battleOptionsFinished)
						counter[1] = 1;
					if (options.rulesOptionsFinished)
						counter[1] = 2;
					if (options.bombersOptionsFinished && options.teamMode)
						counter[1] = 3;
					if (options.bombersOptionsFinished && !options.teamMode)
						counter[1] = 4;
				} else
					counter[0]++;
			}
		}
		if (counter[0] == 3 || counter[0] == 6 || counter[0] == 9 || counter[0] == 12 || counter[0] == 15) {
			counter[4]--;
			if (counter[4] < 1) {
				counter[4] = 0;
				if (counter[0] == 3)
					counter[0] = 0;
				else
					counter[0] = 2;
			}
		}
		if (counter[0] == 18) {
			counter[4]--;
			if (counter[4] < 1) {
				counter[4] = 0;
				if (counter[6] == 1) {
					pause = Pause.PAINT;
					counter[1] = 1;
					counter[6] = 0;
					tickcounter = 0;
					state.set(StateEnum.END_OF_MATCH, this);
					pause = Pause.NONE;
					if (network.weAreHost())
						for (int i = 0; i < network.connectedClients.size(); i++)
							if (network.connectedClients.get(i)[0].equals(options.multiplayer.username))
								network.connectedClients.set(i, new String[] { network.connectedClients.get(i)[0],
										network.connectedClients.get(i)[1], "0" });
				}
				counter[0] = 2;
			}
		}
		if (counter[0] == 19) {
			counter[4]--;
			if (counter[4] < 0) {
				setPause(Pause.ALL);
				counter[0] = 0;
				counter[6] = counter[1];
				if (!network.isPlayingOnline()) {
					// counter[1] = 0;
					counter[2] = currentTimeMillis();
				} else {
					counter[1] = -1;
					counter[2] = -1;
				}
				counter[3] = 0;
				counter[4] = 0;
				counter[5] = 0;
				counter[7] = 0;
				while (objects.size() > 0)
					objects.remove(0);
				for (int i = 0; i < players.size(); i++) {
					players.get(i).character.animation = Animation.IDLE;
					players.get(i).character.animationTick = 0;
					objects.add(players.get(i));
				}
				state.setNextStateAfterFade(StateEnum.GOING_TO_STAGE, this);
				setPause(Pause.NONE);
				return;
			}
		}
		int num = 0, counterToSet = 0;
		while (true) {
			num += 4;
			if (counter[2] < num) {
				counter[3] = counterToSet;
				break;
			}
			counterToSet++;
			if (counterToSet > 5)
				counterToSet = 0;
		}
		if (counter[0] == 11) {
			if (counter[5] == -1) {
				counter[5] = 0;
				counter[6] = 0;
			}
		}
		if (counter[0] == 10 || counter[0] == 11 || counter[0] == 12) {
			for (int i = 0; i < players.size(); i++)
				players.get(i).tick(this);
		}
		float rotateSize = 360;
		if (counter[0] == 17 && counter[5] > 0) {
			counter[5]--;
			counter[1]++;
			if (counter[1] >= rotateSize)
				counter[1] -= rotateSize;
		} else if (counter[0] == 17 && counter[5] < 0) {
			counter[5]++;
			counter[1]--;
			if (counter[1] <= 0)
				counter[1] += rotateSize;
		}
	}

	public void tickMatchSelection(Graphics2D g) {
		draw.rectangle(g, 100, 20, 600, 400, new Color(184, 200, 208));
		for (int i = 0; i < 6; i++)
			for (int j = 0; j < 6; j++) {
				long x = 200 + i * 130 - counter[2];
				long y = j * 130 - counter[2];
				if (Tools.isOdd(i))
					y += 130 / 2;
				g.drawImage(pictures.selectionBombBackground, (int) x, (int) y, this);
			}
		if (counter[0] >= 2 && counter[0] <= 15) {
			g.drawImage(pictures.selectionBackground, 8, 0, this);

			g.drawImage(pictures.selectionButtons.get(3).get(0), 39, 45, this);
			if (options.battleOptionsFinished)
				g.drawImage(pictures.selectionButtons.get(3).get(1), 39, 93, this);
			else
				g.drawImage(Pictures.darken(pictures.selectionButtons.get(3).get(1)), 39, 93, this);
			if (options.rulesOptionsFinished)
				g.drawImage(pictures.selectionButtons.get(3).get(2), 39, 141, this);
			else
				g.drawImage(Pictures.darken(pictures.selectionButtons.get(3).get(2)), 39, 141, this);
			if (options.bombersOptionsFinished && options.teamMode)
				g.drawImage(pictures.selectionButtons.get(3).get(3), 39, 189, this);
			else
				g.drawImage(Pictures.darken(pictures.selectionButtons.get(3).get(3)), 39, 189, this);
			g.drawImage(pictures.selectionButtons.get(4).get(0), 39, 319, this);
			if ((options.teamOptionsFinished && options.teamMode)
					|| (options.bombersOptionsFinished && !options.teamMode))
				g.drawImage(pictures.selectionButtons.get(4).get(1), 39, 366, this);
			else
				g.drawImage(Pictures.darken(pictures.selectionButtons.get(4).get(1)), 39, 366, this);
			g.drawImage(pictures.selectionBackground2, -2, -2, this);
		}
		if (counter[0] == 0) {
			g.drawImage(pictures.selectionButtons.get(1).get(0), 170, 42, this);
			g.drawImage(pictures.selectionButtons.get(0).get(0), 207, 109, this);
			g.drawImage(Pictures.darken(pictures.selectionButtons.get(0).get(1)), 207, 172, this);
			g.drawImage(pictures.selectionButtons.get(0).get(2), 207, 350, this);
			g.drawImage(pictures.selectionButtons.get(2).get(0), 383, 350, this);
			if (counter[1] == 0) {
				if ((tickcounter >= 25 && tickcounter < 50) || tickcounter >= 75)
					g.drawImage(pictures.selectionButtonSelector.get(0).get(1), 203, 107, this);
				g.drawImage(pictures.selectionSelector.get(0).get((int) counter[3]), 266, 98, this);
			} else if (counter[1] == 1) {
				if ((tickcounter >= 25 && tickcounter < 50) || tickcounter >= 75)
					g.drawImage(pictures.selectionButtonSelector.get(0).get(1), 203, 170, this);
				g.drawImage(pictures.selectionSelector.get(0).get((int) counter[3]), 266, 161, this);
			} else if (counter[1] == 2) {
				if ((tickcounter >= 25 && tickcounter < 50) || tickcounter >= 75)
					g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 203, 348, this);
				g.drawImage(pictures.selectionSelector.get(0).get((int) counter[3]), 250, 339, this);
			}
			g.drawImage(pictures.selectionBackground2, -2, -2, this);
			g.drawImage(pictures.selectionBackground, 8, 0, this);
		} else if (counter[0] == 1) {
			g.drawImage(pictures.selectionButtons.get(1).get(0), 170 + (int) counter[4] * 20, 42, this);
			g.drawImage(pictures.selectionButtons.get(0).get(0), 207 + (int) counter[4] * 20, 109, this);
			g.drawImage(Pictures.darken(pictures.selectionButtons.get(0).get(1)), 207 + (int) counter[4] * 20, 172,
					this);
			g.drawImage(Pictures.darken(pictures.selectionButtons.get(0).get(2)), 207 + (int) counter[4] * 20, 350,
					this);
			g.drawImage(pictures.selectionButtons.get(2).get(0), 383 + (int) counter[4] * 20, 350, this);
			g.drawImage(pictures.selectionSelector.get(0).get(0), 266 + (int) counter[4] * 20, 98, this);
			g.drawImage(pictures.selectionBackground2, -2, -2, this);
			g.drawImage(pictures.selectionBackground, 8, 0, this);
		} else if (counter[0] == 2) {
			if (counter[1] == 0) {
				if ((tickcounter >= 25 && tickcounter < 50) || tickcounter >= 75)
					g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 35, 43, this);
				if (options.battleOptionsFinished)
					g.drawImage(pictures.selectionSelector.get(0).get((int) counter[3]), 82, 34, this);
			} else if (counter[1] == 1) {
				if ((tickcounter >= 25 && tickcounter < 50) || tickcounter >= 75)
					g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 35, 91, this);
				g.drawImage(pictures.selectionSelector.get(0).get((int) counter[3]), 82, 82, this);
			} else if (counter[1] == 2) {
				if ((tickcounter >= 25 && tickcounter < 50) || tickcounter >= 75)
					g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 35, 139, this);
				g.drawImage(pictures.selectionSelector.get(0).get((int) counter[3]), 82, 130, this);
			} else if (counter[1] == 3) {
				if ((tickcounter >= 25 && tickcounter < 50) || tickcounter >= 75)
					g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 35, 187, this);
				g.drawImage(pictures.selectionSelector.get(0).get((int) counter[3]), 82, 178, this);
			} else if (counter[1] == 4) {
				if ((tickcounter >= 25 && tickcounter < 50) || tickcounter >= 75)
					g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 35, 362, this);
				g.drawImage(pictures.selectionSelector.get(0).get((int) counter[3]), 82, 353, this);
			}
		} else if (counter[0] == 3) {
			g.drawImage(pictures.selectionButtons.get(1).get(0), 170 + (int) counter[4] * 20, 42, this);
			g.drawImage(pictures.selectionButtons.get(0).get(0), 207 + (int) counter[4] * 20, 109, this);
			g.drawImage(Pictures.darken(pictures.selectionButtons.get(0).get(1)), 207 + (int) counter[4] * 20, 172,
					this);
			g.drawImage(Pictures.darken(pictures.selectionButtons.get(0).get(2)), 207 + (int) counter[4] * 20, 350,
					this);
			g.drawImage(pictures.selectionButtons.get(2).get(0), 383 + (int) counter[4] * 20, 350, this);
			g.drawImage(pictures.selectionSelector.get(0).get(0), 266 + (int) counter[4] * 20, 98, this);
			g.drawImage(pictures.selectionBackground2, -2, -2, this);
			g.drawImage(pictures.selectionBackground, 8, 0, this);
		} else if (counter[0] == 4) {
			g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 35, 43, this);
			g.drawImage(pictures.selectionButtons.get(5).get(0), 170 + 500 - (int) counter[4] * 20, 42, this);
			g.drawImage(pictures.selectionButtons.get(5).get(1), 330 + 500 - (int) counter[4] * 20, 42, this);
			g.drawImage(Pictures.darken(pictures.selectionButtons.get(6).get(0)), 460 + 500 - (int) counter[4] * 20, 42,
					this);
			g.drawImage(pictures.selectionButtons.get(5).get(2), 170 + 500 - (int) counter[4] * 20, 90, this);
			g.drawImage(pictures.selectionButtons.get(5).get(3), 330 + 500 - (int) counter[4] * 20, 90, this);
			g.drawImage(Pictures.darken(pictures.selectionButtons.get(6).get(1)), 460 + 500 - (int) counter[4] * 20, 90,
					this);
			g.drawImage(pictures.selectionButtons.get(6).get(2), 170 + 500 - (int) counter[4] * 20, 138, this);
			if (options.wideMode) {
				g.drawImage(pictures.selectionButtons.get(8).get(0), 386 + 500 - (int) counter[4] * 20, 288, this);
				g.drawImage(pictures.selectionButtons.get(9).get(0), 506 + 500 - (int) counter[4] * 20, 288, this);
				g.drawImage(pictures.selectionButtons.get(9).get(0), 506 + 500 - (int) counter[4] * 20, 320, this);
			} else {
				g.drawImage(Pictures.darken(pictures.selectionButtons.get(8).get(0)), 386 + 500 - (int) counter[4] * 20,
						288, this);
				g.drawImage(Pictures.darken(pictures.selectionButtons.get(9).get(0)), 506 + 500 - (int) counter[4] * 20,
						288, this);
				g.drawImage(Pictures.darken(pictures.selectionButtons.get(9).get(0)), 506 + 500 - (int) counter[4] * 20,
						320, this);
			}
			g.drawImage(pictures.selectionButtons.get(10).get(0), 170 + 500 - (int) counter[4] * 20, 366, this);
			int x = 296, y = 192;
			for (int i = 0; i < 8; i++) {
				g.drawImage(pictures.selectionButtons.get(9).get(options.players[i]), x + 500 - (int) counter[4] * 20,
						y, this);

				y += 32;
				if (i == 4) {
					x = 506;
					y = 192;
				}
			}
			g.drawImage(pictures.selectionButtons.get(7).get(0), 170 + 500 - (int) counter[4] * 20, 186, this);
			g.drawImage(pictures.selectionButtons.get(7).get(1), 380 + 500 - (int) counter[4] * 20, 186, this);
			g.drawImage(pictures.selectionButtons.get(2).get(0), 383 + 500 - (int) counter[4] * 20, 350, this);
			g.drawImage(pictures.selectionBackground2, -2, -2, this);
		} else if (counter[0] == 5) {
			g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 35, 43, this);
			g.drawImage(pictures.selectionButtons.get(5).get(0), 170, 42, this);
			g.drawImage(pictures.selectionButtons.get(5).get(1), 330, 42, this);
			g.drawImage(Pictures.darken(pictures.selectionButtons.get(6).get(0)), 460, 42, this);
			g.drawImage(pictures.selectionButtons.get(5).get(2), 170, 90, this);
			g.drawImage(pictures.selectionButtons.get(5).get(3), 330, 90, this);
			g.drawImage(Pictures.darken(pictures.selectionButtons.get(6).get(1)), 460, 90, this);
			g.drawImage(pictures.selectionButtons.get(6).get(2), 170, 138, this);
			if (options.wideMode) {
				g.drawImage(pictures.selectionButtons.get(8).get(0), 386, 288, this);
				g.drawImage(pictures.selectionButtons.get(9).get(0), 506, 288, this);
				g.drawImage(pictures.selectionButtons.get(9).get(0), 506, 320, this);
			} else {
				g.drawImage(Pictures.darken(pictures.selectionButtons.get(8).get(0)), 386, 288, this);
				g.drawImage(Pictures.darken(pictures.selectionButtons.get(9).get(0)), 506, 288, this);
				g.drawImage(Pictures.darken(pictures.selectionButtons.get(9).get(0)), 506, 320, this);
			}
			g.drawImage(pictures.selectionButtons.get(10).get(0), 170, 366, this);
			int x = 296, y = 192;
			for (int i = 0; i < 8; i++) {
				g.drawImage(pictures.selectionButtons.get(9).get(options.players[i]), x, y, this);

				y += 32;
				if (i == 4) {
					x = 506;
					y = 192;
				}
			}
			g.drawImage(pictures.selectionButtons.get(7).get(0), 170, 186, this);
			g.drawImage(pictures.selectionButtons.get(7).get(1), 380, 186, this);
			if (counter[1] == 0) {
				if ((tickcounter >= 25 && tickcounter < 50) || tickcounter >= 75)
					g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 166, 40, this);
				g.drawImage(pictures.selectionSelector.get(0).get((int) counter[3]), 213, 31, this);
			} else if (counter[1] == 1) {
				if ((tickcounter >= 25 && tickcounter < 50) || tickcounter >= 75)
					g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 326, 40, this);
			} else if (counter[1] == 2) {
				if ((tickcounter >= 25 && tickcounter < 50) || tickcounter >= 75)
					g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 400, 40, this);
			} else if (counter[1] == 3) {
				if ((tickcounter >= 25 && tickcounter < 50) || tickcounter >= 75)
					g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 166, 88, this);
				g.drawImage(pictures.selectionSelector.get(0).get((int) counter[3]), 213, 79, this);
			} else if (counter[1] == 4) {
				if ((tickcounter >= 25 && tickcounter < 50) || tickcounter >= 75)
					g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 326, 88, this);
			} else if (counter[1] == 5) {
				if ((tickcounter >= 25 && tickcounter < 50) || tickcounter >= 75)
					g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 400, 88, this);
			} else if (counter[1] == 6) {
				if ((tickcounter >= 25 && tickcounter < 50) || tickcounter >= 75)
					g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 166, 136, this);
				g.drawImage(pictures.selectionSelector.get(0).get((int) counter[3]), 213, 127, this);
			} else if (counter[1] >= 7 && counter[1] <= 16) {
				int player = (int) counter[1] - 7;
				x = 160;
				y = 194 + (32 * player);
				if (counter[1] >= 12) {
					x += 210;
					y -= 160;
				}
				if ((tickcounter >= 25 && tickcounter < 50) || tickcounter >= 75)
					g.drawImage(pictures.selectionButtonSelector.get(0).get(3), x + 6, y - 9, this);
				g.drawImage(pictures.selectionSelector.get(0).get((int) counter[3]), x + 94, y - 17, this);
				if (network.isPlayingOnline()) {
					if (player >= network.getTotalPlayersAmt())
						g.drawImage(pictures.selectionSelector.get((int) counter[3] + 1).get(0), x, y, this);
				} else
					g.drawImage(pictures.selectionSelector.get((int) counter[3] + 1).get(0), x, y, this);
			} else if (counter[1] == 17) {
				if ((tickcounter >= 25 && tickcounter < 50) || tickcounter >= 75)
					g.drawImage(pictures.selectionButtonSelector.get(0).get(2), 166, 360, this);
				g.drawImage(pictures.selectionSelector.get(0).get((int) counter[3]), 190, 354, this);
			}
			g.drawImage(pictures.selectionButtons.get(2).get(0), 383, 350, this);
		} else if (counter[0] == 6) {
			g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 35, 43, this);
			g.drawImage(pictures.selectionButtons.get(5).get(0), 170 + 500 - (int) counter[4] * 20, 42, this);
			g.drawImage(pictures.selectionButtons.get(5).get(1), 330 + 500 - (int) counter[4] * 20, 42, this);
			g.drawImage(Pictures.darken(pictures.selectionButtons.get(6).get(0)), 460 + 500 - (int) counter[4] * 20, 42,
					this);
			g.drawImage(pictures.selectionButtons.get(5).get(2), 170 + 500 - (int) counter[4] * 20, 90, this);
			g.drawImage(pictures.selectionButtons.get(5).get(3), 330 + 500 - (int) counter[4] * 20, 90, this);
			g.drawImage(Pictures.darken(pictures.selectionButtons.get(6).get(1)), 460 + 500 - (int) counter[4] * 20, 90,
					this);
			g.drawImage(pictures.selectionButtons.get(6).get(2), 170 + 500 - (int) counter[4] * 20, 138, this);
			g.drawImage(pictures.selectionButtons.get(7).get(0), 170 + 500 - (int) counter[4] * 20, 186, this);
			g.drawImage(pictures.selectionButtons.get(7).get(1), 380 + 500 - (int) counter[4] * 20, 186, this);
			if (options.wideMode) {
				g.drawImage(pictures.selectionButtons.get(8).get(0), 386 + 500 - (int) counter[4] * 20, 288, this);
				g.drawImage(pictures.selectionButtons.get(9).get(0), 506 + 500 - (int) counter[4] * 20, 288, this);
				g.drawImage(pictures.selectionButtons.get(9).get(0), 506 + 500 - (int) counter[4] * 20, 320, this);
			} else {
				g.drawImage(Pictures.darken(pictures.selectionButtons.get(8).get(0)), 386 + 500 - (int) counter[4] * 20,
						288, this);
				g.drawImage(Pictures.darken(pictures.selectionButtons.get(9).get(0)), 506 + 500 - (int) counter[4] * 20,
						288, this);
				g.drawImage(Pictures.darken(pictures.selectionButtons.get(9).get(0)), 506 + 500 - (int) counter[4] * 20,
						320, this);
			}
			g.drawImage(pictures.selectionButtons.get(10).get(0), 170 + 500 - (int) counter[4] * 20, 366, this);
			int x = 296, y = 192;
			for (int i = 0; i < 8; i++) {
				g.drawImage(pictures.selectionButtons.get(9).get(options.players[i]), x + 500 - (int) counter[4] * 20,
						y, this);

				y += 32;
				if (i == 4) {
					x = 506;
					y = 192;
				}
			}
			g.drawImage(pictures.selectionButtons.get(2).get(0), 383 + 500 - (int) counter[4] * 20, 350, this);
			g.drawImage(pictures.selectionBackground2, -2, -2, this);
		} else if (counter[0] == 7) {
			g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 35, 91, this);
			g.drawImage(pictures.selectionButtons.get(3).get(1), 170 + 500 - (int) counter[4] * 20, 42, this);
			g.drawImage(pictures.selectionButtons.get(6).get(3), 170 + 500 - (int) counter[4] * 20, 300, this);
			g.drawImage(pictures.selectionButtons.get(9).get(options.numberOfBattles + 3),
					510 + 500 - (int) counter[4] * 20, 48, this);
			g.drawImage(pictures.selectionButtons.get(12).get((int) options.time - 1),
					510 + 500 - (int) counter[4] * 20, 80, this);
			g.drawImage(pictures.selectionButtons.get(9).get(options.shuffle ? 3 : 0),
					510 + 500 - (int) counter[4] * 20, 113, this);
			g.drawImage(Pictures.darken(pictures.selectionButtons.get(9).get(options.noDraw ? 3 : 0)),
					510 + 500 - (int) counter[4] * 20, 146, this);
			g.drawImage(Pictures.darken(pictures.selectionButtons.get(9).get(options.devil ? 3 : 0)),
					510 + 500 - (int) counter[4] * 20, 178, this);
			g.drawImage(pictures.selectionButtons.get(9).get(options.madBomber ? 3 : 0),
					510 + 500 - (int) counter[4] * 20, 210, this);
			g.drawImage(Pictures.darken(pictures.selectionButtons.get(9).get(options.bonusGame ? 3 : 0)),
					510 + 500 - (int) counter[4] * 20, 242, this);
			g.drawImage(pictures.selectionButtons.get(11).get(0), 320 + 500 - (int) counter[4] * 20, 42, this);
			g.drawImage(pictures.selectionButtons.get(13).get(options.comLevel - 1), 383 + 500 - (int) counter[4] * 20,
					300, this);
			g.drawImage(pictures.selectionButtons.get(10).get(0), 170 + 500 - (int) counter[4] * 20, 366, this);
			g.drawImage(pictures.selectionButtons.get(2).get(0), 383 + 500 - (int) counter[4] * 20, 350, this);
			g.drawImage(pictures.selectionButtons.get(2).get(0), 383 + 500 - (int) counter[4] * 20, 350, this);
			g.drawImage(pictures.selectionBackground2, -2, -2, this);
		} else if (counter[0] == 8) {
			g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 35, 91, this);
			g.drawImage(pictures.selectionButtons.get(3).get(1), 170, 42, this);
			g.drawImage(pictures.selectionButtons.get(6).get(3), 170, 300, this);
			g.drawImage(pictures.selectionButtons.get(9).get(options.numberOfBattles + 3), 510, 48, this);
			g.drawImage(pictures.selectionButtons.get(12).get((int) options.time - 1), 510, 80, this);
			g.drawImage(pictures.selectionButtons.get(9).get(options.shuffle ? 3 : 0), 510, 113, this);
			g.drawImage(Pictures.darken(pictures.selectionButtons.get(9).get(options.noDraw ? 3 : 0)), 510, 146, this);
			g.drawImage(Pictures.darken(pictures.selectionButtons.get(9).get(options.devil ? 3 : 0)), 510, 178, this);
			g.drawImage(pictures.selectionButtons.get(9).get(options.madBomber ? 3 : 0), 510, 210, this);
			g.drawImage(Pictures.darken(pictures.selectionButtons.get(9).get(options.bonusGame ? 3 : 0)), 510, 242,
					this);
			g.drawImage(pictures.selectionButtons.get(11).get(0), 320, 42, this);
			g.drawImage(pictures.selectionButtons.get(13).get(options.comLevel - 1), 383, 300, this);
			if (counter[1] == 0) {
				if ((tickcounter >= 25 && tickcounter < 50) || tickcounter >= 75)
					g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 165, 37, this);
				g.drawImage(pictures.selectionSelector.get(0).get((int) counter[3]), 213, 31, this);
			} else if (counter[1] >= 1 && counter[1] <= 7) {
				g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 165, 37, this);
				int x = 310;
				int y = 50 + (32 * ((int) counter[1] - 1));
				if (counter[1] > 2)
					y += 2;
				if ((tickcounter >= 25 && tickcounter < 50) || tickcounter >= 75)
					g.drawImage(pictures.selectionButtonSelector.get(0).get(5), x + 6, y - 9, this);
				g.drawImage(pictures.selectionSelector.get((int) counter[3] + 13).get(0), x, y, this);
				g.drawImage(pictures.selectionSelector.get(0).get((int) counter[3]), x + 126, y - 16, this);
			} else if (counter[1] == 8) {
				if ((tickcounter >= 25 && tickcounter < 50) || tickcounter >= 75)
					g.drawImage(pictures.selectionButtonSelector.get(0).get(1), 165, 295, this);
				g.drawImage(pictures.selectionSelector.get(0).get((int) counter[3]), 230, 289, this);
			} else if (counter[1] == 9) {
				if ((tickcounter >= 25 && tickcounter < 50) || tickcounter >= 75)
					g.drawImage(pictures.selectionButtonSelector.get(0).get(4), 379, 295, this);
				g.drawImage(pictures.selectionSelector.get((int) counter[3] + 7).get(0), 373, 304, this);
			} else if (counter[1] == 10) {
				if ((tickcounter >= 25 && tickcounter < 50) || tickcounter >= 75)
					g.drawImage(pictures.selectionButtonSelector.get(0).get(2), 166, 360, this);
				g.drawImage(pictures.selectionSelector.get(0).get((int) counter[3]), 190, 354, this);
			}
			g.drawImage(pictures.selectionButtons.get(10).get(0), 170, 366, this);
			g.drawImage(pictures.selectionButtons.get(2).get(0), 383, 350, this);
		} else if (counter[0] == 9) {
			g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 35, 91, this);
			g.drawImage(pictures.selectionButtons.get(3).get(1), 170 + 500 - (int) counter[4] * 20, 42, this);
			g.drawImage(pictures.selectionButtons.get(6).get(3), 170 + 500 - (int) counter[4] * 20, 300, this);
			g.drawImage(pictures.selectionButtons.get(9).get(options.numberOfBattles + 3),
					510 + 500 - (int) counter[4] * 20, 48, this);
			g.drawImage(pictures.selectionButtons.get(12).get((int) options.time - 1),
					510 + 500 - (int) counter[4] * 20, 80, this);
			g.drawImage(pictures.selectionButtons.get(9).get(options.shuffle ? 3 : 0),
					510 + 500 - (int) counter[4] * 20, 113, this);
			g.drawImage(Pictures.darken(pictures.selectionButtons.get(9).get(options.noDraw ? 3 : 0)),
					510 + 500 - (int) counter[4] * 20, 146, this);
			g.drawImage(Pictures.darken(pictures.selectionButtons.get(9).get(options.devil ? 3 : 0)),
					510 + 500 - (int) counter[4] * 20, 178, this);
			g.drawImage(pictures.selectionButtons.get(9).get(options.madBomber ? 3 : 0),
					510 + 500 - (int) counter[4] * 20, 210, this);
			g.drawImage(Pictures.darken(pictures.selectionButtons.get(9).get(options.bonusGame ? 3 : 0)),
					510 + 500 - (int) counter[4] * 20, 242, this);
			g.drawImage(pictures.selectionButtons.get(11).get(0), 320 + 500 - (int) counter[4] * 20, 42, this);
			g.drawImage(pictures.selectionButtons.get(13).get(options.comLevel - 1), 383 + 500 - (int) counter[4] * 20,
					300, this);
			g.drawImage(pictures.selectionButtons.get(10).get(0), 170 + 500 - (int) counter[4] * 20, 366, this);
			g.drawImage(pictures.selectionButtons.get(2).get(0), 383 + 500 - (int) counter[4] * 20, 350, this);
			g.drawImage(pictures.selectionButtons.get(2).get(0), 383 + 500 - (int) counter[4] * 20, 350, this);
			g.drawImage(pictures.selectionBackground2, -2, -2, this);

		} else if (counter[0] == 10) {
			g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 35, 139, this);
			g.drawImage(pictures.selectionBomberBackground, 160, 32, this);
			boolean[] playerChosen = new boolean[12];
			for (int i = 0; i < playerChosen.length; i++)
				for (int j = 0; j < players.size(); j++)
					if (players.get(j).character != null && players.get(j).character.equals(i))
						playerChosen[i] = true;
			int i = 0;
			int[] iDrawOrder = { 1, 3, 5, 6, 8, 10, 0, 2, 4, 7, 9, 11 };
			for (int j = 0; j < pictures.characters.size(); j++) {
				i = iDrawOrder[j];
				if (!playerChosen[i]) {
					g.drawImage(pictures.selectionBomberTiles.get(0).get(0), 177 + 32 * i + (i >= 6 ? 16 : 0),
							380 - (Tools.isEven(i) ? (i >= 6 ? 32 : 0) : (i >= 6 ? 0 : 32)), this);
					new Character(pictures, i).draw(g, 186 + 32 * i + (i >= 6 ? 16 : 0),
							335 - (Tools.isEven(i) ? (i >= 6 ? 32 : 0) : (i >= 6 ? 0 : 32)), this);
				}
				for (int k = 0; k < players.size(); k++)
					if (players.get(k).character != null && players.get(k).character.characterIndex == i
							&& !players.get(k).character.animation.equals(Animation.BOMBER_SELECT_IDLE))
						players.get(k).character.draw(g, 186 + 32 * i + (i >= 6 ? 16 : 0),
								335 - (Tools.isEven(i) ? (i >= 6 ? 32 : 0) : (i >= 6 ? 0 : 32)), this);
				g.drawImage(pictures.selectionBomberTiles.get(1).get(0), 286 + 500 - (int) counter[4] * 20, 44, this);
				if (counter[5] != players.size() || players.size() == 0) {
					g.drawImage(pictures.selectionBomberTiles.get(2 + (int) counter[5]).get(0),
							222 + 500 - (int) counter[4] * 20, 92, this);
					g.drawImage(pictures.characters.get((int) counter[1]).get(0).get(0),
							412 + 500 - (int) counter[4] * 20, 92, this);
				} else {
					g.drawImage(pictures.selectionButtons.get(10).get(0), 353 + 500 - (int) counter[4] * 20, 96, this);
				}
			}
			g.drawImage(pictures.selectionBomberBackground2, 160, 238, this);
			if (counter[5] != players.size() || players.size() == 0)
				for (int j = 0; j < pictures.characters.size(); j++) {
					i = iDrawOrder[j];
					if (counter[1] == i)
						g.drawImage(pictures.selectionBomberTiles.get(12).get((int) counter[5]),
								190 + 32 * i + (i >= 6 ? 16 : 0) + 500 - (int) counter[4] * 20,
								323 - (Tools.isEven(i) ? (i >= 6 ? 32 : 0) : (i >= 6 ? 0 : 32)), this);
				}
			for (int j = 0; j < pictures.characters.size(); j++) {
				i = iDrawOrder[j];
				for (int k = 0; k < players.size(); k++)
					if (players.get(k).character != null && players.get(k).character.characterIndex == i
							&& players.get(k).character.animation.equals(Animation.BOMBER_SELECT_IDLE))
						players.get(k).character.draw(g, 186 + 32 * i + (i >= 6 ? 16 : 0),
								335 - (Tools.isEven(i) ? (i >= 6 ? 32 : 0) : (i >= 6 ? 0 : 32)), this);
			}

		} else if (counter[0] == 11) {
			if (players.size() == 0) {
				if (network.isPlayingOnline())
					tickGoingToStage(g);
				else
					tickLoading(g);
				return;
			}
			g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 35, 139, this);
			g.drawImage(pictures.selectionBomberBackground, 160, 32, this);
			boolean[] playerChosen = new boolean[12];
			for (int i = 0; i < playerChosen.length; i++)
				for (int j = 0; j < players.size(); j++)
					if (players.get(j).character != null && players.get(j).character.equals(i))
						playerChosen[i] = true;
			int i = 0;
			int[] iDrawOrder = { 1, 3, 5, 6, 8, 10, 0, 2, 4, 7, 9, 11 };
			for (int j = 0; j < pictures.characters.size(); j++) {
				i = iDrawOrder[j];
				if (!playerChosen[i]) {
					g.drawImage(pictures.selectionBomberTiles.get(0).get(0), 177 + 32 * i + (i >= 6 ? 16 : 0),
							380 - (Tools.isEven(i) ? (i >= 6 ? 32 : 0) : (i >= 6 ? 0 : 32)), this);
					new Character(pictures, i).draw(g, 186 + 32 * i + (i >= 6 ? 16 : 0),
							335 - (Tools.isEven(i) ? (i >= 6 ? 32 : 0) : (i >= 6 ? 0 : 32)), this);
				}
				for (int k = 0; k < players.size(); k++)
					if (players.get(k).character != null && players.get(k).character.characterIndex == i
							&& !players.get(k).character.animation.equals(Animation.BOMBER_SELECT_IDLE))
						players.get(k).character.draw(g, 186 + 32 * i + (i >= 6 ? 16 : 0),
								335 - (Tools.isEven(i) ? (i >= 6 ? 32 : 0) : (i >= 6 ? 0 : 32)), this);
				g.drawImage(pictures.selectionBomberTiles.get(1).get(0), 286, 44, this);
				if (counter[5] != players.size()) {
					g.drawImage(pictures.selectionBomberTiles.get(2 + (int) counter[5]).get(0), 222, 92, this);
					g.drawImage(pictures.characters.get((int) counter[1]).get(0).get(0), 412, 92, this);
					if (network.isPlayingOnline()) {
						String name = "CPU";
						try {
							name = network.connectedClients.get((int) counter[5])[0];
						} catch (Exception e) {
							// no player
						}
						g.setColor(Color.BLACK);
						int width = Tools.getTextWidth(g, name) / 2;
						// g.drawString(name, 380 - width + 1, 154 + 1);
						// g.drawString(name, 380 - width - 1, 154 - 1);
						draw.textWithTexturedImage(g, Color.RED, false, 380 - width, 154, name);
					}
				} else {
					g.drawImage(pictures.selectionButtons.get(10).get(0), 353, 96, this);
					if ((tickcounter >= 25 && tickcounter < 50) || tickcounter >= 75)
						g.drawImage(pictures.selectionButtonSelector.get(0).get(2), 349, 90, this);
				}
			}
			g.drawImage(pictures.selectionBomberBackground2, 160, 238, this);
			if (counter[5] != players.size())
				for (int j = 0; j < pictures.characters.size(); j++) {
					i = iDrawOrder[j];
					if (counter[1] == i)
						g.drawImage(pictures.selectionBomberTiles.get(12).get((int) counter[5]),
								190 + 32 * i + (i >= 6 ? 16 : 0),
								323 - (Tools.isEven(i) ? (i >= 6 ? 32 : 0) : (i >= 6 ? 0 : 32)), this);
				}
			for (int j = 0; j < pictures.characters.size(); j++) {
				i = iDrawOrder[j];
				for (int k = 0; k < players.size(); k++)
					if (players.get(k).character != null && players.get(k).character.characterIndex == i
							&& players.get(k).character.animation.equals(Animation.BOMBER_SELECT_IDLE))
						players.get(k).character.draw(g, 186 + 32 * i + (i >= 6 ? 16 : 0),
								335 - (Tools.isEven(i) ? (i >= 6 ? 32 : 0) : (i >= 6 ? 0 : 32)), this);
			}
		} else if (counter[0] == 12) {
			for (int i = 0; i < players.size(); i++)
				players.get(i).character.animation = Animation.BOMBER_SELECT_WALKING;
			g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 35, 139, this);
			g.drawImage(pictures.selectionBomberBackground, 160, 32, this);
			boolean[] playerChosen = new boolean[12];
			for (int i = 0; i < playerChosen.length; i++)
				for (int j = 0; j < players.size(); j++)
					if (players.get(j).character != null && players.get(j).character.equals(i))
						playerChosen[i] = true;
			int i = 0;
			int[] iDrawOrder = { 1, 3, 5, 6, 8, 10, 0, 2, 4, 7, 9, 11 };
			for (int j = 0; j < pictures.characters.size(); j++) {
				i = iDrawOrder[j];
				if (!playerChosen[i]) {
					g.drawImage(pictures.selectionBomberTiles.get(0).get(0), 177 + 32 * i + (i >= 6 ? 16 : 0),
							380 - (Tools.isEven(i) ? (i >= 6 ? 32 : 0) : (i >= 6 ? 0 : 32)), this);
					new Character(pictures, i).draw(g, 186 + 32 * i + (i >= 6 ? 16 : 0),
							335 - (Tools.isEven(i) ? (i >= 6 ? 32 : 0) : (i >= 6 ? 0 : 32)), this);
				}
				for (int k = 0; k < players.size(); k++)
					if (players.get(k).character != null && players.get(k).character.characterIndex == i
							&& !players.get(k).character.animation.equals(Animation.BOMBER_SELECT_IDLE))
						players.get(k).character.draw(g, 186 + 32 * i + (i >= 6 ? 16 : 0),
								335 - (Tools.isEven(i) ? (i >= 6 ? 32 : 0) : (i >= 6 ? 0 : 32)), this);
				if (counter[5] != players.size() || players.size() == 0)
					g.drawImage(pictures.selectionBomberTiles.get(1).get(0), 286 + 500 - (int) counter[4] * 20, 44,
							this);
				else
					g.drawImage(pictures.selectionBomberTiles.get(1).get(0), 286, 44, this);
				if (counter[5] != players.size() || players.size() == 0) {
					g.drawImage(pictures.selectionBomberTiles.get(2 + (int) counter[5]).get(0),
							222 + 500 - (int) counter[4] * 20, 92, this);
					g.drawImage(pictures.characters.get((int) counter[1]).get(0).get(0),
							412 + 500 - (int) counter[4] * 20, 92, this);
				} else {
					g.drawImage(pictures.selectionButtons.get(10).get(0), 353, 96, this);
					g.drawImage(pictures.selectionButtonSelector.get(0).get(2), 349, 90, this);
				}
			}
			g.drawImage(pictures.selectionBomberBackground2, 160, 238, this);
			if (counter[5] != players.size() || players.size() == 0)
				for (int j = 0; j < pictures.characters.size(); j++) {
					i = iDrawOrder[j];
					if (counter[1] == i)
						g.drawImage(pictures.selectionBomberTiles.get(12).get((int) counter[5]),
								190 + 32 * i + (i >= 6 ? 16 : 0) + 500 - (int) counter[4] * 20,
								323 - (Tools.isEven(i) ? (i >= 6 ? 32 : 0) : (i >= 6 ? 0 : 32)), this);
				}
			for (int j = 0; j < pictures.characters.size(); j++) {
				i = iDrawOrder[j];
				for (int k = 0; k < players.size(); k++)
					if (players.get(k).character != null && players.get(k).character.characterIndex == i
							&& players.get(k).character.animation.equals(Animation.BOMBER_SELECT_IDLE))
						players.get(k).character.draw(g, 186 + 32 * i + (i >= 6 ? 16 : 0),
								335 - (Tools.isEven(i) ? (i >= 6 ? 32 : 0) : (i >= 6 ? 0 : 32)), this);
			}
		} else if (counter[0] == 13) {
			// moving to team selection
			g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 35, 187, this);
		} else if (counter[0] == 14) {
			// team selection
			g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 35, 187, this);
		} else if (counter[0] == 15) {
			// back from team selection
		} else if (counter[0] == 16) {
			g.drawImage(pictures.selectionCircusBackground, -2, -2, this);
			BufferedImage bombBG = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2 = bombBG.createGraphics();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			if (counter[6] != 1) {
				draw.rectangle(g2, 100, 20, 600, 400, new Color(184, 200, 208));
				for (int i = 0; i < 6; i++)
					for (int j = 0; j < 6; j++) {
						long x = 200 + i * 130 - counter[2];
						long y = j * 130 - counter[2];
						if (Tools.isOdd(i))
							y += 130 / 2;
						g2.drawImage(pictures.selectionBombBackground, (int) x, (int) y, this);
					}
				g2.drawImage(pictures.selectionBackground, 8, 0, this);
				g2.drawImage(pictures.selectionButtons.get(3).get(0), 39, 45, this);
				g2.drawImage(pictures.selectionButtons.get(3).get(1), 39, 93, this);
				g2.drawImage(pictures.selectionButtons.get(3).get(2), 39, 141, this);
				if (options.teamMode)
					g2.drawImage(pictures.selectionButtons.get(3).get(3), 39, 189, this);
				else
					g2.drawImage(Pictures.darken(pictures.selectionButtons.get(3).get(3)), 39, 189, this);
				g2.drawImage(pictures.selectionButtons.get(4).get(0), 39, 319, this);
				g2.drawImage(pictures.selectionButtons.get(4).get(1), 39, 366, this);
				g2.drawImage(pictures.selectionBackground2, -2, -2, this);
			} else {
				draw.rectangle(g2, 100, 20, 600, 400, new Color(248, 208, 232));
				for (int i = 0; i < 6 * 3; i++)
					for (int j = 0; j < 6 * 3; j++) {
						long x = 200 + i * 130 - counter[2];
						long y = j * 130 - counter[2];
						if (Tools.isOdd(i))
							y += 130 / 2;
						g2.drawImage(pictures.selectionBombBackground2, (int) x - 19, (int) y - 8, this);
					}
				g2.drawImage(pictures.selectionBackground, 8, 0, this);
				g2.drawImage(pictures.selectionButtons.get(3).get(4), 39, 45, this);
				g2.drawImage(pictures.selectionButtons.get(4).get(0), 39, 319, this);
				if (network.isPlayingOnline()) {
					boolean toDarken = false;
					for (int i = 0; i < network.connectedClients.size(); i++)
						if (network.connectedClients.get(i)[0].equals(options.multiplayer.username))
							if ((network.weAreHost() && network.connectedClients.get(i)[2].equals("1"))
									|| (!network.weAreHost() && network.connectedClients.get(i)[3].equals("1")))
								toDarken = true;
					BufferedImage pic = pictures.selectionButtons.get(4).get(2);
					if (toDarken)
						pic = Pictures.darken(pic);
					g2.drawImage(pic, 39, 366, this);
				} else
					g2.drawImage(pictures.selectionButtons.get(4).get(1), 39, 366, this);
				g2.drawImage(pictures.selectionBackground2, -2, -2, this);
			}
			g2.dispose();
			g2 = null;
			int increaseWidth = 78 + (int) ((getWidth() - 78) * ((50f - counter[4]) / 50f));
			int increaseHeight = 58 + (int) ((getHeight() - 58) * ((50f - counter[4]) / 50f));
			int decreaseX = 281 + (int) (-281 * ((50f - counter[4]) / 50f));
			int decreaseY = 214 + (int) (-214 * ((50f - counter[4]) / 50f));
			BufferedImage bgScreen = Resizer.resize(bombBG, increaseWidth, increaseHeight);
			g.drawImage(bgScreen, decreaseX, decreaseY, this);
			int domeWidth = 616 + (int) ((4928 - 616) * ((50f - counter[4]) / 50f));
			int domeHeight = 323 + (int) ((2584 - 323) * ((50f - counter[4]) / 50f));
			int domeX = 11 - (domeWidth - 616) / 2;
			int domeY = 200 - (domeHeight - 323) / 2 + (int) (810 * ((50f - counter[4]) / 50f));
			// (40,200)
			BufferedImage dome = Resizer.resize(pictures.selectionDomeBackground, domeWidth, domeHeight);
			g.drawImage(dome, domeX, domeY, this);
			Point[] pList = Constants.oval;
			ArrayList<Integer> stageToDraw = new ArrayList<Integer>();
			ArrayList<Boolean> stageSelectable = new ArrayList<Boolean>();
			ArrayList<Point> stagePoint = new ArrayList<Point>();
			ArrayList<Float> stageResize = new ArrayList<Float>();
			int stageOneWidth = pictures.stagesSelections.get(0).getWidth();
			int stageOneHeight = pictures.stagesSelections.get(0).getHeight();
			float rotateSize = 360;
			for (int i = 0; i < pictures.stagesSelections.size(); i++) {
				float percent = (float) i / (float) pictures.stagesSelections.size();
				percent += (float) counter[1] / rotateSize;
				if (percent > 1)
					percent = percent - 1;
				if (percent < 0)
					percent = percent + 1;
				Point point = Tools.getPointAtListPercent(pList, percent);
				int idx = Tools.getIntegerAtListPercent(pList, percent);
				int end = Tools.getIntegerAtListPercent(pList, .5f);
				int start = Tools.getIntegerAtListPercent(pList, 0);
				if (idx > end)
					idx = Math.abs(idx - (end * 2));
				float completion = (float) idx / ((float) end - (float) start);
				float resize = completion * .5f;
				if (completion > .5f) {
					resize = resize * 1.25f;
					point = new Point(point.x - 14, point.y);
				} else
					point = new Point(point.x + 1, point.y);
				stageToDraw.add(i);
				stageSelectable.add(pictures.availableStageAmt - 1 >= i);
				stagePoint.add(point);
				stageResize.add(resize);
			}
			boolean flag = true;
			int temp;
			boolean tempBool;
			Point tempPoint;
			Float tempFloat;
			while (flag) {
				flag = false;
				for (int j = 0; j < stageResize.size() - 1; j++) {
					if (stageResize.get(j) < stageResize.get(j + 1)) {
						temp = stageToDraw.get(j);
						stageToDraw.set(j, stageToDraw.get(j + 1));
						stageToDraw.set(j + 1, temp);
						tempBool = stageSelectable.get(j);
						stageSelectable.set(j, stageSelectable.get(j + 1));
						stageSelectable.set(j + 1, tempBool);
						tempPoint = stagePoint.get(j);
						stagePoint.set(j, stagePoint.get(j + 1));
						stagePoint.set(j + 1, tempPoint);
						tempFloat = stageResize.get(j);
						stageResize.set(j, stageResize.get(j + 1));
						stageResize.set(j + 1, tempFloat);
						flag = true;
					}
				}
			}
			for (int i = 0; i < stageToDraw.size(); i++) {
				BufferedImage stage = pictures.stagesSelections.get(stageToDraw.get(i));
				int width = stage.getWidth(), height = stage.getHeight();
				if (stageResize.get(i) > 0.001)
					stage = Resizer.resize(stage, (int) (stage.getWidth() - stage.getWidth() * stageResize.get(i)),
							(int) (stage.getHeight() - stage.getHeight() * stageResize.get(i)));
				if (!stageSelectable.get(i))
					stage = Pictures.darken(stage);
				Point newPoint = new Point(stagePoint.get(i).x - stage.getWidth() / 2,
						stagePoint.get(i).y - stage.getHeight() / 2);
				if (width != stageOneWidth || height != stageOneHeight) {
					int changeX = width - stageOneWidth;
					int changeY = height - stageOneHeight;
					float resizeAmt = stageResize.get(i);
					newPoint = new Point((int) (newPoint.x - (changeX * (1 - resizeAmt) / 2)),
							(int) (newPoint.y - (changeY * (1 - resizeAmt) / 2)));
				}
				g.drawImage(stage, newPoint.x, newPoint.y - ((50 - (int) counter[4]) * 10), this);
			}
			for (int i = 0; i < pictures.stagesDescription.size() + 1; i++) {
				int toDraw = i;
				if (i == pictures.stagesDescription.size())
					toDraw = 0;
				int counter1 = (int) (counter[1]);
				if (counter1 == 0)
					counter1 = 360;
				g.drawImage(pictures.stagesDescription.get(toDraw),
						184 + (getWidth() * (8 - i)) - (int) ((float) counter1 * 14.2222f),
						62 - ((50 - (int) counter[4]) * 10), this);
			}
		} else if (counter[0] == 17) {
			g.drawImage(pictures.selectionCircusBackground, -2, -2, this);
			BufferedImage bombBG = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2 = bombBG.createGraphics();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			if (counter[6] != 1) {
				draw.rectangle(g2, 100, 20, 600, 400, new Color(184, 200, 208));
				for (int i = 0; i < 6; i++)
					for (int j = 0; j < 6; j++) {
						long x = 200 + i * 130 - counter[2];
						long y = j * 130 - counter[2];
						if (Tools.isOdd(i))
							y += 130 / 2;
						g2.drawImage(pictures.selectionBombBackground, (int) x, (int) y, this);
					}
				g2.drawImage(pictures.selectionBackground, 8, 0, this);
				g2.drawImage(pictures.selectionButtons.get(3).get(0), 39, 45, this);
				g2.drawImage(pictures.selectionButtons.get(3).get(1), 39, 93, this);
				g2.drawImage(pictures.selectionButtons.get(3).get(2), 39, 141, this);
				if (options.teamMode)
					g2.drawImage(pictures.selectionButtons.get(3).get(3), 39, 189, this);
				else
					g2.drawImage(Pictures.darken(pictures.selectionButtons.get(3).get(3)), 39, 189, this);
				g2.drawImage(pictures.selectionButtons.get(4).get(0), 39, 319, this);
				g2.drawImage(pictures.selectionButtons.get(4).get(1), 39, 366, this);
				g2.drawImage(pictures.selectionBackground2, -2, -2, this);
			} else {
				draw.rectangle(g2, 100, 20, 600, 400, new Color(248, 208, 232));
				for (int i = 0; i < 6 * 3; i++)
					for (int j = 0; j < 6 * 3; j++) {
						long x = 200 + i * 130 - counter[2];
						long y = j * 130 - counter[2];
						if (Tools.isOdd(i))
							y += 130 / 2;
						g2.drawImage(pictures.selectionBombBackground2, (int) x - 19, (int) y - 8, this);
					}
				g2.drawImage(pictures.selectionBackground, 8, 0, this);
				g2.drawImage(pictures.selectionButtons.get(3).get(4), 39, 45, this);
				g2.drawImage(pictures.selectionButtons.get(4).get(0), 39, 319, this);
				if (network.isPlayingOnline()) {
					boolean toDarken = false;
					for (int i = 0; i < network.connectedClients.size(); i++)
						if (network.connectedClients.get(i)[0].equals(options.multiplayer.username))
							if ((network.weAreHost() && network.connectedClients.get(i)[2].equals("1"))
									|| (!network.weAreHost() && network.connectedClients.get(i)[3].equals("1")))
								toDarken = true;
					BufferedImage pic = pictures.selectionButtons.get(4).get(2);
					if (toDarken)
						pic = Pictures.darken(pic);
					g2.drawImage(pic, 39, 366, this);
				} else
					g2.drawImage(pictures.selectionButtons.get(4).get(1), 39, 366, this);
				g2.drawImage(pictures.selectionBackground2, -2, -2, this);
			}
			g2.dispose();
			BufferedImage bgScreen = Resizer.resize(bombBG, 78, 58);
			g.drawImage(bgScreen, 281, 214, this);
			g.drawImage(pictures.selectionDomeBackground, 11, 200, this);
			Point[] pList = Constants.oval;
			ArrayList<Integer> stageToDraw = new ArrayList<Integer>();
			ArrayList<Boolean> stageSelectable = new ArrayList<Boolean>();
			ArrayList<Point> stagePoint = new ArrayList<Point>();
			ArrayList<Float> stageResize = new ArrayList<Float>();
			int stageOneWidth = pictures.stagesSelections.get(0).getWidth();
			int stageOneHeight = pictures.stagesSelections.get(0).getHeight();
			float rotateSize = 360;
			for (int i = 0; i < pictures.stagesSelections.size(); i++) {
				float percent = (float) i / (float) pictures.stagesSelections.size();
				percent += (float) counter[1] / rotateSize;
				if (percent > 1)
					percent = percent - 1;
				if (percent < 0)
					percent = percent + 1;
				Point point = Tools.getPointAtListPercent(pList, percent);
				int idx = Tools.getIntegerAtListPercent(pList, percent);
				int end = Tools.getIntegerAtListPercent(pList, .5f);
				int start = Tools.getIntegerAtListPercent(pList, 0);
				if (idx > end)
					idx = Math.abs(idx - (end * 2));
				float completion = (float) idx / ((float) end - (float) start);
				float resize = completion * .5f;
				if (completion > .5f) {
					resize = resize * 1.25f;
					point = new Point(point.x - 14, point.y);
				} else
					point = new Point(point.x + 1, point.y);
				stageToDraw.add(i);
				stageSelectable.add(pictures.availableStageAmt - 1 >= i);
				stagePoint.add(point);
				stageResize.add(resize);
			}
			boolean flag = true;
			int temp;
			boolean tempBool;
			Point tempPoint;
			Float tempFloat;
			while (flag) {
				flag = false;
				for (int j = 0; j < stageResize.size() - 1; j++) {
					if (stageResize.get(j) < stageResize.get(j + 1)) {
						temp = stageToDraw.get(j);
						stageToDraw.set(j, stageToDraw.get(j + 1));
						stageToDraw.set(j + 1, temp);
						tempBool = stageSelectable.get(j);
						stageSelectable.set(j, stageSelectable.get(j + 1));
						stageSelectable.set(j + 1, tempBool);
						tempPoint = stagePoint.get(j);
						stagePoint.set(j, stagePoint.get(j + 1));
						stagePoint.set(j + 1, tempPoint);
						tempFloat = stageResize.get(j);
						stageResize.set(j, stageResize.get(j + 1));
						stageResize.set(j + 1, tempFloat);
						flag = true;
					}
				}
			}
			for (int i = 0; i < stageToDraw.size(); i++) {
				BufferedImage stage = pictures.stagesSelections.get(stageToDraw.get(i));
				int width = stage.getWidth(), height = stage.getHeight();
				if (stageResize.get(i) > 0.001)
					stage = Resizer.resize(stage, (int) (stage.getWidth() - stage.getWidth() * stageResize.get(i)),
							(int) (stage.getHeight() - stage.getHeight() * stageResize.get(i)));
				if (!stageSelectable.get(i))
					stage = Pictures.darken(stage);
				Point newPoint = new Point(stagePoint.get(i).x - stage.getWidth() / 2,
						stagePoint.get(i).y - stage.getHeight() / 2);
				if (width != stageOneWidth || height != stageOneHeight) {
					int changeX = width - stageOneWidth;
					int changeY = height - stageOneHeight;
					float resizeAmt = stageResize.get(i);
					newPoint = new Point((int) (newPoint.x - (changeX * (1 - resizeAmt) / 2)),
							(int) (newPoint.y - (changeY * (1 - resizeAmt) / 2)));
				}
				g.drawImage(stage, newPoint.x, newPoint.y, this);
			}
			for (int i = 0; i < pictures.stagesDescription.size() + 1; i++) {
				int toDraw = i;
				if (i == pictures.stagesDescription.size())
					toDraw = 0;
				int counter1 = (int) counter[1];
				if (counter1 == 0)
					counter1 = 360;
				g.drawImage(pictures.stagesDescription.get(toDraw),
						184 + (getWidth() * (8 - i)) - (int) ((float) counter1 * 14.2222f), 62, this);
			}
		} else if (counter[0] == 18) {
			g.drawImage(pictures.selectionCircusBackground, -2, -2, this);
			BufferedImage bombBG = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D g2 = bombBG.createGraphics();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			if (counter[6] != 1) {
				draw.rectangle(g2, 100, 20, 600, 400, new Color(184, 200, 208));
				for (int i = 0; i < 6; i++)
					for (int j = 0; j < 6; j++) {
						long x = 200 + i * 130 - counter[2];
						long y = j * 130 - counter[2];
						if (Tools.isOdd(i))
							y += 130 / 2;
						g2.drawImage(pictures.selectionBombBackground, (int) x, (int) y, this);
					}
				g2.drawImage(pictures.selectionBackground, 8, 0, this);
				g2.drawImage(pictures.selectionButtons.get(3).get(0), 39, 45, this);
				g2.drawImage(pictures.selectionButtons.get(3).get(1), 39, 93, this);
				g2.drawImage(pictures.selectionButtons.get(3).get(2), 39, 141, this);
				if (options.teamMode)
					g2.drawImage(pictures.selectionButtons.get(3).get(3), 39, 189, this);
				else
					g2.drawImage(Pictures.darken(pictures.selectionButtons.get(3).get(3)), 39, 189, this);
				g2.drawImage(pictures.selectionButtons.get(4).get(0), 39, 319, this);
				g2.drawImage(pictures.selectionButtons.get(4).get(1), 39, 366, this);
				g2.drawImage(pictures.selectionBackground2, -2, -2, this);
			} else {
				draw.rectangle(g2, 100, 20, 600, 400, new Color(248, 208, 232));
				for (int i = 0; i < 6 * 3; i++)
					for (int j = 0; j < 6 * 3; j++) {
						long x = 200 + i * 130 - counter[2];
						long y = j * 130 - counter[2];
						if (Tools.isOdd(i))
							y += 130 / 2;
						g2.drawImage(pictures.selectionBombBackground2, (int) x - 19, (int) y - 8, this);
					}
				g2.drawImage(pictures.selectionBackground, 8, 0, this);
				g2.drawImage(pictures.selectionButtons.get(3).get(4), 39, 45, this);
				g2.drawImage(pictures.selectionButtons.get(4).get(0), 39, 319, this);
				if (network.isPlayingOnline()) {
					boolean toDarken = false;
					for (int i = 0; i < network.connectedClients.size(); i++)
						if (network.connectedClients.get(i)[0].equals(options.multiplayer.username))
							if ((network.weAreHost() && network.connectedClients.get(i)[2].equals("1"))
									|| (!network.weAreHost() && network.connectedClients.get(i)[3].equals("1")))
								toDarken = true;
					BufferedImage pic = pictures.selectionButtons.get(4).get(2);
					if (toDarken)
						pic = Pictures.darken(pic);
					g2.drawImage(pic, 39, 366, this);
				} else
					g2.drawImage(pictures.selectionButtons.get(4).get(1), 39, 366, this);
				g2.drawImage(pictures.selectionBackground2, -2, -2, this);
			}
			g2.dispose();
			int increaseWidth = 78 + (int) ((getWidth() - 78) * ((50f - counter[4]) / 50f));
			int increaseHeight = 58 + (int) ((getHeight() - 58) * ((50f - counter[4]) / 50f));
			int decreaseX = 281 + (int) (-281 * ((50f - counter[4]) / 50f));
			int decreaseY = 214 + (int) (-214 * ((50f - counter[4]) / 50f));
			BufferedImage bgScreen = Resizer.resize(bombBG, increaseWidth, increaseHeight);
			g.drawImage(bgScreen, decreaseX, decreaseY, this);
			int domeWidth = 616 + (int) ((4928 - 616) * ((50f - counter[4]) / 50f));
			int domeHeight = 323 + (int) ((2584 - 323) * ((50f - counter[4]) / 50f));
			int domeX = 11 - (domeWidth - 616) / 2;
			int domeY = 200 - (domeHeight - 323) / 2 + (int) (810 * ((50f - counter[4]) / 50f));
			// (40,200)
			BufferedImage dome = Resizer.resize(pictures.selectionDomeBackground, domeWidth, domeHeight);
			g.drawImage(dome, domeX, domeY, this);
			Point[] pList = Constants.oval;
			ArrayList<Integer> stageToDraw = new ArrayList<Integer>();
			ArrayList<Boolean> stageSelectable = new ArrayList<Boolean>();
			ArrayList<Point> stagePoint = new ArrayList<Point>();
			ArrayList<Float> stageResize = new ArrayList<Float>();
			int stageOneWidth = pictures.stagesSelections.get(0).getWidth();
			int stageOneHeight = pictures.stagesSelections.get(0).getHeight();
			float rotateSize = 360;
			for (int i = 0; i < pictures.stagesSelections.size(); i++) {
				float percent = (float) i / (float) pictures.stagesSelections.size();
				percent += (float) (counter[1] - 4) / rotateSize;
				if (percent > 1)
					percent = percent - 1;
				if (percent < 0)
					percent = percent + 1;
				Point point = Tools.getPointAtListPercent(pList, percent);
				int idx = Tools.getIntegerAtListPercent(pList, percent);
				int end = Tools.getIntegerAtListPercent(pList, .5f);
				int start = Tools.getIntegerAtListPercent(pList, 0);
				if (idx > end)
					idx = Math.abs(idx - (end * 2));
				float completion = (float) idx / ((float) end - (float) start);
				float resize = completion * .5f;
				if (completion > .5f) {
					resize = resize * 1.25f;
					point = new Point(point.x - 14, point.y);
				} else
					point = new Point(point.x + 1, point.y);
				stageToDraw.add(i);
				stageSelectable.add(pictures.availableStageAmt - 1 >= i);
				stagePoint.add(point);
				stageResize.add(resize);
			}
			boolean flag = true;
			int temp;
			boolean tempBool;
			Point tempPoint;
			Float tempFloat;
			while (flag) {
				flag = false;
				for (int j = 0; j < stageResize.size() - 1; j++) {
					if (stageResize.get(j) < stageResize.get(j + 1)) {
						temp = stageToDraw.get(j);
						stageToDraw.set(j, stageToDraw.get(j + 1));
						stageToDraw.set(j + 1, temp);
						tempBool = stageSelectable.get(j);
						stageSelectable.set(j, stageSelectable.get(j + 1));
						stageSelectable.set(j + 1, tempBool);
						tempPoint = stagePoint.get(j);
						stagePoint.set(j, stagePoint.get(j + 1));
						stagePoint.set(j + 1, tempPoint);
						tempFloat = stageResize.get(j);
						stageResize.set(j, stageResize.get(j + 1));
						stageResize.set(j + 1, tempFloat);
						flag = true;
					}
				}
			}
			for (int i = 0; i < stageToDraw.size(); i++) {
				BufferedImage stage = pictures.stagesSelections.get(stageToDraw.get(i));
				int width = stage.getWidth(), height = stage.getHeight();
				if (stageResize.get(i) > 0.001)
					stage = Resizer.resize(stage, (int) (stage.getWidth() - stage.getWidth() * stageResize.get(i)),
							(int) (stage.getHeight() - stage.getHeight() * stageResize.get(i)));
				if (!stageSelectable.get(i))
					stage = Pictures.darken(stage);
				Point newPoint = new Point(stagePoint.get(i).x - stage.getWidth() / 2,
						stagePoint.get(i).y - stage.getHeight() / 2);
				if (width != stageOneWidth || height != stageOneHeight) {
					int changeX = width - stageOneWidth;
					int changeY = height - stageOneHeight;
					float resizeAmt = stageResize.get(i);
					newPoint = new Point((int) (newPoint.x - (changeX * (1 - resizeAmt) / 2)),
							(int) (newPoint.y - (changeY * (1 - resizeAmt) / 2)));
				}
				g.drawImage(stage, newPoint.x, newPoint.y - ((50 - (int) counter[4]) * 10), this);
			}
			for (int i = 0; i < pictures.stagesDescription.size() + 1; i++) {
				int toDraw = i;
				if (i == pictures.stagesDescription.size())
					toDraw = 0;
				int counter1 = (int) (counter[1] - 4);
				if (counter1 == 0)
					counter1 = 360;
				g.drawImage(pictures.stagesDescription.get(toDraw),
						184 + (getWidth() * (8 - i)) - (int) ((float) counter1 * 14.2222f),
						62 - ((50 - (int) counter[4]) * 10), this);
			}
		} else if (counter[0] == 19) {
			g.drawImage(pictures.selectionCircusBackground, -2, -2, this);
			g.drawImage(pictures.selectionDomeBackground, 11, 200 - (50 - (int) counter[4]), this);
			Point[] pList = Constants.oval;
			ArrayList<Integer> stageToDraw = new ArrayList<Integer>();
			ArrayList<Boolean> stageSelectable = new ArrayList<Boolean>();
			ArrayList<Point> stagePoint = new ArrayList<Point>();
			ArrayList<Float> stageResize = new ArrayList<Float>();
			int stageOneWidth = pictures.stagesSelections.get(0).getWidth();
			int stageOneHeight = pictures.stagesSelections.get(0).getHeight();
			float rotateSize = 360;
			for (int i = 0; i < pictures.stagesSelections.size(); i++) {
				float percent = (float) i / (float) pictures.stagesSelections.size();
				percent += (float) (counter[6]) / rotateSize;
				if (percent > 1)
					percent = percent - 1;
				if (percent < 0)
					percent = percent + 1;
				Point point = Tools.getPointAtListPercent(pList, percent);
				int idx = Tools.getIntegerAtListPercent(pList, percent);
				int end = Tools.getIntegerAtListPercent(pList, .5f);
				int start = Tools.getIntegerAtListPercent(pList, 0);
				if (idx > end)
					idx = Math.abs(idx - (end * 2));
				float completion = (float) idx / ((float) end - (float) start);
				float resize = completion * .5f;
				if (completion > .5f) {
					resize = resize * 1.25f;
					point = new Point(point.x - 14, point.y);
				} else
					point = new Point(point.x + 1, point.y);
				stageToDraw.add(i);
				stageSelectable.add(pictures.availableStageAmt - 1 >= i);
				stagePoint.add(point);
				stageResize.add(resize);
			}
			boolean flag = true;
			int temp;
			boolean tempBool;
			Point tempPoint;
			Float tempFloat;
			while (flag) {
				flag = false;
				for (int j = 0; j < stageResize.size() - 1; j++) {
					if (stageResize.get(j) < stageResize.get(j + 1)) {
						temp = stageToDraw.get(j);
						stageToDraw.set(j, stageToDraw.get(j + 1));
						stageToDraw.set(j + 1, temp);
						tempBool = stageSelectable.get(j);
						stageSelectable.set(j, stageSelectable.get(j + 1));
						stageSelectable.set(j + 1, tempBool);
						tempPoint = stagePoint.get(j);
						stagePoint.set(j, stagePoint.get(j + 1));
						stagePoint.set(j + 1, tempPoint);
						tempFloat = stageResize.get(j);
						stageResize.set(j, stageResize.get(j + 1));
						stageResize.set(j + 1, tempFloat);
						flag = true;
					}
				}
			}
			for (int i = 0; i < stageToDraw.size(); i++) {
				BufferedImage stage = pictures.stagesSelections.get(stageToDraw.get(i));
				int width = stage.getWidth(), height = stage.getHeight();
				if (stageResize.get(i) > 0.001)
					stage = Resizer.resize(stage, (int) (stage.getWidth() - stage.getWidth() * stageResize.get(i)),
							(int) (stage.getHeight() - stage.getHeight() * stageResize.get(i)));
				else {
					float resizeAmt = counter[4] / 50f;
					if (resizeAmt < .5f) {
						resizeAmt = .5f;
						stagePoint.set(i,
								new Point(stagePoint.get(i).x, (int) (stagePoint.get(i).y + (25 - counter[4]))));
					}
					if (resizeAmt > 1f)
						resizeAmt = 1f;
					stage = Resizer.resize(stage, (int) (stage.getWidth() * resizeAmt),
							(int) (stage.getHeight() * resizeAmt));
				}
				if (!stageSelectable.get(i))
					stage = Pictures.darken(stage);
				Point newPoint = new Point(stagePoint.get(i).x - stage.getWidth() / 2,
						stagePoint.get(i).y - stage.getHeight() / 2);
				if (width != stageOneWidth || height != stageOneHeight) {
					int changeX = width - stageOneWidth;
					int changeY = height - stageOneHeight;
					float resizeAmt = stageResize.get(i);
					newPoint = new Point((int) (newPoint.x - (changeX * (1 - resizeAmt) / 2)),
							(int) (newPoint.y - (changeY * (1 - resizeAmt) / 2)));
				}
				if (stageResize.get(i) <= 0.001) {
					int x = newPoint.x;
					int y = newPoint.y;
					g.drawImage(stage, x, y, this);
				} else
					g.drawImage(stage, newPoint.x, newPoint.y - ((50 - (int) counter[4]) * 10), this);
			}
			for (int i = 0; i < pictures.stagesDescription.size() + 1; i++) {
				int toDraw = i;
				if (i == pictures.stagesDescription.size())
					toDraw = 0;
				int counter1 = (int) (counter[6]);
				if (counter1 == 0)
					counter1 = 360;
				g.drawImage(pictures.stagesDescription.get(toDraw),
						184 + (getWidth() * (8 - i)) - (int) ((float) counter1 * 14.2222f),
						62 - ((50 - (int) counter[4]) * 10), this);
			}

		}

		if (fadecounter != -2 && opacity == 0)
			opacity = 1;
		// tickFade(g);
	}

	public void tickMatchSelection(int keyCode) {
		if (counter[0] == 0) {
			long tempCounter = upAndDownMenuResult(counter[1], keyCode, 3, false, false);
			if (counter[1] != tempCounter) {
				sound.play("Tick");
				counter[1] = tempCounter;
				tickcounter = 75;
			}
			if (((keyCode == options.controllers[0].AButton || keyCode == options.controllers[0].CButton)
					&& counter[1] == 2) || keyCode == options.controllers[0].BButton) {
				if (network.isPlayingOnline()) {
					logger.log(LogLevel.WARNING, "Cannot go back: playing online.");
					return;
				}
				sound.play("Back");
				state.setNextStateAfterFade(StateEnum.MAIN_MENU, this);
				counter[1] = 0;
				counter[2] = 0;
				counter[3] = 0;
				counter[4] = 0;
			}
			if (keyCode == options.controllers[0].AButton || keyCode == options.controllers[0].CButton) {
				if (counter[1] == 0) {
					// Match
					sound.play("Select");
					counter[0] = 1;
					counter[1] = 0;
					counter[3] = 0;
				} else if (counter[1] == 1) {
					// Series
				}
			}
		} else if (counter[0] == 2) {
			long tempCounter = counter[1];
			if (keyCode == options.controllers[0].upButton || keyCode == options.controllers[0].downButton) {
				if (options.battleOptionsFinished)
					tempCounter = upAndDownMenuResult(counter[1], keyCode, 2, false, false);
				if (options.rulesOptionsFinished)
					tempCounter = upAndDownMenuResult(counter[1], keyCode, 3, false, false);
				if (options.bombersOptionsFinished && options.teamMode)
					tempCounter = upAndDownMenuResult(counter[1], keyCode, 4, false, false);
				if (options.teamOptionsFinished && options.teamMode)
					tempCounter = upAndDownMenuResult(counter[1], keyCode, 5, false, false);
				if (options.bombersOptionsFinished && !options.teamMode) {
					tempCounter = upAndDownMenuResult(counter[1], keyCode, 4, false, false);
					if (tempCounter == 3)
						if (keyCode == options.controllers[0].downButton)
							tempCounter = 4;
						else if (keyCode == options.controllers[0].upButton)
							tempCounter = 2;
				}
			}
			if (counter[1] != tempCounter) {
				sound.play("Tick");
				counter[1] = tempCounter;
				tickcounter = 75;
			}
			if (keyCode == options.controllers[0].BButton) {
				sound.play("Back");
				counter[0] = 3;
				counter[1] = 0;
				counter[3] = 0;
				counter[4] = 25;
			}
			if (keyCode == options.controllers[0].AButton || keyCode == options.controllers[0].CButton) {
				sound.play("Select");
				counter[0] = counter[1] * 3 + 4;
				counter[1] = 0;
				counter[4] = 0;
				if (counter[0] == 4)
					counter[1] = 1;
				if (counter[0] == 7)
					counter[1] = 1;
				if (counter[0] == 16)
					counter[5] = 0;
				if (counter[0] == 10) {
					if (options.bombersOptionsFinished) {
						options.bombersOptionsFinished = false;
						counter[5] = players.size();
						for (int i = 0; i < players.size(); i++)
							players.get(i).character.animation = Animation.BOMBER_SELECT_IDLE;
					}
				}
			}
		} else if (counter[0] == 5) {
			if (keyCode == options.controllers[0].AButton || keyCode == options.controllers[0].CButton) {
				sound.play("Select");
				if (counter[1] == 0 || counter[1] == 3 || counter[1] == 6)
					counter[1]++;
				else if (counter[1] == 1) {
					counter[1] = 4;
					options.wideMode = false;
				} else if (counter[1] == 2) {
					counter[1] = 4;
					options.wideMode = true;
				} else if (counter[1] == 4) {
					counter[1] = 7;
					options.teamMode = false;
				} else if (counter[1] == 5) {
					counter[1] = 7;
					options.teamMode = true;
				} else if (counter[1] == 6)
					counter[1] = 7;
				else if (counter[1] >= 7 && counter[1] <= 16)
					counter[1] = 17;
				else if (counter[1] == 17) {
					counter[4] = 25;
					counter[0] = 6;
					counter[1] = 1;
					options.battleOptionsFinished = true;
				}
			} else if (keyCode == options.controllers[0].BButton) {
				sound.play("Back");
				if (counter[1] == 0 || counter[1] == 3 || counter[1] == 6 || counter[1] == 15) {
					counter[4] = 25;
					counter[0] = 6;
					counter[1] = 0;
				} else if (counter[1] == 1 || counter[1] == 2) {
					counter[1] = 0;
				} else if (counter[1] == 4 || counter[1] == 5) {
					counter[1] = 3;
				} else if (counter[1] >= 7 && counter[1] <= 15) {
					counter[1] = 6;
				}
			} else if (keyCode == options.controllers[0].upButton) {
				long tempCounter = counter[1];
				if (counter[1] == 3 || counter[1] == 6)
					tempCounter -= 3;
				else if (counter[1] >= 8 && counter[1] <= 16) {
					tempCounter--;
					tickcounter = 75;
				} else if (counter[1] == 17)
					tempCounter = 6;
				if (tempCounter != counter[1]) {
					sound.play("Tick");
					counter[1] = tempCounter;
				}
			} else if (keyCode == options.controllers[0].downButton) {
				long tempCounter = counter[1];
				if (counter[1] == 0 || counter[1] == 3)
					tempCounter += 3;
				else if (counter[1] == 6)
					tempCounter = 17;
				else if (counter[1] >= 7 && counter[1] <= 15 && options.wideMode) {
					tempCounter++;
					tickcounter = 75;
				} else if (counter[1] >= 7 && counter[1] <= 13 && !options.wideMode) {
					tempCounter++;
					tickcounter = 75;
				}
				if (tempCounter != counter[1]) {
					sound.play("Tick");
					counter[1] = tempCounter;
				}
			} else if (keyCode == options.controllers[0].rightButton) {
				long tempCounter = counter[1];
				if (counter[1] == 1 || counter[1] == 4)
					logger.log(LogLevel.WARNING, "This game mode is not available.");
				if (counter[1] >= 7 && counter[1] <= 15) {
					int player = (int) counter[1] - 7;
					if (options.players[player] + 1 < 3) {
						counter[5] = 0;
						options.bombersOptionsFinished = false;
						players = new ArrayList<Player>();
						if (network.isPlayingOnline()) {
							if (player < network.getTotalPlayersAmt())
								return;
							else if (options.players[player] == 0) {
								sound.play("Tick");
								options.players[player] = 2;
							}
							return;
						}
						if (player >= 2 && options.players[player] == 0)
							options.players[player] = 2;
						else
							options.players[player]++;
						sound.play("Tick");
					}
				}
				if (tempCounter != counter[1]) {
					sound.play("Tick");
					counter[1] = tempCounter;
				}
			} else if (keyCode == options.controllers[0].leftButton) {
				long tempCounter = counter[1];
				if (counter[1] == 2 || counter[1] == 5)
					tempCounter--;
				if (counter[1] >= 7 && counter[1] <= 15) {
					int foundPlayers = 0;
					for (int i = 0; i < options.players.length; i++)
						if (options.players[i] != 0)
							foundPlayers++;
					int player = (int) counter[1] - 7;
					if (foundPlayers < 3 && options.players[player] == 1) {
					} else if (options.players[player] - 1 > -1) {
						counter[5] = 0;
						options.bombersOptionsFinished = false;
						players = new ArrayList<Player>();
						if (network.isPlayingOnline()) {
							if (player < network.getTotalPlayersAmt())
								return;
							else if (options.players[player] == 2) {
								sound.play("Tick");
								options.players[player] = 0;
							}
							return;
						}
						if (player >= 2 && options.players[player] == 2)
							options.players[player] = 0;
						else
							options.players[player]--;
						sound.play("Tick");
					}
				}
				if (tempCounter != counter[1]) {
					sound.play("Tick");
					counter[1] = tempCounter;
				}
			}
		} else if (counter[0] == 8) {
			if (keyCode == options.controllers[0].AButton || keyCode == options.controllers[0].CButton) {
				if (counter[1] == 0) {
					sound.play("Select");
					counter[1] = 1;
				} else if (counter[1] >= 1 && counter[1] <= 7) {
					sound.play("Select");
					counter[1] = 9;
				} else if (counter[1] == 8) {
					sound.play("Select");
					counter[1] = 9;
				} else if (counter[1] == 9) {
					sound.play("Select");
					counter[1] = 10;
				} else if (counter[1] == 10) {
					sound.play("Select");
					options.rulesOptionsFinished = true;
					counter[4] = 25;
					counter[0] = 9;
					counter[1] = 2;
				}
			} else if (keyCode == options.controllers[0].BButton) {
				sound.play("Back");
				if (counter[1] == 0 || counter[1] == 8 || counter[1] == 10) {
					counter[4] = 25;
					counter[0] = 9;
					counter[1] = 1;
				} else if (counter[1] >= 1 && counter[1] <= 7) {
					counter[1] = 0;
					tickcounter = 90;
				} else if (counter[1] == 9) {
					counter[1] = 8;
					tickcounter = 90;
				}
			} else if (keyCode == options.controllers[0].upButton) {
				long tempCounter = counter[1];
				if (counter[1] >= 2 && counter[1] <= 7)
					tempCounter--;
				else if (counter[1] == 8)
					tempCounter = 0;
				else if (counter[1] == 10)
					tempCounter = 8;
				if (tempCounter != counter[1]) {
					sound.play("Tick");
					counter[1] = tempCounter;
					tickcounter = 90;
				}
			} else if (keyCode == options.controllers[0].downButton) {
				long tempCounter = counter[1];
				if (counter[1] == 0)
					tempCounter = 8;
				else if (counter[1] >= 1 && counter[1] <= 6)
					tempCounter++;
				else if (counter[1] == 8)
					tempCounter = 10;
				if (tempCounter != counter[1]) {
					sound.play("Tick");
					counter[1] = tempCounter;
					tickcounter = 90;
				}
			} else if (keyCode == options.controllers[0].leftButton) {
				int tempComLevelCounter = options.comLevel;
				int tempBattlesCounter = options.numberOfBattles;
				int tempTimeCounter = (int) options.time;
				if (counter[1] == 1) {
					if (options.numberOfBattles > 1) {
						tempBattlesCounter--;
						tickcounter = 90;
					}
				} else if (counter[1] == 2) {
					if (options.time > 1) {
						tempTimeCounter--;
						tickcounter = 90;
					}
				} else if (counter[1] == 3 && options.shuffle) {
					sound.play("Tick");
					options.shuffle = false;
					tickcounter = 90;
				} else if (counter[1] == 4 && options.noDraw) {
					// sound.play("Tick");
					// options.noDraw = false;
					// tickcounter = 90;
				} else if (counter[1] == 5 && options.devil) {
					// sound.play("Tick");
					// options.devil = false;
					// tickcounter = 90;
				} else if (counter[1] == 6 && options.madBomber) {
					sound.play("Tick");
					options.madBomber = false;
					tickcounter = 90;
				} else if (counter[1] == 7 && options.bonusGame) {
					// sound.play("Tick");
					// options.bonusGame = false;
					// tickcounter = 90;
				} else if (counter[1] == 9) {
					if (options.comLevel > 1) {
						tempComLevelCounter--;
						tickcounter = 90;
					}
				}
				if (tempComLevelCounter != options.comLevel) {
					sound.play("Tick");
					options.comLevel = tempComLevelCounter;
					tickcounter = 90;
				} else if (tempBattlesCounter != options.numberOfBattles) {
					sound.play("Tick");
					options.numberOfBattles = tempBattlesCounter;
					tickcounter = 90;
				} else if (tempTimeCounter != options.time) {
					sound.play("Tick");
					options.time = tempTimeCounter;
					tickcounter = 90;
				}
			} else if (keyCode == options.controllers[0].rightButton) {
				int tempComLevelCounter = options.comLevel;
				int tempBattlesCounter = options.numberOfBattles;
				int tempTimeCounter = (int) options.time;
				if (counter[1] == 1) {
					if (options.numberOfBattles < 5) {
						tempBattlesCounter++;
						tickcounter = 90;
					}
				} else if (counter[1] == 2) {
					if (options.time < 9) {
						tempTimeCounter++;
						tickcounter = 90;
					}
				} else if (counter[1] == 3 && !options.shuffle) {
					sound.play("Tick");
					options.shuffle = true;
					tickcounter = 90;
				} else if (counter[1] == 4 && !options.noDraw) {
					// sound.play("Tick");
					// options.noDraw = true;
					// tickcounter = 90;
				} else if (counter[1] == 5 && !options.devil) {
					// sound.play("Tick");
					// options.devil = true;
					// tickcounter = 90;
				} else if (counter[1] == 6 && !options.madBomber) {
					sound.play("Tick");
					// options.madBomber = true;
					tickcounter = 90;
				} else if (counter[1] == 7 && !options.bonusGame) {
					// sound.play("Tick");
					// options.bonusGame = true;
					// tickcounter = 90;
				} else if (counter[1] == 9) {
					if (options.comLevel < 5) {
						tempComLevelCounter++;
						tickcounter = 90;
					}
				}
				if (tempComLevelCounter != options.comLevel) {
					sound.play("Tick");
					options.comLevel = tempComLevelCounter;
					tickcounter = 90;
				} else if (tempBattlesCounter != options.numberOfBattles) {
					sound.play("Tick");
					options.numberOfBattles = tempBattlesCounter;
					tickcounter = 90;
				} else if (tempTimeCounter != options.time) {
					sound.play("Tick");
					options.time = tempTimeCounter;
					tickcounter = 90;
				}
			}
		} else if (counter[0] == 11) {
			long tempCounter = counter[1];
			long tempCounter5 = counter[5];
			boolean[] playerChosen = new boolean[12];
			for (int i = 0; i < playerChosen.length; i++)
				for (int j = 0; j < players.size(); j++)
					if (players.get(j).character != null && players.get(j).character.equals(i))
						playerChosen[i] = true;
			boolean leftClicked = false, rightClicked = false, enterClicked = false, backClicked = false;
			if (counter[5] == -1)
				counter[5] = 0;
			if (network.isPlayingOnline()) {
				if (keyCode == options.controllers[0].leftButton)
					leftClicked = true;
				else if (keyCode == options.controllers[0].rightButton)
					rightClicked = true;
				else if (keyCode == options.controllers[0].AButton || keyCode == options.controllers[0].CButton)
					enterClicked = true;
				else if (keyCode == options.controllers[0].BButton)
					backClicked = true;
			} else {
				int num = (int) counter[5];
				int player = -1;
				for (int i = 0; i < options.players.length; i++)
					if (options.players[i] == 1) {
						player = i;
						break;
					}
				if (counter[5] == 8 || options.players[(int) counter[5]] == 2)
					num = player;
				if (num == -1) {
					for (int i = 0; i < options.players.length; i++) {
						num = i;
						try {
							if (keyCode == options.controllers[num].leftButton)
								leftClicked = true;
							else if (keyCode == options.controllers[num].rightButton)
								rightClicked = true;
							else if (keyCode == options.controllers[num].AButton
									|| keyCode == options.controllers[num].CButton)
								enterClicked = true;
							else if (keyCode == options.controllers[num].BButton)
								backClicked = true;
						} catch (Exception e) {
						}
					}
				} else if (options.players[num] == 1) {
					if (keyCode == options.controllers[num].leftButton)
						leftClicked = true;
					else if (keyCode == options.controllers[num].rightButton)
						rightClicked = true;
					else if (keyCode == options.controllers[num].AButton || keyCode == options.controllers[num].CButton)
						enterClicked = true;
					else if (keyCode == options.controllers[num].BButton)
						backClicked = true;
				}
			}
			if (leftClicked) {
				int nearestNonEmpty = -1;
				for (int i = (int) counter[1] - 1; i >= 0 && nearestNonEmpty == -1; i--)
					if (!playerChosen[i])
						nearestNonEmpty = i;
				if (nearestNonEmpty != -1)
					tempCounter = nearestNonEmpty;
			} else if (rightClicked) {
				int nearestNonEmpty = -1;
				for (int i = (int) counter[1] + 1; i < 12 && nearestNonEmpty == -1; i++)
					if (!playerChosen[i])
						nearestNonEmpty = i;
				if (nearestNonEmpty != -1)
					tempCounter = nearestNonEmpty;
			} else if (enterClicked) {
				while (players.size() == 0)
					sleep(10);
				if (counter[5] >= players.size()) {
					sound.play("Finished");
					counter[4] = 45;
					counter[0] = 12;
					if (network.isPlayingOnline())
						network.inputTurn = 0;
					if (options.teamMode) {
						counter[1] = 3;
						tempCounter = 3;
					} else {
						counter[1] = 4;
						tempCounter = 4;
					}
					options.bombersOptionsFinished = true;
				} else {
					sound.play("Select");
					Character c = new Character(pictures, (int) counter[1]);
					c.animation = Animation.BOMBER_SELECT_UP;
					players.get((int) counter[5]).character = c;
					counter[5]++;
					if (network.isPlayingOnline()) {
						if (options.players[(int) counter[5]] == 2)
							network.inputTurn = 0;
						else
							network.inputTurn = (int) counter[5];
						if (network.inputTurn >= players.size())
							network.inputTurn = 0;
					}
					int nearestNonEmpty = -1;
					for (int i = (int) counter[1] + 1; i < 12 && nearestNonEmpty == -1; i++)
						if (!playerChosen[i])
							nearestNonEmpty = i;
					if (nearestNonEmpty != -1)
						tempCounter = nearestNonEmpty;
					else {
						for (int i = (int) counter[1] - 1; i >= 0 && nearestNonEmpty == -1; i--)
							if (!playerChosen[i])
								nearestNonEmpty = i;
						if (nearestNonEmpty != -1)
							tempCounter = nearestNonEmpty;
					}
				}
			} else if (backClicked) {
				sound.play("Back");
				if (counter[5] == 0) {
					counter[4] = 25;
					counter[0] = 12;
					tempCounter = 2;
					counter[1] = 2;
					players = new ArrayList<Player>();
					options.bombersOptionsFinished = false;
				} else {
					counter[5]--;
					if (network.isPlayingOnline()) {
						if (options.players[(int) counter[5]] == 2) // cpu
							network.inputTurn = 0;
						else
							network.inputTurn = (int) counter[5];
						if (network.inputTurn >= players.size())
							network.inputTurn = 0;
					}
					tempCounter = players.get((int) counter[5]).character.characterIndex;
					players.get((int) counter[5]).character.animation = Animation.BOMBER_SELECT_DOWN;
				}
			}
			if (tempCounter != counter[1]) {
				if (tempCounter5 == counter[5])
					sound.play("Tick");
				counter[1] = tempCounter;
			}
		} else if (counter[0] == 14) {
			// team selections
		} else if (counter[0] == 17) {
			// stage selection
			if (keyCode == options.controllers[0].AButton || keyCode == options.controllers[0].CButton) {
				if (network.isPlayingOnline() && !network.weAreHost())
					return; // They'll send us this info
				int num = (int) (360 - counter[1]) / 45;
				if (num == 8)
					num = 0;
				boolean remainderExists = (360 - counter[1]) % 45 != 0;
				if (pictures.availableStageAmt - 1 >= num && (!remainderExists || counter[1] == 0)) {
					if (network.isPlayingOnline())
						network.server.sendMessageToAllClients(
								"SERVER:STAGE_SELECT" + Client.SPLITTER + counter[1] + Client.SPLITTER + num);
					sound.play("Stage Selection");
					stage = null;
					pause = Pause.ALL;
					counter[0] = 19;
					counter[6] = counter[1];
					counter[1] = num; // stage select
					counter[2] = 0;
					counter[3] = 0;
					counter[4] = 50;
					counter[5] = 0;
					pause = Pause.NONE;
				}
			} else if (keyCode == options.controllers[0].BButton) {
				sound.play("Back");
				counter[0] = 18;
				counter[1] = 4;
				counter[3] = 0;
				counter[4] = 50;
			} else if (keyCode == options.controllers[0].leftButton) {
				if (counter[5] == 0) {
					pause = Pause.ALL;
					sound.play("Stage Change");
					counter[5] = 45;
					pause = Pause.NONE;
				}
			} else if (keyCode == options.controllers[0].rightButton) {
				if (counter[5] == 0) {
					pause = Pause.ALL;
					sound.play("Stage Change");
					counter[5] -= 45;
					pause = Pause.NONE;
				}
			}
		}
	}

	public void tickStage() {
		if (temp[1].contains("Played Match End")) {
			long timeSinceEnd = currentTimeMillis()
					- Long.parseLong(temp[1].split(".Played Match End:")[1].split(":")[0]);
			if (timeSinceEnd > 10000) {
				if (network.isPlayingOnline())
					network.setClientsReady(false);
				state.setNextStateAfterFade(StateEnum.END_OF_MATCH, this);
				counter[1] = 0;
				return;
			}
		}
		long endTime = (long) (counter[2] + options.time * 60000);
		long timeLeft = endTime - currentTimeMillis() + 4000;
		int secondsLeft = (int) Math.floor((double) timeLeft / 1000d);
		int alivePlayers = 0;
		for (int i = 0; i < players.size(); i++)
			if (players.get(i).deadTimer == -1)
				alivePlayers++;
		if (!temp[1].contains("Played Match Intro")) {
			if (music.isPlaying())
				music.stop();
			sound.play("Match Start");
			while (!sound.isPlaying("Match Start")) {
				// wait
			}
			temp[1] += ".Played Match Intro";
		} else if (!sound.isPlaying("Match Start") && !temp[1].contains("Played Battle")) {
			music.loop("Battle");
			temp[1] += ".Played Battle";
		}
		if (secondsLeft < 65 && !temp[1].contains("Last Minute Whistle") && alivePlayers > 1) {
			sound.play("Last Minute Whistle");
			temp[1] += ".Last Minute Whistle";
		}
		if (secondsLeft < 60) {
			if (!temp[1].contains("Last First") && alivePlayers > 1) {
				sound.play("Last First");
				temp[1] += ".Last First";
			}
			if (alivePlayers > 1) {
				int weightsToCreateAmt = (int) ((61000l - timeLeft) / 625 - 1);
				int weightsCreated = 0;
				ArrayList<com.github.vegeto079.saturnbomberman.objects.Object> tempObjects = objects;
				for (Object obj : tempObjects) {
					if (obj instanceof Weight) {
						weightsCreated++;
					}
				}
				weightsCreated = weightsCreated / 2;
				while (weightsToCreateAmt != weightsCreated) {
					try {
						Point weightPoint = Weight.leftDropCourse[weightsCreated];
						objects.add(new Weight(weightPoint, this));
						weightPoint = Weight.rightDropCourse[weightsCreated];
						objects.add(new Weight(weightPoint, this));
						weightsCreated += 1;
					} catch (Exception e) {
						// Got to the next of drop course
						weightsCreated++;
					}
				}
			}
		}
		for (int i = 0; i < objects.size(); i++) {
			objects.get(i).tick(this);
			if (objects.get(i).finished) {
				objects.remove(i);
				i--;
			}
		}

	}

	public void tickStage(Graphics2D g) {
		stage.draw(g, this);
		if (debug) {
			g.setFont(defaultFont);
			g.setColor(Color.RED);
			int[][] stageI = stage.get();
			for (int i = 0; i < stageI.length; i++)
				for (int j = 0; j < stageI[0].length; j++)
					g.drawString("" + stageI[i][j], Stage.getExactTileMidPoint(this, new Point(j, i)).x - 20,
							Stage.getExactTileMidPoint(this, new Point(j, i)).y + 20);
			for (int i = 0; i < objects.size(); i++)
				if (objects.get(i) instanceof com.github.vegeto079.saturnbomberman.objects.Bomb) {
					com.github.vegeto079.saturnbomberman.objects.Bomb bomb = (com.github.vegeto079.saturnbomberman.objects.Bomb) objects
							.get(i);
					Point movingToPoint = new Point(bomb.exactPoint.toPoint().x + 23, bomb.exactPoint.toPoint().y + 24);
					Point topRightPoint = new Point(movingToPoint.x + 14, movingToPoint.y - 8);
					Point bottomLeftPoint = new Point(movingToPoint.x - 9, movingToPoint.y + 14);
					Point bottomRightPoint = new Point(movingToPoint.x + 14, movingToPoint.y + 14);
					Point topLeftPoint = new Point(movingToPoint.x - 9, movingToPoint.y - 8);
					// g.setColor(Color.YELLOW);
					// g.fillRect(topLeftPoint.x, topLeftPoint.y,
					// bottomRightPoint.x
					// - topLeftPoint.x, bottomRightPoint.y - topLeftPoint.y);
					g.setColor(Color.WHITE);
					g.fillOval(movingToPoint.x, movingToPoint.y, 5, 5);
					g.fillOval(topRightPoint.x, topRightPoint.y, 5, 5);
					g.fillOval(bottomLeftPoint.x, bottomLeftPoint.y, 5, 5);
					g.fillOval(bottomRightPoint.x, bottomRightPoint.y, 5, 5);
					g.fillOval(topLeftPoint.x, topLeftPoint.y, 5, 5);
					// if ((xDist < 40 && xDist > 8) || (yDist < 20 && yDist >
					// 8)
					// || (xDist > -20 && xDist < -8) || (yDist > -20 && yDist <
					// -8))
				}
			g.setColor(Color.WHITE);
			for (int i = 0; i < players.size(); i++) {
				if (players.get(i).deadTimer != -1)
					continue;
				Point movingToPoint = new Point(players.get(i).getMiddleOfBodyPoint().x,
						players.get(i).getMiddleOfBodyPoint().y);
				Point topRightPoint = new Point(movingToPoint.x + 14, movingToPoint.y - 8);
				Point bottomLeftPoint = new Point(movingToPoint.x - 9, movingToPoint.y + 14);
				Point bottomRightPoint = new Point(movingToPoint.x + 14, movingToPoint.y + 14);
				Point topLeftPoint = new Point(movingToPoint.x - 9, movingToPoint.y - 8);
				g.fillOval(movingToPoint.x, movingToPoint.y, 5, 5);
				g.fillOval(topRightPoint.x, topRightPoint.y, 5, 5);
				g.fillOval(bottomLeftPoint.x, bottomLeftPoint.y, 5, 5);
				g.fillOval(bottomRightPoint.x, bottomRightPoint.y, 5, 5);
				g.fillOval(topLeftPoint.x, topLeftPoint.y, 5, 5);
			}
		}
	}

	public void tickStage(int keyCode) {
		if (network.isPlayingOnline()) {
			for (int i = 0; i < options.players.length; i++)
				if (i == network.myInputTurnP1) {
					if (keyCode == options.controllers[0].upButton)
						players.get(i).keyDown(0);
					else if (keyCode == options.controllers[0].downButton)
						players.get(i).keyDown(1);
					else if (keyCode == options.controllers[0].leftButton)
						players.get(i).keyDown(2);
					else if (keyCode == options.controllers[0].rightButton)
						players.get(i).keyDown(3);
					else if (keyCode == options.controllers[0].AButton)
						players.get(i).keyDown(4);
					else if (keyCode == options.controllers[0].BButton)
						players.get(i).keyDown(5);
					else if (keyCode == options.controllers[0].CButton)
						players.get(i).keyDown(6);
					else if (keyCode == options.controllers[0].LButton)
						players.get(i).keyDown(7);
					else if (keyCode == options.controllers[0].RButton)
						players.get(i).keyDown(8);
				} else if (i == network.myInputTurnP2) {
					if (keyCode == options.controllers[1].upButton)
						players.get(i).keyDown(0);
					else if (keyCode == options.controllers[1].downButton)
						players.get(i).keyDown(1);
					else if (keyCode == options.controllers[1].leftButton)
						players.get(i).keyDown(2);
					else if (keyCode == options.controllers[1].rightButton)
						players.get(i).keyDown(3);
					else if (keyCode == options.controllers[1].AButton)
						players.get(i).keyDown(4);
					else if (keyCode == options.controllers[1].BButton)
						players.get(i).keyDown(5);
					else if (keyCode == options.controllers[1].CButton)
						players.get(i).keyDown(6);
					else if (keyCode == options.controllers[1].LButton)
						players.get(i).keyDown(7);
					else if (keyCode == options.controllers[1].RButton)
						players.get(i).keyDown(8);
				}
		} else
			for (int i = 0; i < options.players.length; i++)
				if (options.players[i] == 1)
					if (keyCode == options.controllers[i].upButton)
						players.get(i).keyDown(0);
					else if (keyCode == options.controllers[i].downButton)
						players.get(i).keyDown(1);
					else if (keyCode == options.controllers[i].leftButton)
						players.get(i).keyDown(2);
					else if (keyCode == options.controllers[i].rightButton)
						players.get(i).keyDown(3);
					else if (keyCode == options.controllers[i].AButton)
						players.get(i).keyDown(4);
					else if (keyCode == options.controllers[i].BButton)
						players.get(i).keyDown(5);
					else if (keyCode == options.controllers[i].CButton)
						players.get(i).keyDown(6);
					else if (keyCode == options.controllers[i].LButton)
						players.get(i).keyDown(7);
					else if (keyCode == options.controllers[i].RButton)
						players.get(i).keyDown(8);
	}

	public void tickGoingToStage() {
		temp[0] = "";
		temp[1] = "";
		music.stop();
		if (network.isPlayingOnline()) {
			if (counter[7] != 0 && counter[1] != -1) {
				stage = new Stage((int) counter[6], this);
				counter[1] = -1;
			} else if (counter[7] != 0 && stage != null && stage.initiated) {
				if (counter[3] == 0) {
					if (counter[2] <= currentTimeMillis())
						if (network.weAreHost()) {
							for (int i = 0; i < network.connectedClients.size(); i++)
								if (network.connectedClients.get(i)[0].equals(options.multiplayer.username))
									network.connectedClients.set(i, new String[] { network.connectedClients.get(i)[0],
											network.connectedClients.get(i)[1], "1" });
						} else if (!network.client.containsMessageQueued("CLIENT:READY"))
							network.client.sendMessageToServer("CLIENT:READY");
				} else if (counter[2] <= currentTimeMillis() && counter[2] != -1 && counter[3] == 2) {
					counter[7] = 0;
					counter[6] = 0;
					counter[3] = 1;
					state.set(StateEnum.STAGE, this);
				}
			} else {
				if (!network.weAreHost()) {
					if (!network.client.containsMessageQueued("CLIENT:NEEDSEED"))
						network.client.sendMessageToServer("CLIENT:NEEDSEED");
				} else
					counter[7] = randomCount;
			}
		} else {
			if (counter[1] != -1) {
				stage = new Stage((int) counter[1], this);
				counter[1] = -1;
				state.set(StateEnum.STAGE, this);
			}
		}
	}

	public void tickGoingToStage(Graphics2D g) {
		if (network.isPlayingOnline())
			draw.textCenteredWithTexturedImage(g, Color.RED, false, getHeight() / 2 - 27 / 2, "SYNCING...");
	}

	public void tickGoingToStage(int keyCode) {

	}

	public void tickEndOfMatch() {
		if (pictures.stageIsInMemory()) {
			pictures.unloadStageFromMemory();
			stage = null;
			for (int i = 0; i < players.size(); i++)
				if (players.get(i).ai != null) {
					players.get(i).ai.reset();
					// players.get(i).ai = null;
				}
		}
		fadecounterenabled = 0;
		if (fadecounter != -2) {
			if (fadecounter <= HIGHEST_FADE_COUNTER)
				fadeIn();
			if (fadecounter > HIGHEST_FADE_COUNTER)
				fadecounter = HIGHEST_FADE_COUNTER + 1;
		}
		if (!temp[1].contains("Played Show Statistics") || !temp[1].contains("playing")) {
			if (temp[1].contains("Played Selection"))
				temp[1] = "Music Stopped --" + currentTimeMillis() + "--Played Show Statistics, playing =="
						+ currentTimeMillis() + "==| Song Started";
			if (!temp[1].contains("Music Stopped")) {
				temp[1] = "Music Stopped --" + currentTimeMillis() + "--";
				music.stop();
			} else {
				long stoppedTime = Long.parseLong(temp[1].split("--")[1]);
				if (currentTimeMillis() - stoppedTime > 1000 && fadecounterenabled == 0) {
					temp[1] += "Played Show Statistics";
					sound.stopAll();
					sound.play("Show Statistics");
					while (!sound.isPlaying())
						try {
							Thread.sleep(1);
						} catch (Exception e) {
						}
					temp[1] += ", playing ==" + currentTimeMillis() + "==";
				}
			}
		} else if (temp[1].contains("Show Statistics") && temp[1].contains("playing")
				&& !temp[1].contains("| Song Started") && !sound.isPlaying()) {
			long playedTime = Long.parseLong(temp[1].split("==")[1]);
			if (currentTimeMillis() - playedTime > 4500) {
				temp[1] += "| Song Started";
				music.loop("Selection");
			}
		}
		counter[2]++;
		if (counter[2] > 260 * 3) {
			counter[2] = 0;
		}
		if (counter[0] == 1 || counter[0] == 5 || counter[0] == 10 || counter[0] == 11 || counter[0] == 12) {
			counter[4]++;
			if (counter[4] > 25) {
				setPause(Pause.PAINT);
				long setCounter0 = counter[0];
				if (counter[0] == 1)
					setCounter0 = 2;
				if (counter[0] == 5)
					setCounter0 = 2;
				if (counter[0] == 10)
					setCounter0 = 7;
				if (counter[0] == 11) {
					setCounter0 = 4;
					counter[1] = counter[5];
					counter[5] = 0;
				}
				counter[0] = setCounter0;
				counter[4] = 0;
				setPause(Pause.NONE);
			}
		}
		if (counter[0] == 3 || counter[0] == 6 || counter[0] == 8) {
			counter[4]--;
			if (counter[4] < 1) {
				setPause(Pause.PAINT);
				long setCounter0 = counter[0];
				if (counter[0] == 3)
					setCounter0 = 4;
				if (counter[0] == 6)
					setCounter0 = 7;
				if (counter[0] == 8)
					setCounter0 = 9;
				counter[0] = setCounter0;
				counter[4] = 0;
				setPause(Pause.NONE);
			}
		}
		int num = 0, counterToSet = 0;
		while (true) {
			num += 4;
			if (counter[2] < num) {
				counter[3] = counterToSet;
				break;
			}
			counterToSet++;
			if (counterToSet > 5)
				counterToSet = 0;
		}
		if (counter[6] != 0) {
			long tempCounter = counter[1];
			tempCounter = upAndDownMenuResult(counter[1], (int) counter[6], players.size(), true, false);
			if (tempCounter != counter[1]) {
				sound.play("Tick");
				counter[1] = tempCounter;
				tickcounter = 75;
			}
			counter[6] = 0;
		}
	}

	public void tickEndOfMatch(Graphics2D g) {
		draw.rectangle(g, 100, 20, 600, 400, new Color(248, 208, 232));
		for (int i = 0; i < 6 * 3; i++)
			for (int j = 0; j < 6 * 3; j++) {
				long x = 200 + i * 130 - counter[2];
				long y = j * 130 - counter[2];
				if (Tools.isOdd(i))
					y += 130 / 2;
				g.drawImage(pictures.selectionBombBackground2, (int) x - 19, (int) y - 8, this);
			}
		if (counter[0] == 0) {
			Color outline1 = new Color(72, 48, 72), outline2 = new Color(224, 176, 0),
					outline3 = new Color(248, 224, 96);
			int rectX = 282, rectY = 109, rectWidth = 198, rectHeight = 6;
			if (players.size() < 6) {
				rectX += 3;
				for (int i = 0; i < players.size(); i++) {
					rectHeight += 48;
					g.drawImage(pictures.selectionButtons.get(15).get(0), rectX + 1, rectY + 1 + i * 48, this);
					if (i > 0) {
						int luminosity = 40, strength = 250;
						if (i == 1)
							g.setColor(new Color(strength, 0, 0, luminosity));
						else if (i == 2)
							g.setColor(new Color(0, strength, strength, luminosity));
						else if (i == 3)
							g.setColor(new Color(strength, strength, 0, luminosity));
						else if (i == 4)
							g.setColor(new Color(0, strength, 0, luminosity));
						g.fillRect(rectX, rectY + 3 + i * 48, rectWidth, 48);
					}
					int show = 2, num = 0;
					if (counter[2] >= 260 * 3 / 3 * 2) {
						show = 1;
						num = players.get(i).stats.getWins();
					} else if (counter[2] > 260 * 3 / 3) {
						show = 0;
						num = players.get(i).stats.getTotalKilledPlayersAmt();
					} else {
						show = 2;
						num = players.get(i).stats.getRank(players);
					}
					g.drawImage(pictures.selectionButtons.get(14).get(show), rectX + 33, rectY + 1 + i * 48, this);
					// g.drawImage(pictures.selectionButtons.get(16).get(num),
					// rectX + 69,
					// rectY + 13 + i * 48, this);
					int width = Tools.getTextWidth(g, "" + num);
					draw.textWithTexturedImage(g, Color.YELLOW, false, rectX + 75 - width / 2, rectY + 37 + i * 48,
							"" + num);
				}
				g.setColor(outline1);
				g.drawRect(rectX - 3, rectY - 3, rectWidth + 5, rectHeight + 5);
				g.drawRect(rectX - 2, rectY - 2, rectWidth + 3, rectHeight + 3);
				g.setColor(outline2);
				g.drawRect(rectX - 1, rectY - 1, rectWidth + 1, rectHeight + 1);
				g.drawRect(rectX, rectY, rectWidth - 1, rectHeight - 1);
				g.setColor(outline3);
				g.drawRect(rectX + 1, rectY + 1, rectWidth - 3, rectHeight - 3);
				g.drawRect(rectX + 2, rectY + 2, rectWidth - 5, rectHeight - 5);
				for (int i = 0; i < players.size(); i++) {
					if (!players.get(i).isReset)
						players.get(i).reset();
					players.get(i).character.draw(g, rectX + 3, rectY - 13 + i * 48, this);
				}
			} else {
				rectX -= 109;
				for (int i = 0; i < 5; i++) {
					rectHeight += 48;
					g.drawImage(pictures.selectionButtons.get(15).get(0), rectX + 1, rectY + 1 + i * 48, this);
					if (i > 0) {
						int luminosity = 40, strength = 250;
						if (i == 1)
							g.setColor(new Color(strength, 0, 0, luminosity));
						else if (i == 2)
							g.setColor(new Color(0, strength, strength, luminosity));
						else if (i == 3)
							g.setColor(new Color(strength, strength, 0, luminosity));
						else if (i == 4)
							g.setColor(new Color(0, strength, 0, luminosity));
						g.fillRect(rectX, rectY + 3 + i * 48, rectWidth, 48);
					}
					int show = 2, num = 0;
					if (counter[2] >= 260 * 3 / 3 * 2) {
						show = 1;
						num = players.get(i).stats.getWins();
					} else if (counter[2] > 260 * 3 / 3) {
						show = 0;
						num = players.get(i).stats.getThisStageKilledPlayersAmt();
					} else {
						show = 2;
						num = players.get(i).stats.getRank(players);
					}
					g.drawImage(pictures.selectionButtons.get(14).get(show), rectX + 33, rectY + 1 + i * 48, this);
					// g.drawImage(pictures.selectionButtons.get(16).get(num),
					// rectX + 67,
					// rectY + 13 + i * 48, this);
					int width = Tools.getTextWidth(g, "" + num);
					draw.textWithTexturedImage(g, Color.YELLOW, false, rectX + 75 - width / 2, rectY + 37 + i * 48,
							"" + num);
				}
				g.setColor(outline1);
				g.drawRect(rectX - 3, rectY - 3, rectWidth + 5, rectHeight + 5);
				g.drawRect(rectX - 2, rectY - 2, rectWidth + 3, rectHeight + 3);
				g.setColor(outline2);
				g.drawRect(rectX - 1, rectY - 1, rectWidth + 1, rectHeight + 1);
				g.drawRect(rectX, rectY, rectWidth - 1, rectHeight - 1);
				g.setColor(outline3);
				g.drawRect(rectX + 1, rectY + 1, rectWidth - 3, rectHeight - 3);
				g.drawRect(rectX + 2, rectY + 2, rectWidth - 5, rectHeight - 5);
				for (int i = 0; i < 5; i++) {
					if (!players.get(i).isReset)
						players.get(i).reset();
					players.get(i).character.draw(g, rectX + 3, rectY - 13 + i * 48, this);
				}
				rectX += 224;
				rectHeight = 6;
				for (int i = 5; i < players.size(); i++) {
					rectHeight += 48;
					g.drawImage(pictures.selectionButtons.get(15).get(0), rectX + 1, rectY + 1 + (i - 5) * 48, this);
					if ((i - 5) > 0) {
						int luminosity = 40, strength = 250;
						if ((i - 5) == 1)
							g.setColor(new Color(strength, 0, 0, luminosity));
						else if ((i - 5) == 2)
							g.setColor(new Color(0, strength, strength, luminosity));
						else if ((i - 5) == 3)
							g.setColor(new Color(strength, strength, 0, luminosity));
						else if ((i - 5) == 4)
							g.setColor(new Color(0, strength, 0, luminosity));
						g.fillRect(rectX, rectY + 3 + (i - 5) * 48, rectWidth, 48);
					}
					int show = 2, num = 0;
					if (counter[2] >= 260 * 3 / 3 * 2) {
						show = 1;
						num = players.get(i).stats.getWins();
					} else if (counter[2] > 260 * 3 / 3) {
						show = 0;
						num = players.get(i).stats.getThisStageKilledPlayersAmt();
					} else {
						show = 2;
						num = players.get(i).stats.getRank(players);
					}
					g.drawImage(pictures.selectionButtons.get(14).get(show), rectX + 33, rectY + 1 + (i - 5) * 48,
							this);
					// g.drawImage(pictures.selectionButtons.get(16).get(num),
					// rectX + 67,
					// rectY + 13 + (i-5) * 48, this);
					int width = Tools.getTextWidth(g, "" + num);
					draw.textWithTexturedImage(g, Color.YELLOW, false, rectX + 75 - width / 2,
							rectY + 37 + (i - 5) * 48, "" + num);
				}
				g.setColor(outline1);
				g.drawRect(rectX - 3, rectY - 3, rectWidth + 5, rectHeight + 5);
				g.drawRect(rectX - 2, rectY - 2, rectWidth + 3, rectHeight + 3);
				g.setColor(outline2);
				g.drawRect(rectX - 1, rectY - 1, rectWidth + 1, rectHeight + 1);
				g.drawRect(rectX, rectY, rectWidth - 1, rectHeight - 1);
				g.setColor(outline3);
				g.drawRect(rectX + 1, rectY + 1, rectWidth - 3, rectHeight - 3);
				g.drawRect(rectX + 2, rectY + 2, rectWidth - 5, rectHeight - 5);
				for (int i = 5; i < players.size(); i++) {
					if (!players.get(i).isReset)
						players.get(i).reset();
					players.get(i).character.draw(g, rectX + 3, rectY - 11 + (i - 5) * 48, this);
				}
			}
			if (temp[1].contains("| Song Started")) {
				BufferedImage imageToDraw = pictures.selectionButtons.get(17).get(0);
				if (tickcounter >= 90)
					imageToDraw = Pictures.brighten(imageToDraw);
				g.drawImage(imageToDraw, 548, 384, this);
			}
			g.drawImage(pictures.selectionBackground, 8, 0, this);
		} else if (counter[0] == 1) {
			// Going to overall stats page
			Color outline1 = new Color(72, 48, 72), outline2 = new Color(224, 176, 0),
					outline3 = new Color(248, 224, 96);
			int rectX = 282, rectY = 109, rectWidth = 198, rectHeight = 6;
			if (players.size() < 6) {
				rectX += 3;
				for (int i = 0; i < players.size(); i++) {
					rectHeight += 48;
					g.drawImage(pictures.selectionButtons.get(15).get(0), rectX + 1 - (int) counter[4] * 20,
							rectY + 1 + i * 48, this);
					if (i > 0) {
						int luminosity = 40, strength = 250;
						if (i == 1)
							g.setColor(new Color(strength, 0, 0, luminosity));
						else if (i == 2)
							g.setColor(new Color(0, strength, strength, luminosity));
						else if (i == 3)
							g.setColor(new Color(strength, strength, 0, luminosity));
						else if (i == 4)
							g.setColor(new Color(0, strength, 0, luminosity));
						g.fillRect(rectX - (int) counter[4] * 20, rectY + 3 + i * 48, rectWidth, 48);
					}
					int show = 2, num = 0;
					if (counter[2] >= 260 * 3 / 3 * 2) {
						show = 1;
						num = players.get(i).stats.getWins();
					} else if (counter[2] > 260 * 3 / 3) {
						show = 0;
						num = players.get(i).stats.getThisStageKilledPlayersAmt();
					} else {
						show = 2;
						num = players.get(i).stats.getRank(players);
					}
					g.drawImage(pictures.selectionButtons.get(14).get(show), rectX + 33 - (int) counter[4] * 20,
							rectY + 1 + i * 48, this);
					// g.drawImage(pictures.selectionButtons.get(16).get(num),
					// rectX + 69,
					// rectY + 13 + i * 48, this);
					int width = Tools.getTextWidth(g, "" + num);
					draw.textWithTexturedImage(g, Color.YELLOW, false, rectX + 75 - width / 2 - (int) counter[4] * 20,
							rectY + 37 + i * 48, "" + num);
				}
				g.setColor(outline1);
				g.drawRect(rectX - 3 - (int) counter[4] * 20, rectY - 3, rectWidth + 5, rectHeight + 5);
				g.drawRect(rectX - 2 - (int) counter[4] * 20, rectY - 2, rectWidth + 3, rectHeight + 3);
				g.setColor(outline2);
				g.drawRect(rectX - 1 - (int) counter[4] * 20, rectY - 1, rectWidth + 1, rectHeight + 1);
				g.drawRect(rectX - (int) counter[4] * 20, rectY, rectWidth - 1, rectHeight - 1);
				g.setColor(outline3);
				g.drawRect(rectX + 1 - (int) counter[4] * 20, rectY + 1, rectWidth - 3, rectHeight - 3);
				g.drawRect(rectX + 2 - (int) counter[4] * 20, rectY + 2, rectWidth - 5, rectHeight - 5);
				for (int i = 0; i < players.size(); i++) {
					if (!players.get(i).isReset)
						players.get(i).reset();
					players.get(i).character.draw(g, rectX + 3 - (int) counter[4] * 20, rectY - 13 + i * 48, this);
				}
			} else {
				rectX -= 109;
				for (int i = 0; i < 5; i++) {
					rectHeight += 48;
					g.drawImage(pictures.selectionButtons.get(15).get(0), rectX + 1 - (int) counter[4] * 20,
							rectY + 1 + i * 48, this);
					if (i > 0) {
						int luminosity = 40, strength = 250;
						if (i == 1)
							g.setColor(new Color(strength, 0, 0, luminosity));
						else if (i == 2)
							g.setColor(new Color(0, strength, strength, luminosity));
						else if (i == 3)
							g.setColor(new Color(strength, strength, 0, luminosity));
						else if (i == 4)
							g.setColor(new Color(0, strength, 0, luminosity));
						g.fillRect(rectX - (int) counter[4] * 20, rectY + 3 + i * 48, rectWidth, 48);
					}
					int show = 2, num = 0;
					if (counter[2] >= 260 * 3 / 3 * 2) {
						show = 1;
						num = players.get(i).stats.getWins();
					} else if (counter[2] > 260 * 3 / 3) {
						show = 0;
						num = players.get(i).stats.getThisStageKilledPlayersAmt();
					} else {
						show = 2;
						num = players.get(i).stats.getRank(players);
					}
					g.drawImage(pictures.selectionButtons.get(14).get(show), rectX + 33 - (int) counter[4] * 20,
							rectY + 1 + i * 48, this);
					int width = Tools.getTextWidth(g, "" + num);
					draw.textWithTexturedImage(g, Color.YELLOW, false, rectX + 75 - width / 2 - (int) counter[4] * 20,
							rectY + 37 + i * 48, "" + num);
				}
				g.setColor(outline1);
				g.drawRect(rectX - 3 - (int) counter[4] * 20, rectY - 3, rectWidth + 5, rectHeight + 5);
				g.drawRect(rectX - 2 - (int) counter[4] * 20, rectY - 2, rectWidth + 3, rectHeight + 3);
				g.setColor(outline2);
				g.drawRect(rectX - 1 - (int) counter[4] * 20, rectY - 1, rectWidth + 1, rectHeight + 1);
				g.drawRect(rectX - (int) counter[4] * 20, rectY, rectWidth - 1, rectHeight - 1);
				g.setColor(outline3);
				g.drawRect(rectX + 1 - (int) counter[4] * 20, rectY + 1, rectWidth - 3, rectHeight - 3);
				g.drawRect(rectX + 2 - (int) counter[4] * 20, rectY + 2, rectWidth - 5, rectHeight - 5);
				for (int i = 0; i < 5; i++) {
					if (!players.get(i).isReset)
						players.get(i).reset();
					players.get(i).character.draw(g, rectX + 3 - (int) counter[4] * 20, rectY - 13 + i * 48, this);
				}
				rectX += 224;
				rectHeight = 6;
				for (int i = 5; i < players.size(); i++) {
					rectHeight += 48;
					g.drawImage(pictures.selectionButtons.get(15).get(0), rectX + 1 - (int) counter[4] * 20,
							rectY + 1 + (i - 5) * 48, this);
					if ((i - 5) > 0) {
						int luminosity = 40, strength = 250;
						if ((i - 5) == 1)
							g.setColor(new Color(strength, 0, 0, luminosity));
						else if ((i - 5) == 2)
							g.setColor(new Color(0, strength, strength, luminosity));
						else if ((i - 5) == 3)
							g.setColor(new Color(strength, strength, 0, luminosity));
						else if ((i - 5) == 4)
							g.setColor(new Color(0, strength, 0, luminosity));
						g.fillRect(rectX - (int) counter[4] * 20, rectY + 3 + (i - 5) * 48, rectWidth, 48);
					}
					int show = 2, num = 0;
					if (counter[2] >= 260 * 3 / 3 * 2) {
						show = 1;
						num = players.get(i).stats.getWins();
					} else if (counter[2] > 260 * 3 / 3) {
						show = 0;
						num = players.get(i).stats.getThisStageKilledPlayersAmt();
					} else {
						show = 2;
						num = players.get(i).stats.getRank(players);
					}
					g.drawImage(pictures.selectionButtons.get(14).get(show), rectX + 33 - (int) counter[4] * 20,
							rectY + 1 + (i - 5) * 48, this);
					int width = Tools.getTextWidth(g, "" + num);
					draw.textWithTexturedImage(g, Color.YELLOW, false, rectX + 75 - width / 2 - (int) counter[4] * 20,
							rectY + 37 + (i - 5) * 48, "" + num);
				}
				g.setColor(outline1);
				g.drawRect(rectX - 3 - (int) counter[4] * 20, rectY - 3, rectWidth + 5, rectHeight + 5);
				g.drawRect(rectX - 2 - (int) counter[4] * 20, rectY - 2, rectWidth + 3, rectHeight + 3);
				g.setColor(outline2);
				g.drawRect(rectX - 1 - (int) counter[4] * 20, rectY - 1, rectWidth + 1, rectHeight + 1);
				g.drawRect(rectX - (int) counter[4] * 20, rectY, rectWidth - 1, rectHeight - 1);
				g.setColor(outline3);
				g.drawRect(rectX + 1 - (int) counter[4] * 20, rectY + 1, rectWidth - 3, rectHeight - 3);
				g.drawRect(rectX + 2 - (int) counter[4] * 20, rectY + 2, rectWidth - 5, rectHeight - 5);
				for (int i = 5; i < players.size(); i++) {
					if (!players.get(i).isReset)
						players.get(i).reset();
					players.get(i).character.draw(g, rectX + 3 - (int) counter[4] * 20, rectY - 11 + (i - 5) * 48,
							this);
				}
			}
			g.drawImage(pictures.selectionBackground, 8, 0, this);
			if (counter[4] > 0) {
				g.drawImage(pictures.selectionButtons.get(3).get(4), 39 + (int) counter[4] * 20 - 610, 45, this);
				g.drawImage(pictures.selectionButtons.get(4).get(0), 39 + (int) counter[4] * 20 - 610, 319, this);
				if (network.isPlayingOnline()) {
					boolean toDarken = false;
					for (int i = 0; i < network.connectedClients.size(); i++)
						if (network.connectedClients.get(i)[0].equals(options.multiplayer.username))
							if ((network.weAreHost() && network.connectedClients.get(i)[2].equals("1"))
									|| (!network.weAreHost() && network.connectedClients.get(i)[3].equals("1")))
								toDarken = true;
					BufferedImage pic = pictures.selectionButtons.get(4).get(2);
					if (toDarken)
						pic = Pictures.darken(pic);
					g.drawImage(pic, 39 + (int) counter[4] * 20 - 610, 366, this);
				} else
					g.drawImage(pictures.selectionButtons.get(4).get(1), 39 + (int) counter[4] * 20 - 610, 366, this);
				tickcounter = 75;
			}
		} else if (counter[0] == 2) {
			// Selection (stats, battle on new stage)
			g.drawImage(pictures.selectionBackground, 8, 0, this);
			g.drawImage(pictures.selectionButtons.get(3).get(4), 39, 45, this);
			g.drawImage(pictures.selectionButtons.get(4).get(0), 39, 319, this);
			if (network.isPlayingOnline()) {
				boolean toDarken = false;
				for (int i = 0; i < network.connectedClients.size(); i++)
					if (network.connectedClients.get(i)[0].equals(options.multiplayer.username))
						if ((network.weAreHost() && network.connectedClients.get(i)[2].equals("1"))
								|| (!network.weAreHost() && network.connectedClients.get(i)[3].equals("1")))
							toDarken = true;
				BufferedImage pic = pictures.selectionButtons.get(4).get(2);
				if (toDarken)
					pic = Pictures.darken(pic);
				g.drawImage(pic, 39, 366, this);
			} else
				g.drawImage(pictures.selectionButtons.get(4).get(1), 39, 366, this);
			if (counter[1] == 0) {
				if ((tickcounter >= 25 && tickcounter < 50) || tickcounter >= 75)
					g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 35, 43, this);
				g.drawImage(pictures.selectionSelector.get(0).get((int) counter[3]), 82, 34, this);
			} else if (counter[1] == 1) {
				if ((tickcounter >= 25 && tickcounter < 50) || tickcounter >= 75)
					g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 35, 362, this);
				g.drawImage(pictures.selectionSelector.get(0).get((int) counter[3]), 82, 355, this);
			}
		} else if (counter[0] == 3 || counter[0] == 4 || counter[0] == 5) {
			// Backing out of overall stats page
			g.drawImage(pictures.selectionBackground, 8, 0, this);
			g.drawImage(pictures.selectionButtons.get(3).get(4), 39, 45, this);
			g.drawImage(pictures.selectionButtons.get(4).get(0), 39, 319, this);
			if (network.isPlayingOnline()) {
				boolean toDarken = false;
				for (int i = 0; i < network.connectedClients.size(); i++)
					if (network.connectedClients.get(i)[0].equals(options.multiplayer.username))
						if ((network.weAreHost() && network.connectedClients.get(i)[2].equals("1"))
								|| (!network.weAreHost() && network.connectedClients.get(i)[3].equals("1")))
							toDarken = true;
				BufferedImage pic = pictures.selectionButtons.get(4).get(2);
				if (toDarken)
					pic = Pictures.darken(pic);
				g.drawImage(pic, 39, 366, this);
			} else
				g.drawImage(pictures.selectionButtons.get(4).get(1), 39, 366, this);
			g.drawImage(pictures.selectionButtons.get(20).get(0), 156 + (int) counter[4] * 20, 28, this);
			if (counter[0] == 3 || counter[0] == 4) {
				g.drawImage(Pictures.brighten(pictures.selectionButtons.get(3).get(4)), 39, 45, this);
				g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 35, 43, this);
			}
			ArrayList<Player> copyPlayers = Player.copyList(players);
			ArrayList<Player> players = new ArrayList<Player>(copyPlayers);
			Collections.sort(players, PlayerComparator.getComparator(PlayerComparator.RANK_SORT));
			for (int i = 0; i < players.size(); i++) {
				g.drawImage(pictures.selectionButtons.get(19).get(0), 156 + (int) counter[4] * 20, 60 + 32 * i, this);
				String rank = "" + players.get(i).stats.getRank(players);
				draw.textWithOutline(g, rank, 174 - Tools.getTextWidth(g, rank) / 2 + (int) counter[4] * 20,
						88 + 32 * i, Color.WHITE, Color.BLACK);
				// beginning of unnecessary resizing
				g.drawImage(
						Resizer.resize(pictures.characters.get(players.get(i).character.characterIndex).get(0).get(0),
								132, 37),
						189 + (int) counter[4] * 20, 61 + 32 * i, this);
				g.drawImage(pictures.selectionButtons.get(18).get(1), 316 + (int) counter[4] * 20, 60 + 32 * i, this);
				String winPercent = "" + players.get(i).stats.getWinPercent();
				g.setColor(new Color(80, 48, 80));
				g.drawString(winPercent, 380 - Tools.getTextWidth(g, winPercent) + (int) counter[4] * 20, 88 + 32 * i);
				g.drawImage(pictures.selectionButtons.get(18).get(0), 412 + (int) counter[4] * 20, 60 + 32 * i, this);
				String bPoints = "" + players.get(i).stats.getTotalKilledPlayersAmt();
				g.drawString(bPoints, 500 - Tools.getTextWidth(g, bPoints) + (int) counter[4] * 20, 88 + 32 * i);
				g.drawImage(pictures.selectionButtons.get(18).get(0), 508 + (int) counter[4] * 20, 60 + 32 * i, this);
				String wins = "" + players.get(i).stats.getWins();
				g.drawString(wins, 596 - Tools.getTextWidth(g, wins) + (int) counter[4] * 20, 88 + 32 * i);
			}
			if (counter[0] == 4)
				for (int i = 0; i < players.size(); i++)
					if (counter[1] == i) {
						if ((tickcounter >= 25 && tickcounter < 50) || tickcounter >= 75)
							g.drawImage(pictures.selectionButtonSelector.get(0).get(6), 184, 56 + 32 * i, this);
						g.drawImage(pictures.selectionSelector.get(0).get((int) counter[3]), 240, 48 + 32 * i, this);
					}
		} else if (counter[0] == 6 || counter[0] == 7 || counter[0] == 11) {
			ArrayList<Player> copyPlayers = Player.copyList(players);
			ArrayList<Player> players = new ArrayList<Player>(copyPlayers);
			Collections.sort(players, PlayerComparator.getComparator(PlayerComparator.RANK_SORT));
			int subtractNum = 25;
			g.drawImage(pictures.selectionButtons.get(20).get(0), 156 + (int) counter[4] * 20 - (subtractNum * 20), 28,
					this);
			for (int i = 0; i < players.size(); i++) {
				g.drawImage(pictures.selectionButtons.get(19).get(0), 156 + (int) counter[4] * 20 - (subtractNum * 20),
						60 + 32 * i, this);
				String rank = "" + players.get(i).stats.getRank(players);
				draw.textWithOutline(g, rank,
						174 - Tools.getTextWidth(g, rank) / 2 + (int) counter[4] * 20 - (subtractNum * 20), 88 + 32 * i,
						Color.WHITE, Color.BLACK);
				// beginning of unnecessary resizing
				g.drawImage(
						Resizer.resize(pictures.characters.get(players.get(i).character.characterIndex).get(0).get(0),
								132, 37),
						189 + (int) counter[4] * 20 - (subtractNum * 20), 61 + 32 * i, this);
				g.drawImage(pictures.selectionButtons.get(18).get(1), 316 + (int) counter[4] * 20 - (subtractNum * 20),
						60 + 32 * i, this);
				String winPercent = "" + players.get(i).stats.getWinPercent();
				g.setColor(new Color(80, 48, 80));
				g.drawString(winPercent,
						380 - Tools.getTextWidth(g, winPercent) + (int) counter[4] * 20 - (subtractNum * 20),
						88 + 32 * i);
				g.drawImage(pictures.selectionButtons.get(18).get(0), 412 + (int) counter[4] * 20 - (subtractNum * 20),
						60 + 32 * i, this);
				String bPoints = "" + players.get(i).stats.getTotalKilledPlayersAmt();
				g.drawString(bPoints, 500 - Tools.getTextWidth(g, bPoints) + (int) counter[4] * 20 - (subtractNum * 20),
						88 + 32 * i);
				g.drawImage(pictures.selectionButtons.get(18).get(0), 508 + (int) counter[4] * 20 - (subtractNum * 20),
						60 + 32 * i, this);
				String wins = "" + players.get(i).stats.getWins();
				g.drawString(wins, 596 - Tools.getTextWidth(g, wins) + (int) counter[4] * 20 - (subtractNum * 20),
						88 + 32 * i);
			}

			g.drawImage(pictures.selectionBackground, 8, 0, this);
			g.drawImage(Pictures.brighten(pictures.selectionButtons.get(3).get(4)), 39, 45, this);
			g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 35, 43, this);
			g.drawImage(pictures.selectionButtons.get(4).get(0), 39, 319, this);
			if (network.isPlayingOnline()) {
				boolean toDarken = false;
				for (int i = 0; i < network.connectedClients.size(); i++)
					if (network.connectedClients.get(i)[0].equals(options.multiplayer.username))
						if ((network.weAreHost() && network.connectedClients.get(i)[2].equals("1"))
								|| (!network.weAreHost() && network.connectedClients.get(i)[3].equals("1")))
							toDarken = true;
				BufferedImage pic = pictures.selectionButtons.get(4).get(2);
				if (toDarken)
					pic = Pictures.darken(pic);
				g.drawImage(pic, 39, 366, this);
			} else
				g.drawImage(pictures.selectionButtons.get(4).get(1), 39, 366, this);

			g.drawImage(pictures.endStatsOverall, 162 + (int) counter[4] * 20, 38, this);
			// ArrayList<Player> copyPlayers = Player.copyList(players);
			// ArrayList<Player> players = new ArrayList<Player>(copyPlayers);
			// Collections.sort(players,
			// PlayerComparator.getComparator(PlayerComparator.RANK_SORT));
			int playerIndex = (int) counter[5];
			g.drawImage(pictures.characters.get(players.get(playerIndex).character.characterIndex).get(1).get(8),
					224 + (int) counter[4] * 20, 24, this);
			g.drawImage(pictures.endStatsButtons.get(2).get(players.get(playerIndex).getIndex()),
					215 + (int) counter[4] * 20, 85, this);
			// beginning of unnecessary resizing
			g.drawImage(Resizer.resize(
					pictures.characters.get(players.get(playerIndex).character.characterIndex).get(0).get(0), 132, 37),
					174 + (int) counter[4] * 20, 114, this);
			String text = "" + players.get(playerIndex).stats.getTotalBattles();
			draw.textWithOutline(g, text, 240 - Tools.getTextWidth(g, text) / 2 + (int) counter[4] * 20, 211,
					new Color(248, 224, 16), new Color(48, 32, 32));
			text = "" + players.get(playerIndex).stats.getWins();
			draw.textWithOutline(g, text, 220 - Tools.getTextWidth(g, text) / 2 + (int) counter[4] * 20, 243,
					new Color(248, 224, 16), new Color(48, 32, 32));
			text = "" + players.get(playerIndex).stats.getLosses();
			draw.textWithOutline(g, text, 220 - Tools.getTextWidth(g, text) / 2 + (int) counter[4] * 20, 275,
					new Color(248, 224, 16), new Color(48, 32, 32));
			text = "" + players.get(playerIndex).stats.getDraws();
			draw.textWithOutline(g, text, 220 - Tools.getTextWidth(g, text) / 2 + (int) counter[4] * 20, 307,
					new Color(248, 224, 16), new Color(48, 32, 32));
			text = "" + players.get(playerIndex).stats.getWinPercent();
			draw.textWithOutline(g, text, 278 - Tools.getTextWidth(g, text) + (int) counter[4] * 20, 339,
					new Color(248, 224, 16), new Color(48, 32, 32));
			text = "" + players.get(playerIndex).stats.getTotalKilledPlayersAmt();
			draw.textWithOutline(g, text, 284 - Tools.getTextWidth(g, text) / 2 + (int) counter[4] * 20, 371,
					new Color(248, 224, 16), new Color(48, 32, 32));
			text = "" + players.get(playerIndex).stats.getRank(players);
			draw.textWithOutline(g, text, 284 - Tools.getTextWidth(g, text) / 2 + (int) counter[4] * 20, 403,
					new Color(248, 224, 16), new Color(48, 32, 32));
			g.drawImage(pictures.endStatsBg, 335 + (int) counter[4] * 20, 31, this);
			g.drawImage(pictures.endStatsButtons.get(0).get(0), 384 + (int) counter[4] * 20, 31, this);
			int x = 351 + (int) counter[4] * 20, y = 77;
			if (players.size() < 6)
				x = 416 + (int) counter[4] * 20;
			for (int i = 0; i < players.size(); i++) {
				if (i == counter[5])
					continue;
				g.drawImage(pictures.endStatsButtons.get(1).get(0), x, y, this);
				BufferedImage headImage = pictures.characters.get(players.get(i).character.characterIndex).get(5)
						.get(0);
				g.drawImage(headImage, x + 1, y + 3, this);
				String kills = ""
						+ players.get((int) counter[5]).stats.getTotalKilledPlayerAmt(players.get(i).getIndex());
				draw.textWithOutline(g, kills, x + 62 - Tools.getTextWidth(g, kills) / 2, y + 30,
						new Color(248, 224, 16), new Color(48, 32, 32));
				y += 48;
				if (x == 351 + (int) counter[4] * 20 && i > 4) {
					y = 77;
					x += 129;
				}
			}
			BufferedImage imageToDraw = pictures.selectionButtons.get(21).get(0);
			if (tickcounter >= 90)
				imageToDraw = Pictures.brighten(imageToDraw);
			if (counter[0] == 7)
				g.drawImage(imageToDraw, 572, 376, this);
		} else if (counter[0] == 8 || counter[0] == 10) {
			ArrayList<Player> copyPlayers = Player.copyList(players);
			ArrayList<Player> players = new ArrayList<Player>(copyPlayers);
			Collections.sort(players, PlayerComparator.getComparator(PlayerComparator.RANK_SORT));
			g.drawImage(pictures.selectionBackground, 8, 0, this);
			g.drawImage(Pictures.brighten(pictures.selectionButtons.get(3).get(4)), 39, 45, this);
			g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 35, 43, this);
			g.drawImage(pictures.selectionButtons.get(4).get(0), 39, 319, this);
			if (network.isPlayingOnline()) {
				boolean toDarken = false;
				for (int i = 0; i < network.connectedClients.size(); i++)
					if (network.connectedClients.get(i)[0].equals(options.multiplayer.username))
						if ((network.weAreHost() && network.connectedClients.get(i)[2].equals("1"))
								|| (!network.weAreHost() && network.connectedClients.get(i)[3].equals("1")))
							toDarken = true;
				BufferedImage pic = pictures.selectionButtons.get(4).get(2);
				if (toDarken)
					pic = Pictures.darken(pic);
				g.drawImage(pic, 39, 366, this);
			} else
				g.drawImage(pictures.selectionButtons.get(4).get(1), 39, 366, this);

			g.drawImage(pictures.endStatsOverall, 162, 38, this);
			int playerIndex = (int) counter[5];
			g.drawImage(pictures.characters.get(players.get(playerIndex).character.characterIndex).get(1).get(8), 224,
					24, this);
			g.drawImage(pictures.endStatsButtons.get(2).get(players.get(playerIndex).getIndex()), 215, 85, this);
			// beginning of unnecessary resizing
			g.drawImage(Resizer.resize(
					pictures.characters.get(players.get(playerIndex).character.characterIndex).get(0).get(0), 132, 37),
					174, 114, this);
			String text = "" + players.get(playerIndex).stats.getTotalBattles();
			draw.textWithOutline(g, text, 240 - Tools.getTextWidth(g, text) / 2, 211, new Color(248, 224, 16),
					new Color(48, 32, 32));
			text = "" + players.get(playerIndex).stats.getWins();
			draw.textWithOutline(g, text, 220 - Tools.getTextWidth(g, text) / 2, 243, new Color(248, 224, 16),
					new Color(48, 32, 32));
			text = "" + players.get(playerIndex).stats.getLosses();
			draw.textWithOutline(g, text, 220 - Tools.getTextWidth(g, text) / 2, 275, new Color(248, 224, 16),
					new Color(48, 32, 32));
			text = "" + players.get(playerIndex).stats.getDraws();
			draw.textWithOutline(g, text, 220 - Tools.getTextWidth(g, text) / 2, 307, new Color(248, 224, 16),
					new Color(48, 32, 32));
			text = "" + players.get(playerIndex).stats.getWinPercent();
			draw.textWithOutline(g, text, 278 - Tools.getTextWidth(g, text), 339, new Color(248, 224, 16),
					new Color(48, 32, 32));
			text = "" + players.get(playerIndex).stats.getTotalKilledPlayersAmt();
			draw.textWithOutline(g, text, 284 - Tools.getTextWidth(g, text) / 2, 371, new Color(248, 224, 16),
					new Color(48, 32, 32));
			text = "" + players.get(playerIndex).stats.getRank(players);
			draw.textWithOutline(g, text, 284 - Tools.getTextWidth(g, text) / 2, 403, new Color(248, 224, 16),
					new Color(48, 32, 32));
			g.drawImage(pictures.endStatsBg, 335, 31, this);
			g.drawImage(pictures.endStatsButtons.get(0).get(0), 384, 31 - (int) counter[4] * 20, this);
			int x = 351, y = 77 + (int) counter[4] * 20 - 25 * 20;
			if (players.size() < 6)
				x = 416;
			for (int i = 0; i < players.size(); i++) {
				if (i == counter[5])
					continue;
				g.drawImage(pictures.endStatsButtons.get(1).get(0), x, y, this);
				BufferedImage headImage = pictures.characters.get(players.get(i).character.characterIndex).get(5)
						.get(0);
				g.drawImage(headImage, x + 1, y + 3, this);
				String kills = ""
						+ players.get((int) counter[5]).stats.getTotalKilledPlayerAmt(players.get(i).getIndex());
				draw.textWithOutline(g, kills, x + 62 - Tools.getTextWidth(g, kills) / 2, y + 30,
						new Color(248, 224, 16), new Color(48, 32, 32));
				y += 48;
				if (x == 351 && i > 4) {
					y = 77 + (int) counter[4] * 20 - 25 * 20;
					x += 129;
				}
			}
			g.drawImage(pictures.endStatsButtons.get(0).get(1), 384, 31 + (int) counter[4] * 20, this);
			x = 351;
			y = 77 + (int) counter[4] * 20;
			if (players.size() < 6)
				x = 416;
			for (int i = 0; i < players.size() + 1; i++) {
				if (i == counter[5])
					continue;
				else if (i == players.size()) {
					g.drawImage(pictures.endStatsButtons.get(1).get(0), x, y, this);
					String envDeaths = "" + players.get((int) counter[5]).stats.getEnvironmentalDeaths();
					draw.textWithOutline(g, envDeaths, x + 62 - Tools.getTextWidth(g, envDeaths) / 2, y + 30,
							new Color(248, 224, 16), new Color(48, 32, 32));
					g.drawImage(pictures.endStatsSuicide, x + 1, y + 3, this);
				} else {
					g.drawImage(pictures.endStatsButtons.get(1).get(0), x, y, this);
					BufferedImage headImage = pictures.characters.get(players.get(i).character.characterIndex).get(5)
							.get(1);
					g.drawImage(headImage, x + 1, y + 3, this);
					String kills = ""
							+ players.get(i).stats.getTotalKilledPlayerAmt(players.get((int) counter[5]).getIndex());
					draw.textWithOutline(g, kills, x + 62 - Tools.getTextWidth(g, kills) / 2, y + 30,
							new Color(248, 224, 16), new Color(48, 32, 32));
					y += 48;
					if (x == 351 && i > 4) {
						y = 77 + (int) counter[4] * 20;
						x += 129;
					}
				}
			}
			g.drawImage(pictures.endStatsButtons.get(0).get(2), 407, 317 + (int) counter[4] * 20, this);
			g.drawImage(pictures.endStatsButtons.get(1).get(0), 416, 364 + (int) counter[4] * 20, this);
			g.drawImage(pictures.characters.get(players.get((int) counter[5]).character.characterIndex).get(5).get(0),
					417, 367 + (int) counter[4] * 20, this);
			String suicides = "" + players.get((int) counter[5]).stats.getSuicideAmt();
			draw.textWithOutline(g, suicides, 478 - Tools.getTextWidth(g, suicides) / 2, 394 + (int) counter[4] * 20,
					new Color(248, 224, 16), new Color(48, 32, 32));
			BufferedImage imageToDraw = pictures.selectionButtons.get(21).get(0);
			if (tickcounter >= 90)
				imageToDraw = Pictures.brighten(imageToDraw);
			if (counter[0] == 9)
				g.drawImage(imageToDraw, 572, 71, imageToDraw.getWidth(), -imageToDraw.getHeight(), this);
		} else if (counter[0] == 9) {
			ArrayList<Player> copyPlayers = Player.copyList(players);
			ArrayList<Player> players = new ArrayList<Player>(copyPlayers);
			Collections.sort(players, PlayerComparator.getComparator(PlayerComparator.RANK_SORT));
			g.drawImage(pictures.selectionBackground, 8, 0, this);
			g.drawImage(Pictures.brighten(pictures.selectionButtons.get(3).get(4)), 39, 45, this);
			g.drawImage(pictures.selectionButtonSelector.get(0).get(0), 35, 43, this);
			g.drawImage(pictures.selectionButtons.get(4).get(0), 39, 319, this);
			if (network.isPlayingOnline()) {
				boolean toDarken = false;
				for (int i = 0; i < network.connectedClients.size(); i++)
					if (network.connectedClients.get(i)[0].equals(options.multiplayer.username))
						if ((network.weAreHost() && network.connectedClients.get(i)[2].equals("1"))
								|| (!network.weAreHost() && network.connectedClients.get(i)[3].equals("1")))
							toDarken = true;
				BufferedImage pic = pictures.selectionButtons.get(4).get(2);
				if (toDarken)
					pic = Pictures.darken(pic);
				g.drawImage(pic, 39, 366, this);
			} else
				g.drawImage(pictures.selectionButtons.get(4).get(1), 39, 366, this);

			g.drawImage(pictures.endStatsOverall, 162, 38, this);
			int playerIndex = (int) counter[5];
			g.drawImage(pictures.characters.get(players.get(playerIndex).character.characterIndex).get(1).get(8), 224,
					24, this);
			g.drawImage(pictures.endStatsButtons.get(2).get(players.get(playerIndex).getIndex()), 215, 85, this);
			// beginning of unnecessary resizing
			g.drawImage(Resizer.resize(
					pictures.characters.get(players.get(playerIndex).character.characterIndex).get(0).get(0), 132, 37),
					174, 114, this);
			String text = "" + players.get(playerIndex).stats.getTotalBattles();
			draw.textWithOutline(g, text, 240 - Tools.getTextWidth(g, text) / 2, 211, new Color(248, 224, 16),
					new Color(48, 32, 32));
			text = "" + players.get(playerIndex).stats.getWins();
			draw.textWithOutline(g, text, 220 - Tools.getTextWidth(g, text) / 2, 243, new Color(248, 224, 16),
					new Color(48, 32, 32));
			text = "" + players.get(playerIndex).stats.getLosses();
			draw.textWithOutline(g, text, 220 - Tools.getTextWidth(g, text) / 2, 275, new Color(248, 224, 16),
					new Color(48, 32, 32));
			text = "" + players.get(playerIndex).stats.getDraws();
			draw.textWithOutline(g, text, 220 - Tools.getTextWidth(g, text) / 2, 307, new Color(248, 224, 16),
					new Color(48, 32, 32));
			text = "" + players.get(playerIndex).stats.getWinPercent();
			draw.textWithOutline(g, text, 278 - Tools.getTextWidth(g, text), 339, new Color(248, 224, 16),
					new Color(48, 32, 32));
			text = "" + players.get(playerIndex).stats.getTotalKilledPlayersAmt();
			draw.textWithOutline(g, text, 284 - Tools.getTextWidth(g, text) / 2, 371, new Color(248, 224, 16),
					new Color(48, 32, 32));
			text = "" + players.get(playerIndex).stats.getRank(players);
			draw.textWithOutline(g, text, 284 - Tools.getTextWidth(g, text) / 2, 403, new Color(248, 224, 16),
					new Color(48, 32, 32));
			g.drawImage(pictures.endStatsBg, 335, 31, this);
			g.drawImage(pictures.endStatsButtons.get(0).get(1), 384, 31, this);
			int x = 351, y = 77;
			if (players.size() < 6)
				x = 416;
			for (int i = 0; i < players.size() + 1; i++) {
				if (i == counter[5])
					continue;
				else if (i == players.size()) {
					g.drawImage(pictures.endStatsButtons.get(1).get(0), x, y, this);
					String envDeaths = "" + players.get((int) counter[5]).stats.getEnvironmentalDeaths();
					draw.textWithOutline(g, envDeaths, x + 62 - Tools.getTextWidth(g, envDeaths) / 2, y + 30,
							new Color(248, 224, 16), new Color(48, 32, 32));
					g.drawImage(pictures.endStatsSuicide, x + 1, y + 3, this);
				} else {
					g.drawImage(pictures.endStatsButtons.get(1).get(0), x, y, this);
					BufferedImage headImage = pictures.characters.get(players.get(i).character.characterIndex).get(5)
							.get(1);
					g.drawImage(headImage, x + 1, y + 3, this);
					String kills = ""
							+ players.get(i).stats.getTotalKilledPlayerAmt(players.get((int) counter[5]).getIndex());
					draw.textWithOutline(g, kills, x + 62 - Tools.getTextWidth(g, kills) / 2, y + 30,
							new Color(248, 224, 16), new Color(48, 32, 32));
					y += 48;
					if (x == 351 && i > 4) {
						y = 77;
						x += 129;
					}
				}
			}
			g.drawImage(pictures.endStatsButtons.get(0).get(2), 407, 317, this);
			g.drawImage(pictures.endStatsButtons.get(1).get(0), 416, 364, this);
			g.drawImage(pictures.characters.get(players.get((int) counter[5]).character.characterIndex).get(5).get(0),
					417, 367, this);
			String suicides = "" + players.get((int) counter[5]).stats.getSuicideAmt();
			draw.textWithOutline(g, suicides, 478 - Tools.getTextWidth(g, suicides) / 2, 394, new Color(248, 224, 16),
					new Color(48, 32, 32));
			BufferedImage imageToDraw = pictures.selectionButtons.get(21).get(0);
			if (tickcounter >= 90)
				imageToDraw = Pictures.brighten(imageToDraw);
			if (counter[0] == 9)
				g.drawImage(imageToDraw, 572, 71, imageToDraw.getWidth(), -imageToDraw.getHeight(), this);
		}
		g.drawImage(pictures.selectionBackground2, -2, -2, this);
		if (fadecounter != -2 && opacity == 0)
			opacity = 1;
		// tickFade(g);
	}

	public void tickEndOfMatch(int keyCode) {
		if (counter[0] == 0) {
			if (temp[1].contains("| Song Started")
					&& (keyCode == options.controllers[0].AButton || keyCode == options.controllers[0].CButton)) {
				setPause(Pause.PAINT);
				sound.play("Select");
				counter[0] = 1;
				counter[4] = 0;
				setPause(Pause.NONE);
			}
		} else if (counter[0] == 2) {
			if (keyCode == options.controllers[0].AButton || keyCode == options.controllers[0].CButton) {
				if (counter[1] == 0) {
					// Results
					setPause(Pause.PAINT);
					sound.play("Select");
					counter[0] = 3;
					counter[4] = 25;
					tickcounter = 0;
					setPause(Pause.NONE);
				} else if (counter[1] == 1) {
					if (network.isPlayingOnline()) {
						boolean weAreReady = false;
						for (int i = 0; i < network.connectedClients.size(); i++)
							if (network.connectedClients.get(i)[0].equals(options.multiplayer.username))
								if ((network.weAreHost() && network.connectedClients.get(i)[2].equals("1"))
										|| (!network.weAreHost() && network.connectedClients.get(i)[3].equals("1")))
									weAreReady = true;
						if (!weAreReady) {
							sound.play("Select");
							if (network.weAreHost()) {
								boolean everyoneIsReady = true;
								for (int i = 0; i < network.connectedClients.size(); i++) {
									if (network.connectedClients.get(i)[0].equals(options.multiplayer.username))
										network.connectedClients.set(i,
												new String[] { network.connectedClients.get(i)[0],
														network.connectedClients.get(i)[1], "1" });
									else if (network.connectedClients.get(i)[2].equals("0"))
										everyoneIsReady = false;
								}
								if (everyoneIsReady) {
									network.server.sendMessageToAllClients("SERVER:SELECTMATCH");
									setPause(Pause.PAINT);
									sound.play("Select");
									state.set(StateEnum.MATCH_SELECTION, this);
									counter[0] = 16;
									counter[1] = 0;
									counter[4] = 0;
									counter[5] = 0;
									counter[6] = 1;
									setPause(Pause.NONE);
								}
							} else {
								network.client.sendMessageToServer("CLIENT:READY");
							}
						}
					} else {
						setPause(Pause.PAINT);
						sound.play("Select");
						state.set(StateEnum.MATCH_SELECTION, this);
						counter[0] = 16;
						counter[1] = 0;
						counter[4] = 0;
						counter[5] = 0;
						counter[6] = 1;
						setPause(Pause.NONE);
					}
				}
			} else if (keyCode == options.controllers[0].upButton || keyCode == options.controllers[0].downButton) {
				long tempCounter = counter[1];
				tempCounter = upAndDownMenuResult(counter[1], keyCode, 2, true, false);
				if (tempCounter != counter[1]) {
					sound.play("Tick");
					counter[1] = tempCounter;
					tickcounter = 75;
				}
			}
		} else if (counter[0] == 4) {
			if (keyCode == options.controllers[0].AButton || keyCode == options.controllers[0].CButton) {
				setPause(Pause.PAINT);
				sound.play("Select");
				counter[4] = 25;
				counter[5] = counter[1];
				counter[1] = 0;
				counter[0] = 6;
				setPause(Pause.NONE);
			} else if (keyCode == options.controllers[0].upButton || keyCode == options.controllers[0].downButton) {
				counter[6] = keyCode;
			} else if (keyCode == options.controllers[0].BButton) {
				setPause(Pause.PAINT);
				sound.play("Back");
				tickcounter = 0;
				counter[1] = 0;
				counter[4] = 0;
				counter[0] = 5;
				setPause(Pause.NONE);
			}
		} else if (counter[0] == 7) {
			if (keyCode == options.controllers[0].BButton) {
				setPause(Pause.PAINT);
				sound.play("Back");
				counter[4] = 0;
				counter[0] = 11;
				setPause(Pause.NONE);
			} else if (keyCode == options.controllers[0].downButton) {
				setPause(Pause.PAINT);
				sound.play("Tick");
				counter[4] = 25;
				counter[1] = 0;
				counter[0] = 8;
				setPause(Pause.NONE);
			}
		} else if (counter[0] == 9) {
			if (keyCode == options.controllers[0].upButton || keyCode == options.controllers[0].BButton) {
				setPause(Pause.PAINT);
				if (keyCode == options.controllers[0].BButton)
					sound.play("Back");
				else
					sound.play("Tick");
				counter[4] = 0;
				counter[1] = 0;
				counter[0] = 10;
				setPause(Pause.NONE);
			}
		}
	}

	private void fadeIn() {
		fadecounterenabled = 1;
		if (fadecounter == 0)
			fadecounter = HIGHEST_FADE_COUNTER;
		if (fadecounter >= LOWEST_FADE_COUNTER) {
			// Common.log(this,"Fading in.");
			if (fadecounter > HIGHEST_FADE_COUNTER)
				fadecounter = HIGHEST_FADE_COUNTER;
			float percentDone = ((float) HIGHEST_FADE_COUNTER - (float) fadecounter) / (float) HIGHEST_FADE_COUNTER;
			if (percentDone > 1)
				percentDone = 1;
			if (percentDone < 0)
				percentDone = 0;
			opacity = 1f - percentDone;
			fadecounter -= 2;
			if (fadecounter <= 0)
				fadecounter = -2;
		} else {
			// Already done fading in
			fadecounter = -2;
		}
	}

	public void tickFadeOut(Graphics g) {
		g.drawImage(state.getImageToFade(), 0, 0, state.getImageToFade().getWidth(this),
				state.getImageToFade().getHeight(this), this);
	}

	public void tickFadeOut() {
		fadeOut();
		if (fadecounter == HIGHEST_FADE_COUNTER + 1 || fadecounter == -2) {
			tickcounter = 0;
			fadecounter = 0;
			fadecounterenabled = 0;
			state.set(state.getNextStateAfterFade(), this);
		}
	}

	private void fadeOut() {
		fadecounterenabled = 1;
		if (fadecounter == -1)
			fadecounter = LOWEST_FADE_COUNTER;
		if (fadecounter < HIGHEST_FADE_COUNTER) {
			// Common.log(this,"Fading out.");
			if (fadecounter < LOWEST_FADE_COUNTER)
				fadecounter = LOWEST_FADE_COUNTER;
			float percentDone = ((float) LOWEST_FADE_COUNTER + (float) fadecounter) / (float) HIGHEST_FADE_COUNTER;
			// Common.log(this,"counter[2]: " + counter[2]);
			// Common.log(this,"percentDone: " + percentDone);
			if (percentDone > 1)
				percentDone = 1;
			if (percentDone < 0)
				percentDone = 0;
			opacity = percentDone;
			// Common.log(this,"opacity: " + opacity);
			fadecounter += 2;
		} else {
			// Already done fading out
			fadecounter = HIGHEST_FADE_COUNTER + 1;
		}
	}

	public void firstLoad() {
		super.firstLoad();
		// System.setProperty("sun.java2d.d3d", "true");
		pictures = new Pictures(this);

		renderingHints.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		draw.setPictures(pictures);
		music = new MusicHandler(logger);
		sound = new SoundHandler(logger);
		// music.setVolume(.55f);
		// sound.setVolume(.45f);
		music.setVolume(.65f);
		sound.setVolume(.55f);// TODO: Set proper volume?
		// music.setVolume(.0f);
		// sound.setVolume(.0f);
		options = new Options();
		players = new ArrayList<Player>();
		// joystickHandler = new JoystickHandler(new Logger(true));
		joystickHandler = new JoystickHandler(new Logger(false));
		joystickHandler.setJoystickListener(new BombermanJoystickListener(this));
		joystickHandler.start();
		try {
			InputStream is = Tools.getResourceAsStream("res/font/gomarice_goma_cookie.ttf");
			font = Font.createFont(Font.TRUETYPE_FONT, is);
			is.close();
			is = null;
		} catch (Exception e) {
			e.printStackTrace();
			logger.err(LogLevel.ERROR, "Fatal error in loading custom font.");
			System.exit(0);
		}
	}

	/**
	 * 
	 * @param counter
	 * @param keyCode
	 * @param optionsLength
	 * @param loopEdges
	 * @return
	 */
	private long upAndDownMenuResult(long counter, int keyCode, int optionsLength, boolean loopEdges,
			boolean tickSound) {
		long tempCounter = counter;
		if (keyCode == options.controllers[0].upButton)
			tempCounter--;
		else if (keyCode == options.controllers[0].downButton)
			tempCounter++;
		if (tempCounter < 0)
			if (!loopEdges)
				tempCounter = 0;
			else
				tempCounter = optionsLength - 1;
		else if (tempCounter > optionsLength - 1)
			if (!loopEdges)
				tempCounter = optionsLength - 1;
			else
				tempCounter = 0;
		if (tickSound && counter != tempCounter)
			sound.play("Tick");
		return tempCounter;
	}

	private void setOtherButton(int code, int lastCode, int otherButtonWithThisCode) {
		if (code == lastCode)
			return;
		String buttonName = null;
		if (otherButtonWithThisCode == 0) {
			options.controllers[0].upButton = lastCode;
			buttonName = "Up";
		} else if (otherButtonWithThisCode == 1) {
			options.controllers[0].downButton = lastCode;
			buttonName = "Down";
		} else if (otherButtonWithThisCode == 2) {
			options.controllers[0].leftButton = lastCode;
			buttonName = "Left";
		} else if (otherButtonWithThisCode == 3) {
			options.controllers[0].rightButton = lastCode;
			buttonName = "Right";
		} else if (otherButtonWithThisCode == 4) {
			options.controllers[0].AButton = lastCode;
			buttonName = "A";
		} else if (otherButtonWithThisCode == 5) {
			options.controllers[0].BButton = lastCode;
			buttonName = "B";
		} else if (otherButtonWithThisCode == 6) {
			options.controllers[0].CButton = lastCode;
			buttonName = "C";
		} else if (otherButtonWithThisCode == 7) {
			options.controllers[0].LButton = lastCode;
			buttonName = "L";
		} else if (otherButtonWithThisCode == 8) {
			options.controllers[0].RButton = lastCode;
			buttonName = "R";
		} else if (otherButtonWithThisCode == 9) {
			chatButton = lastCode;
			buttonName = "Chat";
		} else if (otherButtonWithThisCode == 10) {
			options.controllers[1].upButton = lastCode;
			buttonName = "Up";
		} else if (otherButtonWithThisCode == 11) {
			options.controllers[1].downButton = lastCode;
			buttonName = "Down";
		} else if (otherButtonWithThisCode == 12) {
			options.controllers[1].leftButton = lastCode;
			buttonName = "Left";
		} else if (otherButtonWithThisCode == 13) {
			options.controllers[1].rightButton = lastCode;
			buttonName = "Right";
		} else if (otherButtonWithThisCode == 14) {
			options.controllers[1].AButton = lastCode;
			buttonName = "A";
		} else if (otherButtonWithThisCode == 15) {
			options.controllers[1].BButton = lastCode;
			buttonName = "B";
		} else if (otherButtonWithThisCode == 16) {
			options.controllers[1].CButton = lastCode;
			buttonName = "C";
		} else if (otherButtonWithThisCode == 17) {
			options.controllers[1].LButton = lastCode;
			buttonName = "L";
		} else if (otherButtonWithThisCode == 18) {
			options.controllers[1].RButton = lastCode;
			buttonName = "R";
		}
		if (buttonName != null)
			logger.log(LogLevel.WARNING,
					"Controller #" + ((otherButtonWithThisCode > 9) ? "2" : "1") + " " + buttonName
							+ " is already using \"" + KeyEvent.getKeyText(code) + "\". Switching it to \""
							+ KeyEvent.getKeyText(lastCode) + "\".");
	}

	public void gameTick() {
		// if (!logger.allLevelsShownEqual(true))
		// logger = new Logger(true, true, true, true);
		if (!logger.levelsShownEqual(false, true, true, true))
			logger = new Logger(false, true, true, true);
		super.gameTick();
		if (joystickHandler != null)
			joystickHandler.setPollTime(10000);
		if (state.is(StateEnum.LOADING))
			tickLoading();
		else if (state.is(StateEnum.DEBUG))
			tickDebug();
		else if (state.is(StateEnum.FADE_OUT))
			tickFadeOut();
		else if (state.is(StateEnum.MAIN_MENU))
			tickMainMenu();
		else if (state.is(StateEnum.NETWORKING))
			tickNetworking();
		else if (state.is(StateEnum.CONTROLS))
			tickControls();
		else if (state.is(StateEnum.MATCH_SELECTION))
			tickMatchSelection();
		else if (state.is(StateEnum.STAGE))
			tickStage();
		else if (state.is(StateEnum.GOING_TO_STAGE))
			tickGoingToStage();
		else if (state.is(StateEnum.END_OF_MATCH))
			tickEndOfMatch();
		if (network != null)
			network.tick();
	}

	public void paintTick(Graphics2D g) {
		super.paintTick(g);
		float derived = 1f;
		if (font != null)
			font = font.deriveFont(derived += 25.5f);
		if (defaultFont == null) {
			defaultFont = new Font(g.getFont().getName(), g.getFont().getStyle(), g.getFont().getSize());
			logger.log(LogLevel.DEBUG, "Set default font to " + g.getFont().getName());
		} else {
			g.setFont(font);
			// logger.log(LogLevel.DEBUG, "Set our font!");
		}
		g.setRenderingHints(renderingHints);
		// g.setRenderingHints(rh2);
		if (state.is(StateEnum.LOADING))
			tickLoading(g);
		else if (state.is(StateEnum.DEBUG))
			tickDebug(g);
		else if (state.is(StateEnum.FADE_OUT))
			tickFadeOut(g);
		else if (state.is(StateEnum.MAIN_MENU))
			tickMainMenu(g);
		else if (state.is(StateEnum.NETWORKING))
			tickNetworking(g);
		else if (state.is(StateEnum.CONTROLS))
			tickControls(g);
		else if (state.is(StateEnum.MATCH_SELECTION))
			tickMatchSelection(g);
		else if (state.is(StateEnum.STAGE))
			tickStage(g);
		else if (state.is(StateEnum.GOING_TO_STAGE))
			tickGoingToStage(g);
		else if (state.is(StateEnum.END_OF_MATCH))
			tickEndOfMatch(g);
		if (fadecounterenabled == 1)
			draw.blackRectangle(g, opacity);
		if (loading && fadecounter == -2) {
			Color bgColor = Color.WHITE;
			Color grayColor = Color.BLACK;
			g.setColor(bgColor);
			Stroke oldStroke = g.getStroke();
			g.setStroke(new BasicStroke(10));
			double maxI = 360;
			for (int i = 0; i < maxI; i++) {
				Color thisColor = new Color(grayColor.getRed()
						+ (int) (((double) bgColor.getRed() - (double) grayColor.getRed()) * ((double) i / maxI)),
						grayColor.getGreen() + (int) (((double) bgColor.getGreen() - (double) grayColor.getGreen())
								* ((double) i / maxI)),
						grayColor.getBlue() + (int) (((double) bgColor.getBlue() - (double) grayColor.getBlue())
								* ((double) i / maxI)));
				g.setColor(thisColor);
				g.draw(new Arc2D.Double(getWidth() - 75, 25, 50, 50, Tools.getRotation(5, false) + i, 1, Arc2D.OPEN));
			}
			g.setStroke(oldStroke);
		}
		if (network != null && pictures.initialized && network.isPlayingOnline())
			network.tick(g);
		if (debug) {
			if (state.equals(StateEnum.STAGE)) {
				for (int i = 0; i < players.size(); i++) {
					if (players.get(i).deadTimer != -1)
						continue;
					java.awt.geom.Area area = stage.getBoundings(this, players.get(i).getCellPoint(),
							players.get(i).getMiddleOfBodyPoint(), players.get(i));
					g.setColor(new Color(255, 0, 0, 155));
					g.fill(area);
				}
				stage.drawCollisionMap(g, this);
			}

			String temps = "[" + temp[0] + "]";
			for (int i = 1; i < temp.length; i++)
				temps += ", [" + temp[i] + "]";
			String counters = "" + counter[0];
			for (int i = 1; i < counter.length; i++)
				counters += ", " + counter[i];
			String[] characters = new String[8];
			for (int i = 0; i < characters.length; i++) {
				characters[i] = "Character[" + i + "]: None";
				if (players.size() > i && players.get(i) != null && players.get(i).character != null) {
					characters[i] = "Character [" + i + "]: " + ((options.players[i] == 2) ? "CPU" : "Human")
							+ ". Animation: " + players.get(i).character.animation + ". ("
							+ players.get(i).getCellPoint().x + "," + players.get(i).getCellPoint().y + ")";
					if (players.get(i).aPressed)
						characters[i] += "A ";
					if (players.get(i).bPressed)
						characters[i] += "B ";
					if (players.get(i).cPressed)
						characters[i] += "C ";
					if (players.get(i).upPressed)
						characters[i] += "UP ";
					if (players.get(i).downPressed)
						characters[i] += "DOWN ";
					if (players.get(i).leftPressed)
						characters[i] += "LEFT ";
					if (players.get(i).rightPressed)
						characters[i] += "RIGHT ";
					if (players.get(i).lPressed)
						characters[i] += "L ";
					if (players.get(i).rPressed)
						characters[i] += "R ";
				}
			}
			ArrayList<String> debug = new ArrayList<String>();
			debug.add(state.getName());
			debug.add("fps " + getFps() + " ups " + getUps());
			if (resizer != null)
				debug.add("Resizer: " + resizer.getWidth() + "," + resizer.getHeight());
			debug.add("Mouse: " + mouseLocation.x + "," + mouseLocation.y);
			debug.add("Random seed: " + seed + ", count: " + randomCount);
			debug.add("tickcounter: " + tickcounter);
			debug.add("fadecounterenabled: " + fadecounterenabled + ", fadecounter: " + fadecounter);
			debug.add("temp: " + temps);
			debug.add("counter: " + counters);
			// for (int i = 0; i < characters.length; i++)
			// if (!characters[i].contains("None"))
			// debug.add(characters[i]);
			if (network != null && network.isPlayingOnline())
				debug.add(
						"networking: " + network.inputTurn + ", " + network.myInputTurnP1 + ", " + network.myInputTurnP2
								+ ", " + network.getRandomCursor() + ", " + network.getTotalPlayersAmt());
			String[] theDebug = new String[debug.size()];
			for (int i = 0; i < debug.size(); i++)
				theDebug[i] = debug.get(i);
			draw.debug(g, defaultFont, theDebug);
		}
		if (players != null && debug) // TODO make look better
			for (int i = 0; i < players.size(); i++)
				if (players.get(i).ai != null)
					players.get(i).ai.drawReachableLocations(g);
	}

	@Override
	public void mouseMoved(int x, int y, int button) {
		mouseMoved(x, y);
		mouseLocation = new Point(x, y);
	}

	@Override
	public void mouseDragged(int x, int y, int button, int originX, int originY) {
		if (mousePressed) {
			if (network != null && pictures.initialized && (new Rectangle(network.graphic.x, network.graphic.y, 15, 15)
					.contains(new Point(originX, originY)) || network.graphic.isMoving)) {
				network.graphic.move(x, y);
				return;
			}
			mouseControl(x, y);
		}
	}

	@Override
	public void mousePressed(int x, int y, int button) {
		if (button == 1) {
			mousePressed = true;
			if (network != null && pictures.initialized
					&& (new Rectangle(network.graphic.x + network.graphic.WIDTH - 15, network.graphic.y, 15, 15)
							.contains(new Point(x, y)) || network.graphic.isMoving))
				if (network.graphic.open() || network.graphic.close())
					return;
			mouseControl(x, y);
		}
	}

	public void mouseMoved(int x, int y) {
		if (state.is(StateEnum.MAIN_MENU)) {
			if (counter[7] == 0) {
				if (!pictures.initialized)
					return;
				Rectangle pressEnter = new Rectangle(200, 270, 235, 30);
				if (pressEnter.contains(new Point(x, y)) && !pressEnter.contains(mouseLocation)) {
					// If we only just moved into the location
					tickcounter = 90;
					sound.play("Tick");
				}
			} else if (counter[7] == 1) {
				Rectangle quickPlay = new Rectangle(220, 245, 200, 30);
				if (quickPlay.contains(new Point(x, y)) && !quickPlay.contains(mouseLocation)) {
					// If we only just moved into the location
					counter[1] = 0;
					tickcounter = 90;
					sound.play("Tick");
				}
			}
		}
	}

	public void mouseControl(int x, int y) {
		if (state.is(StateEnum.MAIN_MENU)) {
			if (counter[7] == 0) {
				Rectangle pressEnter = new Rectangle(200, 270, 235, 30);
				if (pressEnter.contains(new Point(x, y))) {
					tickMainMenu(options.controllers[0].CButton);
				}
			} else if (counter[7] == 1) {
				Rectangle quickPlay = new Rectangle(220, 245, 200, 30);
				if (quickPlay.contains(new Point(x, y))) {
					counter[1] = 0;
					tickMainMenu(options.controllers[0].CButton);
				}
			}
		} else if (state.is(StateEnum.STAGE) && options.players[0] == 1) {
			Point goingTo = stage.getTileAtActualPoint(new Point(x, y), this);
			if (goingTo == null)
				return;
			logger.log(LogLevel.DEBUG, "goingTo: " + goingTo);
			counter[7] = Integer.parseInt(new String(goingTo.x + "97079" + goingTo.y));
		}
	}

	public void mouseReleased(int x, int y, int button) {
		super.mousePressed(x, y, button);
		mousePressed = false;
		if (network != null && network.graphic.isMoving) {
			network.graphic.stopMoving();
		} else if (state.is(StateEnum.STAGE) && options.players[0] == 1) {
			if (counter[7] > 10000)
				counter[7] = 0;
			players.get(0).upPressed = false;
			players.get(0).downPressed = false;
			players.get(0).leftPressed = false;
			players.get(0).rightPressed = false;
		}
	}

	public void mouseClicked(int x, int y, int button) {
		if (mousePressed)
			return;
		super.mouseClicked(x, y, button);
		if (state.is(StateEnum.STAGE) && options.players[0] == 1 && button == 3) {
			// Point player = Stage.getExactTileMidPoint(this,
			// players.get(0).getCellPoint());
			// if (new Point(x, y).distance(player) < 50)
			players.get(0).cPressed = true;
		}
	}

	@SuppressWarnings("deprecation")
	public void keyReleased(KeyEvent e) {
		super.keyReleased(e);
		keyReleased(e.getKeyCode());
	}

	public void keyReleased(int keyCode) {
		while (keysPressed.contains(keyCode))
			for (int i = 0; i < keysPressed.size(); i++)
				if (keysPressed.get(i) == keyCode) {
					keysPressed.remove(i);
					break;
				}
		if (state.equals(StateEnum.STAGE))
			if (network.isPlayingOnline()) {
				for (int i = 0; i < options.players.length; i++)
					if (i == network.myInputTurnP1) {
						if (keyCode == options.controllers[0].upButton)
							players.get(i).keyUp(0);
						else if (keyCode == options.controllers[0].downButton)
							players.get(i).keyUp(1);
						else if (keyCode == options.controllers[0].leftButton)
							players.get(i).keyUp(2);
						else if (keyCode == options.controllers[0].rightButton)
							players.get(i).keyUp(3);
						else if (keyCode == options.controllers[0].AButton)
							players.get(i).keyUp(4);
						else if (keyCode == options.controllers[0].BButton)
							players.get(i).keyUp(5);
						else if (keyCode == options.controllers[0].CButton)
							players.get(i).keyUp(6);
						else if (keyCode == options.controllers[0].LButton)
							players.get(i).keyUp(7);
						else if (keyCode == options.controllers[0].RButton)
							players.get(i).keyUp(8);
					} else if (i == network.myInputTurnP2) {
						if (keyCode == options.controllers[1].upButton)
							players.get(i).keyUp(0);
						else if (keyCode == options.controllers[1].downButton)
							players.get(i).keyUp(1);
						else if (keyCode == options.controllers[1].leftButton)
							players.get(i).keyUp(2);
						else if (keyCode == options.controllers[1].rightButton)
							players.get(i).keyUp(3);
						else if (keyCode == options.controllers[1].AButton)
							players.get(i).keyUp(4);
						else if (keyCode == options.controllers[1].BButton)
							players.get(i).keyUp(5);
						else if (keyCode == options.controllers[1].CButton)
							players.get(i).keyUp(6);
						else if (keyCode == options.controllers[1].LButton)
							players.get(i).keyUp(7);
						else if (keyCode == options.controllers[1].RButton)
							players.get(i).keyUp(8);
					}
			} else
				for (int i = 0; i < options.players.length; i++)
					if (options.players[i] == 1)
						if (keyCode == options.controllers[i].upButton)
							players.get(i).keyUp(0);
						else if (keyCode == options.controllers[i].downButton)
							players.get(i).keyUp(1);
						else if (keyCode == options.controllers[i].leftButton)
							players.get(i).keyUp(2);
						else if (keyCode == options.controllers[i].rightButton)
							players.get(i).keyUp(3);
						else if (keyCode == options.controllers[i].AButton)
							players.get(i).keyUp(4);
						else if (keyCode == options.controllers[i].BButton)
							players.get(i).keyUp(5);
						else if (keyCode == options.controllers[i].CButton)
							players.get(i).keyUp(6);
						else if (keyCode == options.controllers[i].LButton)
							players.get(i).keyUp(7);
						else if (keyCode == options.controllers[i].RButton)
							players.get(i).keyUp(8);
		if (state.is(StateEnum.MATCH_SELECTION) && network.isPlayingOnline() && Math.random() == 5) {
			int buttonPressed = -1, index = -1;
			int[] buttons1p = options.controllers[0].getButtonArray();
			int[] buttons2p = options.controllers[1].getButtonArray();
			String[] msg = Options.Controls.getStringArray();
			if (network.inputTurn != network.myInputTurnP1 && network.inputTurn != network.myInputTurnP2)
				return;
			else if (network.inputTurn == network.myInputTurnP2) {
				for (int i = 0; i < buttons2p.length; i++)
					if (keyCode == buttons2p[i]) {
						buttonPressed = buttons1p[i];
						index = i;
					}
			} else if (network.inputTurn == network.myInputTurnP1) {
				for (int i = 0; i < buttons1p.length; i++)
					if (keyCode == buttons1p[i]) {
						buttonPressed = buttons1p[i];
						index = i;
					}
			}
			if (buttonPressed == -1)
				return;
			else {
				if (network.weAreHost())
					network.server.sendMessageToAllClients(
							"SERVER:KEY" + Client.SPLITTER + "RELEASE" + Client.SPLITTER + msg[index]);
				else
					network.client.sendMessageToServer(
							"CLIENT:KEY" + Client.SPLITTER + "RELEASE" + Client.SPLITTER + msg[index]);
				// keyReleased(buttonPressed);
				return;
			}
		}
	}

	@SuppressWarnings("deprecation")
	@Override
	public void keyPressed(KeyEvent e) {
		int keyCode = e.getKeyCode();
		if (!keysPressed.contains(keyCode))
			keysPressed.add(keyCode);
		else
			return;
		try {
			if (state.equals(StateEnum.MATCH_SELECTION) && network.isPlayingOnline()) {
				boolean pressIsGameRelated = false;
				int buttonPressed = -1, index = -1;
				int[] buttons1p = options.controllers[0].getButtonArray();
				int[] buttons2p = options.controllers[1].getButtonArray();
				String[] msg = Options.Controls.getStringArray();
				for (int i = 0; i < buttons1p.length; i++)
					if (buttons1p[i] == keyCode)
						pressIsGameRelated = true;
				for (int i = 0; i < buttons2p.length; i++)
					if (buttons2p[i] == keyCode)
						pressIsGameRelated = true;
				if (!pressIsGameRelated) {
					// Don't progress further if key press isn't something from
					// the game. Press it like normal.
					super.keyPressed(e);
					return;
				}
				if (network.inputTurn != network.myInputTurnP1 && network.inputTurn != network.myInputTurnP2)
					return;
				else if (network.inputTurn == network.myInputTurnP2) {
					for (int i = 0; i < buttons2p.length; i++)
						if (keyCode == buttons2p[i]) {
							buttonPressed = buttons1p[i];
							index = i;
						}
				} else if (network.inputTurn == network.myInputTurnP1) {
					for (int i = 0; i < buttons1p.length; i++)
						if (keyCode == buttons1p[i]) {
							buttonPressed = buttons1p[i];
							index = i;
						}
				}
				if (buttonPressed != -1) {
					String counterMsg = "";
					for (int i = 0; i < counter.length; i++)
						counterMsg += "-_-=COUNTER=-_-" + counter[i];
					if (network.weAreHost())
						network.server.sendMessageToAllClients(
								"SERVER:KEY" + Client.SPLITTER + "PRESS" + Client.SPLITTER + msg[index] + counterMsg);
					else
						network.client.sendMessageToServer(
								"CLIENT:KEY" + Client.SPLITTER + "PRESS" + Client.SPLITTER + msg[index] + counterMsg);
					super.keyPressed(e);
				}
				return;
			}
		} catch (Exception ex) {
			// ignore
		}
		if (!state.equals(StateEnum.MATCH_SELECTION) || !network.isPlayingOnline()) {
			super.keyPressed(e);
		} else {
			boolean pressIsGameRelated = false;
			int[] buttons1p = options.controllers[0].getButtonArray();
			int[] buttons2p = options.controllers[1].getButtonArray();
			for (int i = 0; i < buttons1p.length; i++)
				if (buttons1p[i] == keyCode)
					pressIsGameRelated = true;
			for (int i = 0; i < buttons2p.length; i++)
				if (buttons2p[i] == keyCode)
					pressIsGameRelated = true;
			if (!pressIsGameRelated) {
				super.keyPressed(e);
			}
		}
	}

	@Override
	public void keyPressed(int keyCode) {
		if (keyCode == KeyEvent.VK_F1) {
			counter[2] += 120000;
		} else if (keyCode == KeyEvent.VK_F2)
			debug = !debug;
		else if (keyCode == KeyEvent.VK_F3) {
			freezeAI = !freezeAI;
		} else if (keyCode == KeyEvent.VK_F4) {
			// joystickHandler.forceResetJoysticks();
			System.out.println("");
			for (int i = 0; i < players.size(); i++)
				if (players.get(i).ai != null && players.get(i).deadTimer == -1) {
					System.out.println("");
					System.out.print("AI#" + i + ":");
					System.out.print(" Hunter Level: " + players.get(i).ai.playerHunterLevel);
					System.out.print(" Speed Ratio: " + players.get(i).ai.speedRatio);
					System.out.print(" Proactive: " + players.get(i).ai.walkToBombPointProactively);
					System.out.print(" Distance Modifier: " + players.get(i).ai.distanceModifier);
					System.out.print(" Reaction Time: " + players.get(i).ai.reactionTime);
					System.out.print(" Fire Multiplier: " + players.get(i).ai.walkingThroughFireMultiplier);
				}
		} else if (keyCode == KeyEvent.VK_F5) {
			if (pause.all())
				pause = Pause.PAINT;
			else if (pause.game())
				pause = Pause.NONE;
			else if (pause.paint())
				pause = Pause.ALL;
			else
				pause = Pause.GAME;
		} else if (keyCode == KeyEvent.VK_F6) {
			if (pause.all())
				pause = Pause.GAME;
			else if (pause.game())
				pause = Pause.ALL;
			else if (pause.paint())
				pause = Pause.NONE;
			else
				pause = Pause.PAINT;
		} else if (keyCode == KeyEvent.VK_F7) {
			if (state.is(StateEnum.MAIN_MENU)) {
				setPause(Pause.PAINT);
				options.multiplayer.username = "RAND" + (int) (Math.random() * 10000d);
				network.setRandomCursor();
				state.set(StateEnum.NETWORKING, this);
				counter[0] = 1;
				counter[1] = 1;
				counter[2] = 0;
				counter[3] = -1;
				temp[1] = "Played Networking";
				music.stop();
				keyPressed(options.controllers[0].CButton);
				setPause(Pause.NONE);
			}
		} else if (keyCode == KeyEvent.VK_F8) {
			if (state.is(StateEnum.MAIN_MENU)) {
				setPause(Pause.PAINT);
				options.multiplayer.username = "RAND" + (int) (Math.random() * 10000d);
				network.setRandomCursor();
				state.set(StateEnum.NETWORKING, this);
				counter[0] = 1;
				counter[1] = 2;
				counter[2] = 0;
				counter[3] = -1;
				temp[1] = "Played Networking";
				music.stop();
				keyPressed(options.controllers[0].CButton);
				setPause(Pause.NONE);
			}
		} else if (keyCode == KeyEvent.VK_F9) {
			com.github.vegeto079.saturnbomberman.objects.Bomb bomb = new com.github.vegeto079.saturnbomberman.objects.Bomb(
					new Point(-1, 4), BombType.NORMAL, players.get(0), this, 14, System.currentTimeMillis(), 1);
			bomb.explodeNow();
			bomb = new com.github.vegeto079.saturnbomberman.objects.Bomb(new Point(-1, 6), BombType.NORMAL,
					players.get(0), this, 14, System.currentTimeMillis(), 1);
			bomb.explodeNow();
			// System.out.println("network.getPingBuffer(): " +
			// network.getPingBuffer());
		} else if (keyCode == KeyEvent.VK_F10) {
			// Player p = new Player();
			// p.index = -1;
			// objects.add(new Bomb(new Point(0, 0), BombType.POWER, p, this, 9,
			// System
			// .currentTimeMillis() - 100));
			if (state.equals(StateEnum.NETWORKING)) {
				if (network.getRandomCursor() == 2)
					network.randomCursor = 0;
				else
					network.randomCursor++;
				music.stop();
				temp[1] = "";
			}
		} else if (keyCode == KeyEvent.VK_F11) {
			if (paintTicksPerSecond > 30)
				paintTicksPerSecond = 30;
			else if (paintTicksPerSecond == 30)
				paintTicksPerSecond = 15;
			else
				paintTicksPerSecond = 70;
		} else if (keyCode == KeyEvent.VK_F12) {
			// if (!network.isPlayingOnline() && debug)
			// System.exit(0);
			// TODO: goto benchmarking
			benchmarkStatus = 0;
			ticksPerSecond = 60;
			paintTicksPerSecond = 1000;
			setPause(Pause.PAINT);
			int playerAmt = 1;
			int aiAmt = 7;
			options.battleOptionsFinished = true;
			options.rulesOptionsFinished = true;
			options.bombersOptionsFinished = true;
			options.comLevel = 5;
			options.shuffle = false;
			options.time = 3f;
			counter[0] = 0;
			counter[1] = 0;
			counter[2] = currentTimeMillis();
			counter[3] = 0;
			counter[4] = 0;
			counter[5] = 0;
			counter[6] = 0;
			counter[7] = 0;
			players = new ArrayList<Player>();
			while (objects.size() > 0)
				objects.remove(0);
			for (int i = 0; i < playerAmt + aiAmt; i++) {
				players.add(new Player(this));
				Character c = new Character(pictures, i);
				players.get(i).setIndex(i);
				players.get(i).character = c;
				players.get(i).isCpu = true;
			}
			for (int i = 0; i < players.size(); i++)
				objects.add(players.get(i));
			stage = new Stage(1, this);
			state.setNextStateAfterFade(StateEnum.GOING_TO_STAGE, this);
			setPause(Pause.NONE);
		} else if (keyCode == KeyEvent.VK_F13 && debug) {
			if (!network.isPlayingOnline()) {
				if (temp[0].equals("")) {
					temp[0] = state.getName();
					state.set(StateEnum.DEBUG, this);
					tickcounter = 0;
				} else {
					state.set(temp[0], this);
					temp[0] = "";
				}
			}
		} else if (keyCode == KeyEvent.VK_MINUS) {
			logger.log(LogLevel.DEBUG, "Lowering volume.");
			if (sound != null)
				sound.changeVolume(-.05f);
			if (music != null)
				music.changeVolume(-.05f);
		} else if (keyCode == KeyEvent.VK_EQUALS) {
			logger.log(LogLevel.DEBUG, "Raising volume.");
			if (sound != null)
				sound.changeVolume(.05f);
			if (music != null)
				music.changeVolume(.05f);
		} else if (state.is(StateEnum.DEBUG)) {
			if (keyCode == options.controllers[0].downButton)
				tickcounter++;
			else if (keyCode == options.controllers[0].upButton)
				tickcounter--;
		} else if (state.is(StateEnum.MAIN_MENU))
			tickMainMenu(keyCode);
		else if (state.is(StateEnum.NETWORKING))
			tickNetworking(keyCode);
		else if (state.is(StateEnum.CONTROLS))
			tickControls(keyCode);
		else if (state.is(StateEnum.MATCH_SELECTION))
			tickMatchSelection(keyCode);
		else if (state.is(StateEnum.STAGE))
			tickStage(keyCode);
		else if (state.is(StateEnum.GOING_TO_STAGE))
			tickGoingToStage(keyCode);
		else if (state.is(StateEnum.END_OF_MATCH))
			tickEndOfMatch(keyCode);
	}
}