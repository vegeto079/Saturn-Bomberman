package com.github.vegeto079.saturnbomberman.networking;

import java.awt.Color;
import java.awt.Graphics2D;
import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import com.github.vegeto079.ngcommontools.main.Game;
import com.github.vegeto079.ngcommontools.main.Logger;
import com.github.vegeto079.ngcommontools.main.Logger.LogLevel;
import com.github.vegeto079.ngcommontools.main.Tools;
import com.github.vegeto079.ngcommontools.networking.Client;
import com.github.vegeto079.ngcommontools.networking.Client.ClientMessageHandler;
import com.github.vegeto079.ngcommontools.networking.Server;
import com.github.vegeto079.ngcommontools.networking.Server.Handler;
import com.github.vegeto079.ngcommontools.networking.Server.ServerMessageHandler;

/**
 * Master Server for Bomberman. All players will firstly (attempt to) connect to
 * this {@link Server} when playing multiplayer. It will act sort of like
 * Gamespy in a way, but not actually handle connections between players itself.
 * <br>
 * The goal of this is to have an easy-to-connect interface between
 * {@link Client}s.
 * 
 * @author Nathan
 * @version 1.0: Started tracking version.
 * @version 1.01: Added {@link #maxLogs} and {@link #HOSTING_TIMEOUT}.
 * @version 1.1: Added {@link #testingConnectionWith} instead of just a boolean
 *          to determine who we are trying to connect with. Now has a few
 *          catches to make sure we don't get stuck with this enabled.
 * @see {@link MasterServerConnector}
 * 
 */
public class MasterServer extends Game {
	private static final long serialVersionUID = 1;

