package com.github.vegeto079.saturnbomberman.networking;

import java.io.FileNotFoundException;
import java.util.ArrayList;

import com.github.vegeto079.ngcommontools.main.Logger;
import com.github.vegeto079.ngcommontools.main.Logger.LogLevel;
import com.github.vegeto079.ngcommontools.main.ResourceHandler;
import com.github.vegeto079.ngcommontools.main.Tools;
import com.github.vegeto079.ngcommontools.networking.Client;
import com.github.vegeto079.ngcommontools.networking.Client.ClientMessageHandler;
import com.github.vegeto079.ngcommontools.networking.Server;
import com.github.vegeto079.ngcommontools.networking.Server.ServerMessageHandler;

/**
 * Class used to connect to {@link MasterServer}.
 * 
 * @author Nathan
 * @version 1.0: Started tracking version.
 * @version 1.1: If the <b>MasterServer</b> is already connecting, instead of
 *          cancelling and saying we couldn't connect, we retry after waiting.
 *          Added {@link #connectingMessage} to show a useful message to the
 *          end-user.
 * 
 */
public class MasterServerConnector {
	/**
	 * IP Address used to connect to the {@link MasterServer}.<br>
	 * If <b>null</b>, it will be grabbed from /ip/serverIP.txt
	 */
	// public String ip = "localhost";
	public String ip = null;
	/**
	 * Port used to connect to the {@link MasterServer}. <br>
	 * If <b>-1</b>, it will be grabbed from /ip/serverPort.txt
	 */
	private int port = -1;
	/**
	 * Client used to connect to the {@link MasterServer}.
	 */
	private mscClient client = null;
	/**
	 * Servers used to connect to the {@link MasterServer}, testing {@link Client}'s
	 * ability to be part of a P2P Network.
	 */
	private Server server = null;
	/**
	 * Whether or not we are currently connecting to the {@link MasterServer}. False
	 * if {@link #run(String, Logger, String)} is not currently running.
	 */
	public boolean connecting = false;
	/**
	 * Whether or not we have successfully connected to the {@link MasterServer} .
	 */
	public boolean connected = false;
	/**
	 * Whether or not we can run a P2P Network. If {@link #connected} is
	 * <b>false</b>, this variable is not indicative of actual ability.
	 */
	public boolean canRunP2P = false;
	/**
	 * Whether or not we are currently hosting a {@link Server} that {@link Client}s
	 * can connect to. Set this to the amount of connected clients if you want the
	 * {@link MasterServer} to know we have a server running.
	 */
	public int connectedClients = -1;
	/**
	 * The maximum amount of {@link Client}s the {@link Server} will accept. Tells
	 * the {@link MasterServer} so they can tell all <b>Client</b>s this
	 * information.
	 */
	public int maxClients = -1;
	public boolean useP2P = false;
	/**
	 * The last time we told the {@link MasterServer} that we are hosting. Must stay
	 * under {@link MasterServer#HOSTING_TIMEOUT} to prevent the Master Server from
	 * thinking we are no longer hosting.
	 */
	public long toldServerHosting = System.currentTimeMillis();
	/**
	 * Information regarding the {@link Client}s connected to the
	 * {@link MasterServer}, besides ours.
	 */
	public ArrayList<String[]> masterServerClientInfo = null;

	static MasterServerConnector me = null;

	/**
	 * Last message received from Server.
	 */
	private String serverMessage = "";

	/**
	 * Message to display to user when connecting.
	 */
	public String connectingMessage = "";

	public static void main(String[] args) {
		// Temporary to test usage
		me = new MasterServerConnector();
		String ip = me.ip;
		if (ResourceHandler.runningFromJar())
			Tools.displayInputDialog("Please enter IP address of Master Server", "Default is " + me.ip);
		me.run(ip, new Logger(true), "Tester");
	}

