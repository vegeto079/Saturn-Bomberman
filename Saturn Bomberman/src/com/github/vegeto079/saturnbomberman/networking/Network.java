package com.github.vegeto079.saturnbomberman.networking;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Stroke;
import java.util.ArrayList;

import com.github.vegeto079.saturnbomberman.main.MainBomberman;
import com.github.vegeto079.saturnbomberman.main.Stage;
import com.github.vegeto079.saturnbomberman.main.Character.Animation;
import com.github.vegeto079.saturnbomberman.misc.State;
import com.github.vegeto079.saturnbomberman.misc.State.StateEnum;
import com.github.vegeto079.saturnbomberman.objects.Block;
import com.github.vegeto079.saturnbomberman.objects.Bomb;
import com.github.vegeto079.saturnbomberman.objects.Item;
import com.github.vegeto079.saturnbomberman.objects.Player;
import com.github.vegeto079.saturnbomberman.objects.Bomb.BombType;

import com.github.vegeto079.ngcommontools.main.*;
import com.github.vegeto079.ngcommontools.main.Game.*;
import com.github.vegeto079.ngcommontools.main.Logger.*;
import com.github.vegeto079.ngcommontools.networking.*;
import com.github.vegeto079.ngcommontools.networking.Server.*;
import com.github.vegeto079.ngcommontools.networking.Client.*;
/**
 * 
 * @author Nathan
 * @version 1.0: Started tracking version.
 * @version 1.01: {@link Message} added, with {@link #messagesSent} and
 *          {@link #getMessagesSentTo(ArrayList, String)}. We now keep track of
 *          all messages sent and re-send them if they are not received within a
 * @version 1.02: Reverted version 1.01, as I don't think packet loss is the
 *          problem.
 * @version 1.03: Fixed problem, there was an issue where we were forwarding the
 *          usernames of clients (as well as their messages) to everyone, making
 *          the client think that the username was the end of the message,
 *          cutting it short, missing lots of details - which looked like packet
 *          loss. Now that we omit the username part of the forwarded message,
 *          everything works normally.
 * @version 1.04: Added {@link #getPingBuffer()}.
 * @version 1.1: Added {@link NetworkEvent}, {@link NetworkEventType},
 *          {@link #waitingNetworkEvents},
 *          {@link #addNetworkEvent(NetworkEventType, long, String[])}.
 * @version 1.11: Changed all {@link System#currentTimeMillis()} references to
 *          {@link MainBomberman#currentTimeMillis()}.
 */
public class Network {
	public int serverMaxClients;

	/**
	 * The amount of time (in ms) between connection attempts with
	 * clients/servers. Ping cannot be below this value.
	 */
	public long timeBetweenConnectionAttempts = 25;
	// public long timeBetweenConnectionAttempts = 50;
	// public long timeBetweenConnectionAttempts = 1500;

	/**
	 * Last time we told Clients general game information.
	 */
	public long toldClientsInfoTime = 0;
	/**
	 * The amount of time we wait before telling Clients general game
	 * information, just incase.
	 */
	public long tellClientsInfoDelay = 1000;
	public ArrayList<String[]> connectedClients = null;
	public int inputTurn = -1;
	public int myInputTurnP1 = -1;
	public int myInputTurnP2 = -1;

	public MS masterServer;
	public S server;
	public C client;
	public MainBomberman game;
	public NetworkGraphic graphic;

	public BombermanServerMessageHandler serverMessageHandler;
	public BombermanClientMessageHandler clientMessageHandler;

	public String overrideMasterServerIP = null;
	/**
	 * The current cursor type (networking menu)
	 */
	public int randomCursor;

	public NetworkTalking talking;
	/**
	 * Set this to simulate client latency.
	 */
	public long simulatedLag = 0;

	public Network(MainBomberman game) {
		this.game = game;
		graphic = new NetworkGraphic();
		talking = new NetworkTalking(game);
		masterServer = new MS();
		setRandomCursor();
	}

	/**
	 * Sets {@link #randomCursor}
	 */
	public void setRandomCursor() {
		randomCursor = (int) (Math.random() * (game.pictures.networkingCursor.size() - 1)
				/ 2);
	}

	/**
	 * @return {@link #randomCursor}
	 */
	public int getRandomCursor() {
		return randomCursor;
	}

	/**
	 * The main ticking thread for the Network. Handles anything that needs to
	 * be on-going, such as messages that must be sent periodically.
	 */
	public void tick() {
		if (server != null) {
			try {
				if (connectedClients.size() == 0)
					connectedClients.add(new String[] { game.options.multiplayer.username,
							"" + game.options.multiplayer.localPlayers, "1" });
				for (int i = 0; i < connectedClients.size(); i++) {
					String clientName = connectedClients.get(i)[0];
					if (clientName.equals(game.options.multiplayer.username))
						continue;
					int index = server.getHandlerIndex(clientName);
					if (index == -1) {
						// client disconnected
						connectedClients.remove(i);
						break;
					}
				}
			} catch (Exception e) {
				// Could throw an Exception if a Client is added/removed while
				// running this. Just ignore it, next tick will correct it.
			}
			if (server.getConnectedClientAmt() > 0 && game.currentTimeMillis()
					- toldClientsInfoTime >= tellClientsInfoDelay) {
				game.logger.log(LogLevel.DEBUG,
						"It's been a while, sending clients server info.");
				toldClientsInfoTime = game.currentTimeMillis();
				String message = "SERVER_INFO:" + game.options.multiplayer.maxPlayers;
				for (int i = 0; i < connectedClients.size(); i++) {
					int ping = -1;
					if (!connectedClients.get(i)[0]
							.equals(game.options.multiplayer.username))
						ping = server.getClientPing(connectedClients.get(i)[0]);
					// continue;
					message += ":" + connectedClients.get(i)[0] + Client.SPLITTER
							+ connectedClients.get(i)[1] + Client.SPLITTER + ping
							+ Client.SPLITTER + connectedClients.get(i)[2];
				}
				if (game.state.is(StateEnum.MATCH_SELECTION)
						|| game.state.is(StateEnum.END_OF_MATCH))
					message += "-_-SEED-_-" + game.seed + "-" + game.randomCount;
				server.sendMessageToAllClients(message);
			}
		} else if (client != null) {
			if (client.isConnected() && game.currentTimeMillis()
					- toldClientsInfoTime >= tellClientsInfoDelay) {
				toldClientsInfoTime = Long.MAX_VALUE; // dont tell twice
				client.sendMessageToServer("CLIENT_INFO" + Client.SPLITTER
						+ game.options.multiplayer.localPlayers + Client.SPLITTER);
			}
		}
		runNetworkEvents();
	}

	public void tick(Graphics2D g) {
		if (graphic != null)
			graphic.tick(g);
	}