	public MasterServer(Logger logger, String[] args, int ticksPerSecond, int paintTicksPerSecond, String title,
			int width, int height) {
		super(logger, args, ticksPerSecond, paintTicksPerSecond, title, width, height);
		if (port == -1)
			try {
				port = Integer.parseInt(Tools.readResourceFile(MasterServer.class, "/ip/serverIP.txt").get(0));
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
	}

	private static MasterServer me = null;
	private MSServer server = null;
	private Client client = null;
	private int port = -1;

	private MSLogger logger = null;
	private ArrayList<String> logs = new ArrayList<String>();

	private ArrayList<String> clientNames = new ArrayList<String>();
	private ArrayList<Boolean> clientCanP2P = new ArrayList<Boolean>();
	private ArrayList<Long> gameAvailable = new ArrayList<Long>();
	private ArrayList<Integer> connectedClients = new ArrayList<Integer>();
	private ArrayList<Boolean> p2pRequired = new ArrayList<Boolean>();
	private ArrayList<Integer> maxClients = new ArrayList<Integer>();

	private String testingConnectionWith = null;
	private int portToTry = -1;
	private String connectMsg = "USING:";

	private static boolean showDisplay = true;
	private int maxLogs = 100;

	public static final long HOSTING_TIMEOUT = 5000;

	public static void main(String[] args) {
		// if (!ResourceHandler.runningFromJar())
		// args = new String[] { "NO_DISPLAY" };
		Logger newLogger = new Logger(true);
		for (int i = 0; i < args.length; i++)
			if (args[i].equals("NO_DISPLAY"))
				showDisplay = false;
		me = new MasterServer(newLogger, args, 1, 1, "Master Server Console", showDisplay ? 800 : 0,
				showDisplay ? 300 : 0);
		// me.dontResize = true;
		me.startThread();
	}

	public void firstLoad() {
		logger = new MSLogger(super.logger);
		super.logger = logger;
		server = new MSServer(new MSServerMessageHandler(), logger, port, 1000, 7500, -1, "Grand Master Server");
		server.openIncomingClientConnection();
	}

	public void gameTick() {
		if (!connectMsg.contains("USING:")) {
			String message = new String(connectMsg);
			connectMsg = "USING:" + connectMsg;
			String username = message.split(Server.USERNAME_SPLITTER)[1];
			client = new Client(new MSClientMessageHandler(), logger, 250, "Master Server Client Connection Test");
			try {
				logger.log(LogLevel.WARNING,
						"Starting Client connection to (" + server.getHandler(server.getHandlerIndex(username)).getIP()
								+ ":" + portToTry + "," + server.getHandlerIndex(username) + "," + username + ")");
			} catch (Exception e) {
				logger.log(LogLevel.WARNING, "Had an issue connecting to a client...");
				client.disconnect();
				connectMsg = null;
				return;
			}
			client.connectToServer(server.getHandler(server.getHandlerIndex(username)).getIP(), portToTry, 2);
		} else if (client != null) {
			if (client.isConnecting()) {
				logger.log(LogLevel.DEBUG, "wait");
			} else if (client.isConnected()) {
				logger.log(LogLevel.DEBUG, "connected ok");
			} else {
				logger.log(LogLevel.DEBUG, "didn't connect");
			}
		}
		if (server != null && server.getConnectedClientAmt() > 0) {
			ArrayList<Handler> handlers = server.getHandlers();
			for (int i = 0; i < handlers.size(); i++) {
				Handler handler = handlers.get(i);
				if (handler.hasMessageQueued())
					continue;
				String message = "MASTER-SERVER-PLAYERS:" + server.getConnectedClientAmt();
				for (int j = 0; j < clientNames.size(); j++)
					try {
						if (!handler.getTheirName().equals(clientNames.get(j))) {
							String ip = server.getHandler(server.getHandlerIndex(clientNames.get(j))).getIP();
							if (ip.equals(handler.getIP()))
								ip = "localhost";
							message += ":" + clientNames.get(j) + Client.SPLITTER + ip + Client.SPLITTER
									+ p2pRequired.get(j) + Client.SPLITTER
									+ (System.currentTimeMillis() - gameAvailable.get(j) < HOSTING_TIMEOUT)
									+ Client.SPLITTER + connectedClients.get(j) + Client.SPLITTER + maxClients.get(j);
						}
					} catch (Exception e) {
						return;
					}
				server.sendMessageToClient(message, handler);
			}
		}
	}

	public void paintTick(Graphics2D g) {
		maxLogs = getHeight() / Tools.getTextHeight(g, "XXX");
		g.setColor(Color.WHITE);
		int textHeight = Tools.getTextHeight(g, "XXX");
		for (int i = 0; i < logs.size(); i++)
			g.drawString(logs.get(i), 0, textHeight + i * textHeight - 5);
		if (server != null) {
			int clientsConnected = server.getConnectedClientAmt();
			g.setColor(Color.GREEN);
			String clientsConnectedText = "Clients Connected: " + clientsConnected;
			int y = -20;
			g.drawString(clientsConnectedText, getWidth() - Tools.getTextWidth(g, clientsConnectedText),
					textHeight + (y += 15));
			if (server.isConnected())
				for (int i = 0; i < clientNames.size(); i++) {
					String clientText = "Client #" + i + ": " + clientNames.get(i) + ", P2P: " + clientCanP2P.get(i)
							+ ", Game: " + (System.currentTimeMillis() - gameAvailable.get(i) < HOSTING_TIMEOUT)
							+ ", Connected: " + connectedClients.get(i) + ", Max: " + maxClients.get(i);
					g.drawString(clientText, getWidth() - Tools.getTextWidth(g, clientText), textHeight + (y += 15));
				}
			if (testingConnectionWith != null) {
				String testingConnectionTest = "Testing NAT with: " + testingConnectionWith;
				g.drawString(testingConnectionTest, getWidth() - Tools.getTextWidth(g, testingConnectionTest),
						textHeight + (y += 15));
			}
		}
	}

	public class MSClientMessageHandler extends ClientMessageHandler {

		public void process(String message) {

		}

	}

	public class MSServerMessageHandler extends ServerMessageHandler {

		public void process(String message) {
			logger.log(LogLevel.DEBUG, "Got raw message (MSMessageHandler): " + message);
			if (message.contains("Hey, can you test our P2P compatibility please? Thanks.")) {
				String username = message.split(Server.USERNAME_SPLITTER)[1];
				int newPortToTry = port
						- Integer.parseInt(message.split(Server.USERNAME_SPLITTER)[0].split("Thanks.")[1]);
				if (testingConnectionWith == username) {
					logger.log(LogLevel.DEBUG, "Something screwy is going on, they're asking to connect again?");
					while (server.getHandlerIndex(username) != -1)
						server.getHandler(server.getHandlerIndex(username)).end();
					testingConnectionWith = null;
					return;
				}
				if (testingConnectionWith != null) {
					if (server.getHandlerIndex(testingConnectionWith) == -1)
						testingConnectionWith = null;
					else {
						server.sendMessageToClient("We're already testing! Hold on.", username);
						return;
					}
				}
				portToTry = newPortToTry;
				testingConnectionWith = username;
				logger.log(LogLevel.NORMAL, "Received P2P compatibility connection request. (" + username + ")");
				server.sendMessageToClient("Ok, we're ready for ya.", username);
				connectMsg = message;
			} else if (message.contains("P2P connection! Done with testing.")) {
				logger.log(LogLevel.NORMAL, "They're done with testing, closing everything up.");
				String username = message.split(Server.USERNAME_SPLITTER)[1];
				boolean p2p = false;
				if (message.contains("NOT")) {
					logger.log(LogLevel.DEBUG, "They can NOT do P2P.");
				} else {
					logger.log(LogLevel.DEBUG, "They CAN do P2P.");
					p2p = true;
				}
				clientNames.add(username);
				clientCanP2P.add(p2p);
				gameAvailable.add(0l);
				connectedClients.add(-1);
				p2pRequired.add(false);
				maxClients.add(-1);
				client.disconnect();
				client = null;
				connectMsg = "USING:";
				testingConnectionWith = null;
				logger.log(LogLevel.WARNING, "Finished adding new Client to Master Server list.");
			} else if (message.contains("We have a game up!")) {
				String username = message.split(Server.USERNAME_SPLITTER)[1];
				for (int i = 0; i < clientNames.size(); i++)
					if (clientNames.get(i).equals(username)) {
						gameAvailable.set(i, System.currentTimeMillis());
						p2pRequired.set(i, Boolean.parseBoolean(message.split("!")[1]));
						connectedClients.set(i, Integer.parseInt(message.split("!")[2]));
						maxClients.set(i, Integer.parseInt(message.split("!")[3]));
					}
			}
		}
	}

	public class MSLogger extends Logger {
		public MSLogger(Logger loggerToClone) {
			super(loggerToClone.getLevelsShown()[0], loggerToClone.getLevelsShown()[1],
					loggerToClone.getLevelsShown()[2], loggerToClone.getLevelsShown()[3]);
		}

		public void log(LogLevel logLevel, String... strings) {
			super.log(logLevel, strings);
			if (!super.getLevelsShown()[LogLevel.valueOf(logLevel.name()).ordinal()])
				return;
			for (String string : strings) {
				SimpleDateFormat sdf = new SimpleDateFormat("(hh:mm:ss) ");
				Date date = new Date();
				logs.add(sdf.format(date) + string);
				if (logs.size() > maxLogs)
					logs.remove(0);
			}
		}
	}

	public class MSServer extends Server {

		public MSServer(ServerMessageHandler messageHandler, Logger logger, int port,
				long timeBetweenConnectionAttempts, long clientTimeout, long seed, String username) {
			super(messageHandler, logger, port, timeBetweenConnectionAttempts, clientTimeout, seed, username);
		}

		@Override
		public void onExit(Handler handler, int index) {
			String name = handler.getTheirName();
			logger.log(LogLevel.DEBUG, "exiting name: " + name);
			for (int i = 0; i < clientNames.size(); i++) {
				if (clientNames.get(i).equals(name)) {
					logger.log(LogLevel.DEBUG, "Found and killed client");
					clientNames.remove(i);
					clientCanP2P.remove(i);
					if (name.equals(testingConnectionWith))
						testingConnectionWith = null;
					break;
				}
			}
		}

	}
}