	public MasterServerConnector() {
		if (ip == null)
			try {
				ip = Tools.readResourceFile("/ip/serverIP.txt").get(0);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
		if (port == -1)
			try {
				port = Integer.parseInt(Tools.readResourceFile("/ip/serverPort.txt").get(0) + 1);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			}
	}

	/**
	 * Connects to the {@link MasterServer} and gets this MasterServerConnector set
	 * up through various handshakes. This method may take several seconds to fully
	 * complete, and has sleeping involved. May re-write this so it's in it's own
	 * thread.
	 * 
	 * @param ip
	 *            IP address of the Master Server.
	 * @param logger
	 *            Logger to pass to {@link #client} and {@link #server}.
	 * @param username
	 *            Username used to identify ourselves to the Master Server.
	 * @return Whether or not we have successfully connected to the
	 *         <b>MasterServer</b>.
	 */
	public boolean run(String ip, Logger logger, String username) {
		if (ip == null)
			return false;
		connectingMessage = "Starting Connection...";
		connecting = true;
		client = new mscClient(new mscClientMessageHandler(), logger, 1000, username);
		client.connectToServer(ip, port, 2);
		logger.log(LogLevel.WARNING, "Attempting to connect to Master Server..");
		long start = System.currentTimeMillis();
		while (client.isConnecting() && System.currentTimeMillis() - start < 5000) {
			try {
				Thread.sleep(100);
			} catch (Exception e) {
			}
		}
		if (System.currentTimeMillis() - start >= 5000) {
			// Took longer than 5 seconds to connect?
			client.disconnect();
			return false;
		}
		if (client.isConnected()) {
			logger.log(LogLevel.DEBUG, "Connected to Master Server!");
			int randomPort = Tools.random(null, 2, 10, null);
			String message = "Hey, can you test our P2P compatibility please? Thanks." + randomPort;
			randomPort = port - randomPort;
			client.sendMessageToServer(message);
			logger.log(LogLevel.DEBUG, "Sending message to start P2P compatibility test.. (" + message + ")");
			start = System.currentTimeMillis();
			connectingMessage = "Initiating NAT tests...";
			while (client.hasMessageQueued(message) && System.currentTimeMillis() - start < 5000) {
				try {
					Thread.sleep(100);
				} catch (Exception e) {
				}
			}
			if (!client.hasMessageQueued(message)) {
				logger.log(LogLevel.DEBUG, "Sent message successfully. Waiting for response..");
				start = System.currentTimeMillis();
				connectingMessage = "Waiting for server...";
				while (System.currentTimeMillis() - start < 5000) {
					if (serverMessage.contains("ready for ya")) {
						logger.log(LogLevel.DEBUG, "Got the clear to go from the Master Server!");
						break;
					} else if (serverMessage.contains("already testing")) {
						logger.log(LogLevel.ERROR, "Master Server is already testing, trying again in 2 seconds.");
						connectingMessage = "Retrying...";
						client.disconnect();
						try {
							Thread.sleep(2000);
						} catch (Exception e) {
						}
						return run(ip, logger, username);
					}
					try {
						Thread.sleep(10);
					} catch (Exception e) {
					}
				}
				if (System.currentTimeMillis() - start < 5000) {
					logger.log(LogLevel.DEBUG,
							"Ready to go! Going to set up our Server now (port:" + randomPort + ").");
					server = new Server(new mscServerMessageHandler(), logger, randomPort, 100, 5000, -1, username);
					server.openIncomingClientConnection();
					start = System.currentTimeMillis();
					logger.log(LogLevel.DEBUG, "Waiting for server connection (5s tops)");
					connectingMessage = "Waiting for NAT response...";
					while (!server.isConnected() && System.currentTimeMillis() - start < 5000) {
						try {
							Thread.sleep(100);
						} catch (Exception e) {
						}
					}
					connectingMessage = "Almost done...";
					if (server.isConnected()) {
						logger.log(LogLevel.DEBUG,
								"We connected to the Master Server as a Server successfully (we can run a P2P connection).");
						canRunP2P = true;
						message = "We can run a P2P connection! Done with testing.";
						client.sendMessageToServer(message);
						while (client.hasMessageQueued(message) && System.currentTimeMillis() - start < 5000) {
							try {
								Thread.sleep(100);
							} catch (Exception e) {
							}
						}
						if (!client.hasMessageQueued(message)) {
							logger.log(LogLevel.DEBUG, "Told Master Server we're done testing!");
						} else {
							logger.log(LogLevel.WARNING,
									"For some reason we couldn't tell the Master Server we are done testing :( that sucks.");
						}
					} else {
						logger.log(LogLevel.DEBUG,
								"Could not connect to Master Server as a Server (we are not eligible for P2P connections).");
						canRunP2P = false;
						message = "We can NOT run a P2P connection! Done with testing.";
						client.sendMessageToServer(message);
						while (client.hasMessageQueued(message) && System.currentTimeMillis() - start < 5000) {
							try {
								Thread.sleep(100);
							} catch (Exception e) {
							}
						}
						if (!client.hasMessageQueued(message)) {
							logger.log(LogLevel.DEBUG, "Told Master Server we're done testing!");
						} else {
							logger.log(LogLevel.WARNING,
									"For some reason we couldn't tell the Master Server we are done testing :( that sucks.");
						}
					}
					server.disconnect();
					logger.log(LogLevel.WARNING, "Connection to Master Server completed.");
					connecting = false;
					connected = true;
					return true;
				} else {
					logger.log(LogLevel.ERROR, "Could not get confirmation message from Master Server.");
				}
			} else {
				logger.log(LogLevel.ERROR, "Could not send message to Master Server.");
			}
		} else {
			logger.log(LogLevel.ERROR, "Could not connect to Master Server.");
		}
		client.disconnect();
		return false;
	}

	public int getMasterServerConnectedClientAmt() {
		return masterServerClientInfo.size();
	}

	public ArrayList<String[]> getMasterServerClientInfo() {
		return masterServerClientInfo;
	}

	/**
	 * Disconnects from the {@link MasterServer}.
	 */
	public void disconnect() {
		if (client != null && !client.disconnecting) {
			client.disconnect();
			client = null;
		}
		if (server != null) {
			server.disconnect();
			server = null;
		}
		connectedClients = -1;
		connected = false;
		connecting = false;
		masterServerClientInfo = null;
	}

	public class mscClientMessageHandler extends ClientMessageHandler {

		public void process(String message) {
			serverMessage = message.split(Client.USERNAME_SPLITTER)[0];
			if (serverMessage.contains(Client.SPLITTER)) {
				ArrayList<String[]> clientInfo = new ArrayList<String[]>();
				String[] rawInfo = serverMessage.split(":");
				for (int i = 2; i < rawInfo.length; i++) {
					clientInfo.add(rawInfo[i].split(Client.SPLITTER));
				}
				masterServerClientInfo = clientInfo;
			} else if (serverMessage.contains("MASTER-SERVER-PLAYERS"))
				masterServerClientInfo = new ArrayList<String[]>();
			if (connectedClients > -1
					&& System.currentTimeMillis() - toldServerHosting > MasterServer.HOSTING_TIMEOUT / 2
					&& !client.containsMessageQueued("We have a game up!" + connectedClients)) {
				client.sendMessageToServer(
						"We have a game up!" + useP2P + "!" + connectedClients + "!" + maxClients + "!");
				toldServerHosting = System.currentTimeMillis();
			}
		}

	}

	public class mscServerMessageHandler extends ServerMessageHandler {

		public void process(String message) {

		}

	}

	public class mscClient extends Client {
		boolean disconnecting = false;

		public mscClient(ClientMessageHandler messageHandler, Logger logger, long timeBetweenConnectionAttempts,
				String username) {
			super(messageHandler, logger, timeBetweenConnectionAttempts, username);
		}

		public void onExit() {
			logger.log("EXITING, NO MASTER SERVER FOUND!");
			disconnecting = true;
			MasterServerConnector.this.disconnect();
			// System.exit(1);
		}

	}
}