	/**
	 * @return Total players connected to the game, including clients who have
	 *         multiple local players. Returns -1 if not connected.
	 */
	public int getTotalPlayersAmt() {
		if (!isPlayingOnline())
			return -1;
		int playerCount = 0;
		for (int i = 0; i < connectedClients.size(); i++) {
			playerCount++;
			if (Integer.parseInt(connectedClients.get(i)[1]) == 2)
				playerCount++;
		}
		return playerCount;
	}

	/**
	 * @return The amount of currently connected Clients. Takes into account
	 *         Clients with more than one local player.
	 */
	public int getConnectedClientAmt() {
		try {
			if (connectedClients == null)
				return -1;
			int amt = 0;
			for (int i = 0; i < connectedClients.size(); i++)
				amt += Integer.parseInt(connectedClients.get(i)[1]);
			return amt;
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
	}

	/**
	 * @return If we are playing online
	 */
	public boolean isPlayingOnline() {
		return getConnectedClientAmt() > 0;
	}

	/**
	 * @return If we are the host of an online game. Returns false if we are not
	 *         online.
	 */
	public boolean weAreHost() {
		if (isPlayingOnline() && server != null)
			return true;
		return false;
	}

	/**
	 * @return The highest amount of ping (multiplied by 2) of any connected
	 *         player, used as a buffer to time all clients together.<br>
	 *         Returns 0 if not online. Minimum of 250 when online.
	 */
	public int getPingBuffer() {
		int biggest = 0;
		if (isPlayingOnline()) {
			if (server != null) {
				ArrayList<Server.Handler> handlers = server.getHandlers();
				for (int i = 0; i < handlers.size(); i++) {
					int ping = handlers.get(i).getPing();
					if (ping > biggest)
						biggest = ping;
				}
			} else if (client != null) {
				for (int i = 0; i < connectedClients.size(); i++) {
					int ping = Integer.parseInt(connectedClients.get(i)[2]);
					if (ping > biggest)
						biggest = ping;
				}
			}
		}
		if (game.network.isPlayingOnline() && biggest < 100)
			return 250;
		return (int) ((double) biggest * 2d);
	}

	/**
	 * Starts {@link #server} and opens incoming connections for Clients.
	 * 
	 */
	public void startServer() {
		connectedClients = new ArrayList<String[]>();
		serverMessageHandler = new BombermanServerMessageHandler();
		server = new S(serverMessageHandler, game.logger, 5678,
				timeBetweenConnectionAttempts, 100000, game.seed,
				game.options.multiplayer.username);
		server.openIncomingClientConnection();

	}

	/**
	 * Starts {@link Network#client} and attempts to connect to the given
	 * Server.
	 * 
	 * @param ip
	 *            Server to connect to.
	 */
	public void startClient(String ip) {
		// String customIP = Tools.displayInputDialog(
		// "If you want to input a custom IP, do so here.",
		// "Otherwise, leave this blank.");
		// if (customIP != null && !customIP.equals(""))
		// ip = customIP;
		clientMessageHandler = new BombermanClientMessageHandler();
		client = new C(clientMessageHandler, game.logger, timeBetweenConnectionAttempts,
				game.options.multiplayer.username);
		client.connectToServer(ip, 5678, 5);
		connectedClients = new ArrayList<String[]>();
	}

	/**
	 * Handles all information regarding Master Server (<b>MS</b>).
	 */
	public class MS {
		public MasterServerConnector msConnector = null;

		public MS() {
			msConnector = new MasterServerConnector();
		}

		/**
		 * Begin connection to the Master Server.
		 * 
		 * @param game
		 * @return Whether or not we successfully connected to the Master
		 *         Server.
		 */
		public boolean connect() {
			if (overrideMasterServerIP != null)
				msConnector.ip = overrideMasterServerIP;
			return msConnector.run(msConnector.ip, game.logger,
					game.options.multiplayer.username);
		}

		/**
		 * @return Whether or not we are currently connected to the Master
		 *         Server.
		 */
		public boolean isConnected() {
			return msConnector.connected;
		}

		/**
		 * @return Whether or not we are currently connecting to the Master
		 *         Server.
		 */
		public boolean isConnecting() {
			return msConnector.connecting;
		}

		/**
		 * @return Whether or not we have been whitelisted for running P2P
		 *         connections by the Master Server.
		 */
		public boolean weCanP2P() {
			return msConnector.canRunP2P;
		}

		/**
		 * Disconnects from Master Server.
		 */
		public void disconnect() {
			msConnector.disconnect();
		}

		/**
		 * @return Information regarding all Clients connected to the Master
		 *         Server.
		 */
		public ArrayList<String[]> getClientInfo() {
			return msConnector.getMasterServerClientInfo();
		}

		/**
		 * @return {@link #msConnector} == null?
		 */
		public boolean isNull() {
			return msConnector == null;
		}

		/**
		 * @return {@link MasterServerConnector#connectingMessage}.
		 */
		public String getConnectingMessage() {
			return msConnector.connectingMessage;
		}
	}

	/**
	 * Handles all information regarding Server (<b>S</b>).
	 */
	public class S extends Server {

		public S(ServerMessageHandler messageHandler, Logger logger, int port,
				long timeBetweenConnectionAttempts, long clientTimeout, long seed,
				String username) {
			super(messageHandler, logger, port, timeBetweenConnectionAttempts,
					clientTimeout, seed, username);
		}

		/**
		 * Sets information regarding connected/connecting clients to our
		 * server.
		 * 
		 * @param connectedClients
		 * @param maxClients
		 */
		public void setClientConnectingInfo(int connectedClients, int maxClients) {
			masterServer.msConnector.connectedClients = connectedClients;
			masterServer.msConnector.maxClients = maxClients;
		}

		/**
		 * {@link Server#disconnect()} and sets {@link Network#server} to null.
		 */
		@Override
		public void disconnect() {
			if (connectedClients.size() > game.options.multiplayer.localPlayers)
				Tools.displayDialog(
						"Uh oh! Server disconnected. This is unrecoverable and requires a game restart.");
			super.disconnect();
			client = null;
		}

		@Override
		public void onExit(Handler handler, int index) {
			super.onExit(handler, index);
			server.sendMessageToAllClients(
					"SERVER:CLIENTDISCONNECT" + Client.SPLITTER + handler.getTheirName());
			Tools.displayDialog("Uh oh! Client \"" + handler.getTheirName()
					+ "\"Disconnected. This is unrecoverable and requires a game restart.");
		}
	}

	/**
	 * Handles all information regarding Client (<b>C</b>).
	 */
	public class C extends Client {
		public C(ClientMessageHandler messageHandler, Logger logger,
				long timeBetweenConnectionAttempts, String username) {
			super(messageHandler, logger, timeBetweenConnectionAttempts, username);
			lag = simulatedLag;
		}

		/**
		 * Runs {@link Client#disconnect()} normally, then sets
		 * {@link Network#client} to null.
		 */
		@Override
		public void disconnect() {
			if (connectedClients.size() > game.options.multiplayer.localPlayers)
				Tools.displayDialog(
						"Uh oh! Disconnected from Server. This is unrecoverable and requires a game restart.");
			super.disconnect();
			client = null;
			toldClientsInfoTime = 0;
		}
	}

	public void setClientsReady(boolean ready) {
		String set = ready ? "1" : "0";
		// System.err.println("setClientsReady(" + ready + ")");
		for (int i = 0; i < connectedClients.size(); i++)
			if (weAreHost()) {
				if (!connectedClients.get(i)[2].equals(set))
					connectedClients.set(i, new String[] { connectedClients.get(i)[0],
							connectedClients.get(i)[1], set });
			} else if (!connectedClients.get(i)[3].equals(set))
				connectedClients.set(i, new String[] { connectedClients.get(i)[0],
						connectedClients.get(i)[1], connectedClients.get(i)[2], set });
	}

	public class BombermanServerMessageHandler extends ServerMessageHandler {

		public void process(String message) {
			if (message.contains("CLIENT_INFO")) {
				game.logger.log(LogLevel.DEBUG,
						"Got client info message! (" + message + ")");
				String username = message.split(Client.USERNAME_SPLITTER)[1];
				String[] split = message.split(Client.SPLITTER);
				int localClients = Integer.parseInt(split[1]);
				connectedClients.add(new String[] { username, "" + localClients, "1" });
			} else if (message.startsWith("CLIENT:")) {
				game.logger.log(LogLevel.DEBUG, "Got client message!");
				String username = message.split(":")[1]
						.split(Client.USERNAME_SPLITTER)[1];
				message = message.split(":")[1].split(Client.USERNAME_SPLITTER)[0];
				if (message.startsWith("KEY")) {
					game.logger.log(LogLevel.DEBUG,
							"Got client message for key! (" + message + ")");
					String[] counters = message.split("-_-=COUNTER=-_-");
					for (int i = 1; i < counters.length; i++)
						game.counter[i - 1] = Long.parseLong(counters[i]);
					message = counters[0];
					game.logger.log(LogLevel.DEBUG, "Set counters.");
					String key = message.split(Client.SPLITTER)[2];
					int[] buttons = game.options.controllers[0].getButtonArray();
					String[] msg = com.github.vegeto079.saturnbomberman.main.Options.Controls.getStringArray();
					int keyCode = -1;
					for (int i = 0; i < buttons.length; i++)
						if (key.equals(msg[i]))
							keyCode = buttons[i];
					if (keyCode == -1)
						game.logger.log(LogLevel.ERROR,
								"Couldn't find key from message! " + message);
					else {
						boolean press = message.split(Client.SPLITTER)[1].equals("PRESS");
						for (int i = 0; i < server.getHandlers().size(); i++) {
							String handlerName = server.getHandlers().get(i)
									.getTheirName();
							if (handlerName.equals(username))
								continue;
							else
								server.sendMessageToClient(
										"SERVER:KEY" + Client.SPLITTER
												+ message.split(Client.SPLITTER)[1]
												+ Client.SPLITTER
												+ message.split(Client.SPLITTER)[2],
										handlerName);
						}
						if (press) {
							game.logger.log(LogLevel.DEBUG,
									"Pressing key (client request): " + keyCode + " ("
											+ key + ")");
							game.keyPressed(keyCode);
						} else {
							game.logger.log(LogLevel.DEBUG,
									"Releasing key (client request): " + keyCode + " ("
											+ key + ")");
							game.keyReleased(keyCode);
						}
					}
				} else if (message.equals("READY")) {
					game.logger.log(LogLevel.DEBUG,
							"Client says they're ready! (" + message + ")");
					boolean allReady = true;
					for (int i = 0; i < connectedClients.size(); i++) {
						if (connectedClients.get(i)[0].equals(username)
								&& connectedClients.get(i)[2].equals("0")) {
							game.logger.log(LogLevel.DEBUG,
									"Set " + username + " to ready.");
							connectedClients.set(i,
									new String[] { connectedClients.get(i)[0],
											connectedClients.get(i)[1], "1" });
						}
						if (!connectedClients.get(i)[2].equals("1"))
							allReady = false;
					}
					if (allReady)
						if (game.state.is(StateEnum.GOING_TO_STAGE)
								&& game.counter[3] == 0) {
							// TODO: starting at diff times?
							long startGame = game.currentTimeMillis() + getPingBuffer();
							game.logger.log(LogLevel.DEBUG,
									"Everyone is ready! Time to go! (" + message
											+ ") startGame (" + startGame
											+ ") time until game ("
											+ (startGame - game.currentTimeMillis())
											+ ")");
							server.sendMessageToAllClients(
									"SERVER:STARTGAME" + Client.SPLITTER + startGame);
							game.setPause(Pause.ALL);
							game.counter[1] = 0;
							game.counter[2] = startGame;
							game.counter[3] = 2;
							game.setPause(Pause.NONE);
						} else if (game.state.is(StateEnum.END_OF_MATCH)) {
							game.logger.log(LogLevel.DEBUG,
									"Everyone is ready! Time to go! (" + message + ")");
							server.sendMessageToAllClients("SERVER:SELECTMATCH");
							game.setPause(Pause.ALL);
							game.sound.play("Select");
							game.state.set(StateEnum.MATCH_SELECTION, game);
							game.counter[0] = 16;
							game.counter[1] = 0;
							game.counter[4] = 0;
							game.counter[5] = 0;
							game.counter[6] = 1;
							game.setPause(Pause.NONE);
						}
				} else if (message.equals("NEEDSEED")) {
					game.logger.log(LogLevel.DEBUG,
							"Client asked for seed, sending info.");
					server.sendMessageToClient("SERVER:SEEDINFO" + Client.SPLITTER
							+ game.seed + Client.SPLITTER + game.counter[7]
							+ Client.SPLITTER + game.counter[6], username);
				}
			} else if (message.startsWith("GAME:")) {
				server.sendMessageToAllClientsExcludingNames(
						message.split(Client.USERNAME_SPLITTER)[0],
						message.split(Client.USERNAME_SPLITTER)[1]);
				// server.sendMessageToAllClients(message);
				processGameMessage(message);
			}
		}
	}

	public class BombermanClientMessageHandler extends ClientMessageHandler {

		public void process(String message) {
			if (message.contains("SERVER_INFO")) {
				message = message.split(Client.USERNAME_SPLITTER)[0];
				game.logger.log(LogLevel.DEBUG,
						"Got server info message! (" + message + ")");
				if (message.contains("-_-SEED-_-")) {
					String seedInfo = message.split("-_-SEED-_-")[1];
					game.setSeed(Long.parseLong(seedInfo.split("-")[0]),
							Long.parseLong(seedInfo.split("-")[1]));
					message = message.split("-_-SEED-_-")[0];
				}
				ArrayList<String[]> tempConnectedClients = new ArrayList<String[]>();
				String[] split = message.split(":");
				serverMaxClients = Integer.parseInt(split[1]);
				for (int i = 2; i < split.length; i++) {
					String[] clientInfo = split[i].split(Client.SPLITTER);
					tempConnectedClients.add(new String[] { clientInfo[0], clientInfo[1],
							clientInfo[2], clientInfo[3] });
				}
				connectedClients = tempConnectedClients;
			} else if (message.startsWith("SERVER:")) {
				message = message.split(":")[1].split(Client.USERNAME_SPLITTER)[0];
				game.logger.log(LogLevel.DEBUG, "Got server message! (" + message + ")");
				if (message.equals("START")) {
					game.setPause(Pause.PAINT);
					game.logger.log(LogLevel.DEBUG, "Got server message to start game!");
					for (int i = 0; i < game.counter.length; i++)
						game.counter[i] = 0;
					for (int i = 0; i < game.options.players.length; i++)
						if (i < getTotalPlayersAmt())
							game.options.players[i] = 1;
						else
							game.options.players[i] = 2;
					game.setPause(Pause.NONE);
					game.state.setNextStateAfterFade(StateEnum.MATCH_SELECTION, game);
				} else if (message.startsWith("KEY")) {
					game.logger.log(LogLevel.DEBUG, "Got server message for key!");
					String[] counters = message.split("-_-=COUNTER=-_-");
					for (int i = 1; i < counters.length; i++)
						game.counter[i - 1] = Long.parseLong(counters[i]);
					message = counters[0];
					game.logger.log(LogLevel.DEBUG, "Set counters.");
					String key = message.split(Client.SPLITTER)[2];
					int[] buttons = game.options.controllers[0].getButtonArray();
					String[] msg = com.github.vegeto079.saturnbomberman.main.Options.Controls.getStringArray();
					int keyCode = -1;
					for (int i = 0; i < buttons.length; i++)
						if (key.equals(msg[i]))
							keyCode = buttons[i];
					if (keyCode == -1)
						game.logger.log(LogLevel.ERROR,
								"Couldn't find key from message! " + message);
					else {
						boolean press = message.split(Client.SPLITTER)[1].equals("PRESS");
						if (press) {
							game.logger.log(LogLevel.DEBUG,
									"Pressing key (server request): " + keyCode + " ("
											+ key + ")");
							game.keyPressed(keyCode);
						} else {
							game.logger.log(LogLevel.DEBUG,
									"Releasing key (server request): " + keyCode + " ("
											+ key + ")");
							game.keyReleased(keyCode);
						}
					}
				} else if (message.startsWith("PLAYER")) {
					game.logger.log(LogLevel.DEBUG,
							"Got server message for player position! (" + message + ")");
					int ourCount = Integer.parseInt(message.split(Client.SPLITTER)[1]);
					myInputTurnP1 = ourCount;
					if (game.options.multiplayer.localPlayers == 2)
						myInputTurnP2 = ourCount + 1;
				} else if (message.startsWith("STAGE_SELECT")) {
					game.logger.log(LogLevel.DEBUG,
							"Got server message for stage selection! (" + message + ")");
					game.stage = null;
					game.pause = Pause.ALL;
					game.counter[0] = 19;
					game.counter[7] = 0;
					game.counter[6] = Long.parseLong(message.split(Client.SPLITTER)[1]);
					game.counter[1] = Long.parseLong(message.split(Client.SPLITTER)[2]);
					game.counter[2] = 0;
					game.counter[3] = 0;
					game.counter[4] = 50;
					game.counter[5] = 0;
					game.pause = Pause.NONE;
				} else if (message.startsWith("STARTGAME")) {
					game.setPause(Pause.ALL);
					long startGame = Long.parseLong(message.split(Client.SPLITTER)[1]);
					game.logger.log(LogLevel.DEBUG,
							"Got server message to start game! Starting very soon. ("
									+ message + ") startGame (" + startGame
									+ ") time until game ("
									+ (startGame - game.currentTimeMillis()) + ")");
					game.counter[2] = startGame;
					game.counter[3] = 2;
					game.setPause(Pause.NONE);
					// TODO: starting at diff times?
					// Tools.displayDialog("Got message to start game (" +
					// message + ")");
				} else if (message.startsWith("SELECTMATCH")) {
					game.logger.log(LogLevel.DEBUG,
							"Got server message to select match! (" + message + ")");
					game.setPause(Pause.ALL);
					game.sound.play("Select");
					game.state.set(StateEnum.MATCH_SELECTION, game);
					game.counter[0] = 16;
					game.counter[1] = 0;
					game.counter[4] = 0;
					game.counter[5] = 0;
					game.counter[6] = 1;
					game.setPause(Pause.NONE);
				} else if (message.startsWith("CLIENTDISCONNECT")) {
					Tools.displayDialog("Uh oh! Server told us that Client \""
							+ message.split(Client.SPLITTER)[1]
							+ "\" disconnected.\nThis is unrecoverable and requires a game restart.");
				} else if (message.startsWith("SEEDINFO")) {
					game.setSeed(Long.parseLong(message.split(Client.SPLITTER)[1]),
							Long.parseLong(message.split(Client.SPLITTER)[2]));
					game.counter[1] = Integer.parseInt(message.split(Client.SPLITTER)[3]);
					game.counter[7] = 1;
					// set up stage
				}
			} else if (message.startsWith("GAME:")) {
				processGameMessage(message);
			}
		}
	}

	private void processGameMessage(String message) {
		String username = message.split(Client.USERNAME_SPLITTER)[1];
		message = message.split(":")[1].split(Client.USERNAME_SPLITTER)[0];
		game.logger.log(LogLevel.DEBUG, "Got game message! (" + message + ")");
		String[] info = message.split(Client.SPLITTER);
		if (message.startsWith("KILLED")) {
			game.logger.log(LogLevel.DEBUG, "Someone died! RIP");
			Player player = game.players.get(Integer.parseInt(info[1]));
			int killedBy = Integer.parseInt(info[2]);
			game.setSeed(game.seed, Integer.parseInt(info[3]));
			player.deadTimer = 0;
			player.stats.killedByPlayer(killedBy);
			if (killedBy < Integer.MAX_VALUE)
				game.players.get(killedBy).stats.killedPlayer(Integer.parseInt(info[1]));
			player.character.animation = Animation.IDLE;
			if (player.pickedUpBomb != null)
				player.pickedUpBomb = null;
			game.logger.log(LogLevel.DEBUG, "Player found and killed.");
			// We will be told about the bomb separately
		} else if (message.startsWith("BOMBADD")) {
			game.logger.log(LogLevel.DEBUG, "Adding bomb! Kaboom!");
			Point cellPoint = new Point(Integer.parseInt(info[1]),
					Integer.parseInt(info[2]));
			Bomb.BombType bombType = Bomb.BombType.values()[Integer.parseInt(info[3])];
			Player playerOrigin = game.players.get(Integer.parseInt(info[4]));
			int maxPower = Integer.parseInt(info[5]);
			long explodeTime = Long.parseLong(info[6]);
			long identifier = Long.parseLong(info[7]);
			Bomb bomb = new Bomb(cellPoint, bombType, playerOrigin, game, maxPower,
					explodeTime, identifier);
			game.objects.add(bomb);
			game.logger.log(LogLevel.DEBUG, "Bomb added!");
		} else if (message.startsWith("BOMBKICK")) {
			game.logger.log(LogLevel.DEBUG, "Setting a network event for bomb kick!");
			addNetworkEvent(NetworkEventType.BOMBKICK, Long.parseLong(info[5]), info);
		} else if (message.startsWith("BOMBBOUNCESTOP")) {
			game.logger.log(LogLevel.DEBUG,
					"Setting a network event for bomb bounce stopping!");
			addNetworkEvent(NetworkEventType.BOMBBOUNCESTOP, Long.parseLong(info[5]),
					info);
		} else if (message.startsWith("BOMBBOUNCEHEAD")) {
			game.logger.log(LogLevel.DEBUG,
					"Setting a network event for bomb bouncing on head!");
			addNetworkEvent(NetworkEventType.BOMBBOUNCEHEAD, Long.parseLong(info[4]),
					info);
		} else if (message.startsWith("BOMBTHROW")) {
			game.logger.log(LogLevel.DEBUG, "Setting a network event for bomb throw!");
			addNetworkEvent(NetworkEventType.BOMBTHROW, Long.parseLong(info[5]), info);
		} else if (message.startsWith("BOMBMOVINGSTOP")) {
			game.logger.log(LogLevel.DEBUG, "Bomb stopped moving!");
			long identifier = Long.parseLong(info[1]);
			Point cellPoint = new Point(Integer.parseInt(info[2]),
					Integer.parseInt(info[3]));
			DoublePoint exactPoint = new DoublePoint(Double.parseDouble(info[4]),
					Double.parseDouble(info[5]));
			Bomb bomb = talking.getBomb(identifier);
			if (bomb != null) {
				game.logger.log(LogLevel.DEBUG, "Bomb stopped!");
				bomb.moving = -1;
				bomb.exactPoint = exactPoint;
				bomb.cellPoint = cellPoint;
				bomb.playerKicked = null;
			} else
				game.logger.log(LogLevel.ERROR, "Could not find stop moving bomb!");
		} else if (message.startsWith("BOMBPICKUP")) {
			game.logger.log(LogLevel.DEBUG, "Bomb picked up!");
			long identifier = Long.parseLong(info[1]);
			Player player = game.players.get(Integer.parseInt(info[2]));
			long time = Long.parseLong(info[3]);
			Bomb bomb = talking.getBomb(identifier);
			if (bomb != null) {
				game.logger.log(LogLevel.DEBUG, "Player picked up bomb!");
				if (bomb.exploding)
					bomb.exploding = false;
				player.pickedUpBomb = new Bomb(bomb, time);
				bomb.remove();
				bomb = null;
			} else
				game.logger.log(LogLevel.ERROR, "Could not find pickup bomb!");
		} else if (message.startsWith("BOMBPLACED")) {
			game.logger.log(LogLevel.DEBUG, "Bomb placement!");
			Player player = game.players.get(Integer.parseInt(info[1]));
			Point cellPoint = new Point(Integer.parseInt(info[2]),
					Integer.parseInt(info[3]));
			boolean fromLineBomb = Boolean.parseBoolean(info[4]);
			long time = Long.parseLong(info[5]);
			long identifier = Long.parseLong(info[6]);
			int bombOrdinal = Integer.parseInt(info[7]);
			player.layBomb(cellPoint, fromLineBomb, time, identifier,
					BombType.values()[bombOrdinal]);
		} else if (message.startsWith("BOMBJELLYKICKED")) {
			game.logger.log(LogLevel.DEBUG, "Jelly bomb kicked!");
			long identifier = Long.parseLong(info[1]);
			DoublePoint exactPoint = new DoublePoint(Double.parseDouble(info[2]),
					Double.parseDouble(info[3]));
			long seed = Long.parseLong(info[4]);
			long randomCount = Long.parseLong(info[5]);
			int direction = Integer.parseInt(info[6]);
			Bomb bomb = talking.getBomb(identifier);
			if (bomb != null) {
				game.logger.log(LogLevel.DEBUG, "Jelly bomb kicked success!");
				game.setSeed(seed, randomCount);
				bomb.exactPoint = exactPoint;
				bomb.moving = direction;
				game.sound.play("Bounce");
			} else
				game.logger.log(LogLevel.ERROR, "Could not find jelly bomb to kick!");
		} else if (message.startsWith("BOMBJELLYBOUNCE")) {
			game.logger.log(LogLevel.DEBUG, "Jelly bomb bounce!");
			long identifier = Long.parseLong(info[1]);
			DoublePoint exactPoint = new DoublePoint(Double.parseDouble(info[2]),
					Double.parseDouble(info[3]));
			long seed = Long.parseLong(info[4]);
			long randomCount = Long.parseLong(info[5]);
			int direction = Integer.parseInt(info[6]);
			Bomb bomb = talking.getBomb(identifier);
			if (bomb != null) {
				game.logger.log(LogLevel.DEBUG, "Jelly bomb bounced success!");
				game.setSeed(seed, randomCount);
				if (bomb.exploding || bomb.pickedUpCount < 3) {
					game.logger.log(LogLevel.DEBUG,
							"Wait, this bomb already landed.. don't move it!");
				} else {
					// If it's closeby probably don't need to update this as
					// you'd
					// just be pulling it backwards
					if (bomb.exactPoint.distance(exactPoint) > 25)
						bomb.exactPoint = exactPoint;
					bomb.pickedUpCount = 3 + direction * 5;
					game.sound.play("Bounce");
				}
			} else
				game.logger.log(LogLevel.ERROR, "Could not find jelly bomb to bounce!");
		} else if (message.startsWith("ITEMDESTROY")) {
			game.logger.log(LogLevel.DEBUG, "An item got destroyed!");
			Point cellPoint = new Point(Integer.parseInt(info[1]),
					Integer.parseInt(info[2]));
			int type = Integer.parseInt(info[3]);
			Item item = talking.getItem(cellPoint, type);
			if (item != null) {
				game.logger.log(LogLevel.DEBUG, "Destroyed item!");
				item.destroy(game);
			} else
				game.logger.log(LogLevel.ERROR, "Could not destroy item!");
		} else if (message.startsWith("ITEMACTIVATE")) {
			game.logger.log(LogLevel.DEBUG, "Someone got an item!");
			Point cellPoint = new Point(Integer.parseInt(info[1]),
					Integer.parseInt(info[2]));
			int type = Integer.parseInt(info[3]);
			Player player = game.players.get(Integer.parseInt(info[4]));
			long retrievalTime = Long.parseLong(info[5]);
			Item item = talking.getItem(cellPoint, type);
			if (item == null) {
				// Could not find item. Create one to pickup! We deserve it.
				item = new Item(cellPoint, game, type);
			} else {
				// Found item
			}
			item.activate(game, player, true, retrievalTime);
			game.logger.log(LogLevel.DEBUG, "Item activated!");
		} else if (message.startsWith("PLAYERMOVE")) {
			game.logger.log(LogLevel.DEBUG, "Player is moving!");
			Player player = game.players.get(Integer.parseInt(info[1]));
			player.lastButtonPress = game.currentTimeMillis();
			DoublePoint exactPoint = new DoublePoint(Double.parseDouble(info[2]),
					Double.parseDouble(info[3]));
			player.character.facing = Integer.parseInt(info[4]);
			player.character.animation = Animation.values()[Integer.parseInt(info[5])];
			player.exactPoint = exactPoint;
			player.upPressed = Boolean.parseBoolean(info[6]);
			player.downPressed = Boolean.parseBoolean(info[7]);
			player.leftPressed = Boolean.parseBoolean(info[8]);
			player.rightPressed = Boolean.parseBoolean(info[9]);
			if (player.upPressed || player.downPressed || player.leftPressed
					|| player.rightPressed)
				player.moving = true;
			game.logger.log(LogLevel.DEBUG, "Player moved.");
		} else if (message.startsWith("PLAYERSTOPMOVE")) {
			game.logger.log(LogLevel.DEBUG, "Player stopped moving!");
			Player player = game.players.get(Integer.parseInt(info[1]));
			player.lastButtonPress = game.currentTimeMillis();
			DoublePoint exactPoint = new DoublePoint(Double.parseDouble(info[2]),
					Double.parseDouble(info[3]));
			player.character.facing = Integer.parseInt(info[4]);
			player.character.animation = Animation.values()[Integer.parseInt(info[5])];
			player.exactPoint = exactPoint;
			player.moving = false;
			player.upPressed = false;
			player.downPressed = false;
			player.leftPressed = false;
			player.rightPressed = false;
			game.logger.log(LogLevel.DEBUG, "Player stopped.");
		} else if (message.startsWith("SKULLINFECT")) {
			game.logger.log(LogLevel.DEBUG, "Infecting player with skull!");
			Player playerInfected = game.players.get(Integer.parseInt(info[1]));
			long skullTimer = Long.parseLong(info[2]);
			int skullType = Integer.parseInt(info[3]);
			playerInfected.skullTimer = skullTimer;
			playerInfected.skullType = skullType;
			game.sound.play("Hey");
			game.logger.log(LogLevel.DEBUG, "Player infected.");
		} else if (message.startsWith("BLOCKDESTROY")) {
			game.logger.log(LogLevel.DEBUG, "Destroying block!");
			Point cellPoint = new Point(Integer.parseInt(info[1]),
					Integer.parseInt(info[2]));
			long time = Long.parseLong(info[3]);
			Block block = Block.get(cellPoint, game);
			if (block != null) {
				block.destroyAt(time);
				game.logger.log(LogLevel.DEBUG, "Block destroyed.");
			} else {
				game.logger.log(LogLevel.WARNING,
						"Could not find block that was supposed to be destroyed!");
			}
		}
		if (weAreHost())
			server.sendMessageToAllClientsExcludingNames(message, username);
	}

	public class NetworkGraphic {
		/**
		 * Default positions for corners - x value.
		 */
		public int[] defaultX;
		/**
		 * Default positions for corners - y value.
		 */
		public int[] defaultY;
		/**
		 * Current position - x value.
		 */
		public int x;
		/**
		 * Current position - y value.
		 */
		public int y;
		/**
		 * Width of graphic.
		 */
		public final int WIDTH = 250;
		/**
		 * Height of graphic.
		 */
		public final int HEIGHT = 15;
		/**
		 * Distance from {@link #defaultX} and {@link #defaultY} that will cause
		 * position to 'snap' into place via {@link #move(int, int)}.
		 */
		public int snap = 20;
		/**
		 * 0: Open<br>
		 * 1: Closed<br>
		 * Anything above 1: time until closed.<br>
		 * Anything below 0: (negative) time until open.
		 */
		public long open = 1;
		/**
		 * The color of the outline of the graphic. By default a slightly opaque
		 * green.
		 */
		public Color outlineColor = new Color(0, 200, 0, 200);
		/**
		 * The color of the background of the graphic. By default a slightly
		 * opaque black.
		 */
		public Color backgroundColor = new Color(0, 0, 0, 200);
		/**
		 * The color of the text of the graphic. By default green.
		 */
		public Color textColor = new Color(0, 240, 0, 240);
		/**
		 * Whether or not we are currently moving the interface.
		 */
		public boolean isMoving = false;
		/**
		 * The different between the upper left of the graphic and the point
		 * that was originally clicked on, so dragging is more fluid.
		 */
		public Point movingOffset = null;
		/**
		 * The amount of time it takes the display to dock/open (in ms).
		 */
		public float openTime = 500;
		/**
		 * Size of the opened up graphic.
		 */
		public int openSize = 125;
		/**
		 * Whether our graphic is opened upwards or downwards.
		 */
		public boolean upwards = false;
		/**
		 * The last game state {@link #game} was in. Used to determine when
		 * there's a change in gamestate.
		 */
		public State lastState = null;

		public NetworkGraphic() {
			defaultX = new int[] { 10, 380, 10, 380, 10, 380, 10, 380 };
			defaultY = new int[] { 10, 10, 323, 323, 110, 110, 423, 423 };
			x = defaultX[1];
			y = defaultY[1];
		}

		private void tick(Graphics2D g) {
			if ((!game.state.is(StateEnum.MATCH_SELECTION)
					&& !game.state.is(StateEnum.GOING_TO_STAGE)
					&& !game.state.is(StateEnum.STAGE)
					&& !game.state.is(StateEnum.END_OF_MATCH))
					|| game.fadecounterenabled != 0)
				return;
			if (lastState == null || !game.state.is(lastState.get())) {
				lastState = game.state;
				if (lastState.equals(StateEnum.MATCH_SELECTION)) {
					x = defaultX[7];
					y = defaultY[7];
					upwards = true;
				} else if (lastState.equals(StateEnum.GOING_TO_STAGE)) {
					setClientsReady(false);
				}
			}
			Stroke stroke = g.getStroke();

			g.setColor(backgroundColor);
			g.fillRect(x, y, WIDTH, HEIGHT);

			g.setStroke(new BasicStroke(2f));
			g.setColor(outlineColor);
			g.drawRect(x, y, WIDTH, HEIGHT);
			g.drawLine(x + 15, y + 1, x + 15, y + 14);
			g.drawLine(x + WIDTH - 15, y + 1, x + WIDTH - 15, y + 14);
			g.setStroke(stroke);

			g.setColor(textColor);
			g.setFont(game.defaultFont.deriveFont(10.5f));
			String msg = (getConnectedClientAmt() > -1 ? getConnectedClientAmt() : 0)
					+ " Connected Players";
			int readyPlayers = 0;
			if (weAreHost()) {
				for (int i = 0; i < connectedClients.size(); i++)
					if (connectedClients.get(i)[2].equals("1"))
						readyPlayers++;
			} else {
				for (int i = 0; i < connectedClients.size(); i++)
					if (connectedClients.get(i)[3].equals("1"))
						readyPlayers++;
			}
			if (readyPlayers < connectedClients.size())
				msg = readyPlayers + " / " + connectedClients.size() + " Ready Players";
			g.drawString(msg, x + 20, y + 12);

			g.drawImage(game.pictures.networkingPopup.get(0).get(0), x + 1, y + 1, game);
			int img = 2;
			if (open == 1 || open < 0)
				img = 1;
			if (upwards)
				img = (img == 1) ? 2 : 1;
			g.drawImage(game.pictures.networkingPopup.get(0).get(img), x + WIDTH - 14,
					y + 1, game);
			float percent = 1;
			if (open < 0) {
				percent = ((float) (-open - game.currentTimeMillis()) / openTime);
				if (percent <= 0f) {
					open = 0;
					tick(g);
					return;
				}
			} else if (open == 0) {
				percent = 0f;
			} else if (open == 1) {
				percent = 10f;
			} else if (open > 1) {
				percent = 1f - ((float) (open - game.currentTimeMillis()) / openTime);
				if (percent >= 1f) {
					open = 1;
					tick(g);
					return;
				}
			}
			int tempOpenSize = new Integer(openSize);
			int openSize = new Integer(tempOpenSize);
			if (connectedClients.size() < 8)
				openSize = openSize - (8 - connectedClients.size()) * 13;
			defaultX = new int[] { 10, 380, 10, 380, 10, 380, 10, 380 };
			defaultY = new int[] { 10, 10, 323, 323, 110, 110, 423, 423 };
			for (int i = 2; i < 4 && !upwards; i++)
				defaultY[i] += openSize;
			for (int i = 4; i < 6 && upwards; i++)
				defaultY[i] -= openSize;
			if (open != 1)
				if (y + 15 + openSize >= game.getHeight() && !upwards) {
					upwards = true;
				} else if (y - openSize <= 0 && upwards) {
					upwards = false;
				}
			float dropDown = openSize - openSize * percent;
			if (percent < 10f) {
				if (upwards) {
					g.setColor(backgroundColor);
					g.fillRect(x, y - (int) dropDown, WIDTH, (int) dropDown);
					g.setColor(outlineColor);
					g.drawRect(x, y - (int) dropDown, WIDTH, (int) dropDown);
				} else {
					g.setColor(backgroundColor);
					g.fillRect(x, y + 15, WIDTH, (int) dropDown);
					g.setColor(outlineColor);
					g.drawRect(x, y + 15, WIDTH, (int) dropDown);
				}
			}
			if (open != 0)
				return;
			g.setColor(outlineColor);
			g.drawLine(x + 1, y + 14 + (upwards ? -openSize : 15), x + WIDTH - 1,
					y + 14 + (upwards ? -openSize : 15));
			g.setColor(textColor);
			g.drawString("NAME", x + 5, y + 12 + (upwards ? -openSize : 15));

			int furthestName = Integer.MIN_VALUE;
			int width = 0;
			for (int i = 0; i < connectedClients.size(); i++) {
				width = Tools.getTextWidth(g, connectedClients.get(i)[0]);
				if (width > furthestName)
					furthestName = width;
			}
			furthestName += x + 15;
			g.setColor(outlineColor);
			g.drawLine(furthestName, y + (upwards ? -openSize : 15) + 1, furthestName,
					y + 13 + (upwards ? -openSize : 15));
			furthestName += 5;
			g.setColor(textColor);
			g.drawString("LOCAL", furthestName, y + 12 + (upwards ? -openSize : 15));
			width = Tools.getTextWidth(g, "LOCAL");
			g.drawString("PING", furthestName + 46, y + 12 + (upwards ? -openSize : 15));
			furthestName += width + 5;
			g.drawString("RDY", furthestName + 42, y + 12 + (upwards ? -openSize : 15));
			g.setColor(outlineColor);
			g.drawLine(furthestName, y + (upwards ? -openSize : 15) + 1, furthestName,
					y + 13 + (upwards ? -openSize : 15));
			g.drawLine(furthestName + 35, y + (upwards ? -openSize : 15) + 1,
					furthestName + 35, y + 13 + (upwards ? -openSize : 15));
			for (int i = 0; i < connectedClients.size(); i++) {
				g.setColor(textColor);
				g.drawString(connectedClients.get(i)[0], x + 5,
						y + 13 * (i + 2) + (upwards ? -openSize : 15));
				String local = connectedClients.get(i)[1];
				g.drawString(local,
						furthestName - (width + 5) / 2 - Tools.getTextWidth(g, local) / 2,
						y + 13 * (i + 2) + (upwards ? -openSize : 15));
				if (server != null)
					local = "" + server.getClientPing(connectedClients.get(i)[0]);
				else
					local = connectedClients.get(i)[2];
				g.drawString(
						local, furthestName - (width + 5) / 2
								- Tools.getTextWidth(g, local) / 2 + 38,
						y + 13 * (i + 2) + (upwards ? -openSize : 15));
				if (server != null)
					local = connectedClients.get(i)[2];
				else
					local = connectedClients.get(i)[3];
				if (local.equals("0"))
					g.setColor(Color.RED);
				int squareX = furthestName - (width + 5) / 2 - 15 / 2 + 71;
				int squareY = y + 13 * (i + 2) + (upwards ? -openSize : 15) - 8;
				g.fillRect(squareX, squareY, 15, 8);
			}
		}

		/**
		 * Moves the graphic, assuming <b>x</b> and <b>y</b> are mouse position.
		 * 
		 * @param x
		 * @param y
		 */
		public void move(int x, int y) {
			if (!isMoving) {
				movingOffset = new Point(this.x - x, this.y - y);
				isMoving = true;
			}
			x = x + movingOffset.x;
			y = y + movingOffset.y;
			int start = 0, max = 4;
			if (upwards) {
				start = 4;
				max = defaultX.length;
			}
			for (int i = start; i < max; i++)
				if (Math.abs(defaultX[i] - x) <= snap
						&& Math.abs(defaultY[i] - y) <= snap) {
					x = defaultX[i];
					y = defaultY[i];
				}
			if (x + WIDTH >= game.getWidth())
				x = game.getWidth() - WIDTH;
			if (x <= 0)
				x = 0;
			if (y + HEIGHT >= game.getHeight())
				y = game.getHeight() - HEIGHT;
			if (y <= 0)
				y = 0;
			this.x = x;
			this.y = y;
		}

		public boolean open() {
			if (open != 1)
				return false;
			open = -(game.currentTimeMillis() + (long) openTime);
			return true;
		}

		public boolean close() {
			if (open != 0)
				return false;
			open = game.currentTimeMillis() + (long) openTime;
			return true;
		}

		/**
		 * Sets {@link #isMoving} to <b>false</b>, so we no longer track the
		 * mouse.
		 */
		public void stopMoving() {
			isMoving = false;
		}
	}

	public enum NetworkEventType {
		BOMBKICK, BOMBTHROW, BOMBBOUNCEHEAD, BOMBBOUNCESTOP;
	}

	private ArrayList<NetworkEvent> waitingNetworkEvents = new ArrayList<NetworkEvent>();

	private class NetworkEvent {
		NetworkEventType type = null;
		long timeToRunAt = -1;
		String[] info = null;

		public NetworkEvent(NetworkEventType type, long timeToRunAt, String[] info) {
			this.type = type;
			this.timeToRunAt = timeToRunAt;
			this.info = info;
		}

		public boolean runIfPossible() {
			if (game.currentTimeMillis() < timeToRunAt)
				return false;
			if (type.equals(NetworkEventType.BOMBKICK)) {
				game.logger.log(LogLevel.DEBUG, "Bomb kick!");
				long identifier = Long.parseLong(info[1]);
				int direction = Integer.parseInt(info[2]);
				DoublePoint exactPoint = new DoublePoint(Double.parseDouble(info[3]),
						Double.parseDouble(info[4]));
				Player player = game.players.get(Integer.parseInt(info[5]));
				Bomb bomb = talking.getBomb(identifier);
				if (bomb != null) {
					game.logger.log(LogLevel.DEBUG, "Kicking bomb!");
					game.sound.play("Bomb GO");
					bomb.exactPoint = exactPoint;
					bomb.moving = direction;
					player.lastKickedBomb = bomb;
					player.lastKickedBombTime = game.currentTimeMillis();
					bomb.playerKicked = player;
				} else
					game.logger.log(LogLevel.ERROR, "Could not find kicked bomb!");
			} else if (type.equals(NetworkEventType.BOMBTHROW)) {
				game.logger.log(LogLevel.DEBUG, "Someone is throwing a bomb!");
				DoublePoint exactPoint = new DoublePoint(Double.parseDouble(info[1]),
						Double.parseDouble(info[2]));
				int facing = Integer.parseInt(info[3]);
				Player player = game.players.get(Integer.parseInt(info[4]));
				player.exactPoint = exactPoint;
				player.character.facing = facing;
				if (player.pickedUpBomb != null) {
					game.logger.log(LogLevel.DEBUG, "Bomb thrown!");
					player.pickedUpBomb.pickedUpCount = 3 + (player.character.facing * 5);
					player.pickedUpBomb.cellPoint = player.getCellPoint();
					DoublePoint p = new DoublePoint(
							Stage.getExactTileMidPoint(game, player.getCellPoint()));
					player.pickedUpBomb.exactPoint = new DoublePoint(
							p.x + player.pickedUpBomb.adjustExactPoint.x,
							p.y + player.pickedUpBomb.adjustExactPoint.y);
					game.objects.add(player.pickedUpBomb);
					player.pickedUpBomb = null;
					if (player.character.animation != Animation.STUNNED_SPINNING) {
						game.sound.play("Bomb GO");
						player.character.animation = Animation.HOLDING_BOMB_THROWING;
						player.character.animationTick = 0;
					}
				} else
					game.logger.log(LogLevel.ERROR,
							"Could not find picked up bomb to throw!");
			} else if (type.equals(NetworkEventType.BOMBBOUNCEHEAD)) {
				game.logger.log(LogLevel.DEBUG, "Bomb bounced on someone's head!");
				Player player = game.players.get(Integer.parseInt(info[1]));
				long playerLastButtonPress = Long.parseLong(info[2]);
				game.sound.play("Ouch");
				player.lastButtonPress = playerLastButtonPress;
				player.character.animation = Animation.STUNNED_SPINNING;
				player.character.animationTick = 0;
				game.setSeed(game.seed, Integer.parseInt(info[3]));
				player.placeRandomItemToBoard();
				game.logger.log(LogLevel.DEBUG, "Bounced on their head!");
			} else if (type.equals(NetworkEventType.BOMBBOUNCESTOP)) {
				game.logger.log(LogLevel.DEBUG, "Bomb stopped bouncing!");
				long identifier = Long.parseLong(info[1]);
				Point cellPoint = new Point(Integer.parseInt(info[2]),
						Integer.parseInt(info[3]));
				long explodeTimeAdd = Long.parseLong(info[4]);
				Bomb bomb = talking.getBomb(identifier);
				if (bomb != null) {
					game.logger.log(LogLevel.DEBUG,
							"Placing down bouncing bomb for explosion!");
					bomb.moving = -1;
					if (bomb.explodeTime < 50000)
						bomb.explodeTime += explodeTimeAdd;
					bomb.pickedUpCount = -1;
					bomb.cellPoint = cellPoint;
					DoublePoint p = new DoublePoint(
							Stage.getExactTileMidPoint(game, cellPoint));
					bomb.exactPoint = new DoublePoint(p.x + bomb.adjustExactPoint.x,
							p.y + bomb.adjustExactPoint.y);
				} else
					game.logger.log(LogLevel.ERROR, "Could not find bouncing bomb!");
			}
			return true;
		}

	}

	public void addNetworkEvent(NetworkEventType type, long timeToRunAt, String[] info) {
		waitingNetworkEvents.add(new NetworkEvent(type, timeToRunAt, info));
	}

	private void runNetworkEvents() {
		for (int i = 0; i < waitingNetworkEvents.size(); i++)
			if (waitingNetworkEvents.get(i).runIfPossible()) {
				waitingNetworkEvents.remove(i);
				i--;
			}
	}
}
