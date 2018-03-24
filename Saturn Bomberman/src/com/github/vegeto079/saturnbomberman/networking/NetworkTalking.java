package com.github.vegeto079.saturnbomberman.networking;

import java.awt.Point;
import java.util.ArrayList;

import com.github.vegeto079.ngcommontools.networking.Client;
import com.github.vegeto079.ngcommontools.networking.Server.Handler;
import com.github.vegeto079.saturnbomberman.main.MainBomberman;
import com.github.vegeto079.saturnbomberman.networking.Network.NetworkEventType;
import com.github.vegeto079.saturnbomberman.objects.Bomb;
import com.github.vegeto079.saturnbomberman.objects.Bomb.BombType;
import com.github.vegeto079.saturnbomberman.objects.Item;
import com.github.vegeto079.saturnbomberman.objects.Player;


/**
 * Handles communication between Client and Server, specifically, the sending of
 * messages in a easier-to-understand manner.
 * 
 * @author Nathan
 * @version 1.0: Started tracking version.
 * @version 1.01: Added time to kick to {@link #bombKick(Bomb, Player, int)}.
 * @version 1.02: Changed all {@link System#currentTimeMillis()} references to
 *          {@link MainBomberman#currentTimeMillis()}.
 */
public class NetworkTalking {
	public MainBomberman game;

	public NetworkTalking(MainBomberman game) {
		this.game = game;
	}

	public boolean itemDestroy(Item item, Player playerOrigin, Player playerKicked) {
		if (game.network.isPlayingOnline()) {
			int playerIndex = playerOrigin.getIndex();
			if (playerKicked != null)
				playerIndex = playerKicked.getIndex();
			String itemString = "ITEMDESTROY" + Client.SPLITTER + item.cellPoint.x
					+ Client.SPLITTER + item.cellPoint.y + Client.SPLITTER
					+ item.type.ordinal();
			return sendString(itemString, playerIndex);
		}
		return false;
	}

	public boolean jellyBombKicked(Bomb bomb, Player playerOrigin, Player playerKicked,
			int randomDirection) {
		if (game.network.isPlayingOnline()) {
			int playerIndex = playerOrigin.getIndex();
			if (playerKicked != null)
				playerIndex = playerKicked.getIndex();
			String bombString = "BOMBJELLYKICKED" + Client.SPLITTER + bomb.identifier
					+ Client.SPLITTER + bomb.exactPoint.x + Client.SPLITTER
					+ bomb.exactPoint.y + Client.SPLITTER + game.seed + Client.SPLITTER
					+ game.randomCount + Client.SPLITTER + randomDirection;
			return sendString(bombString, playerIndex);
		}
		return false;
	}

	public boolean jellyBombBounce(Bomb bomb, int randomDirection) {
		if (game.network.isPlayingOnline()) {
			String bombString = "BOMBJELLYBOUNCE" + Client.SPLITTER + bomb.identifier
					+ Client.SPLITTER + bomb.exactPoint.x + Client.SPLITTER
					+ bomb.exactPoint.y + Client.SPLITTER + game.seed + Client.SPLITTER
					+ game.randomCount + Client.SPLITTER + randomDirection;
			return sendString(bombString, -1);
		}
		return false;
	}

	public boolean bombMovingStop(Bomb bomb, Player playerOrigin, Player playerKicked) {
		if (game.network.isPlayingOnline()) {
			int playerIndex = -1;
			if (playerKicked != null)
				playerIndex = playerKicked.getIndex();
			playerOrigin.getIndex();
			String bombString = "BOMBMOVINGSTOP" + Client.SPLITTER + bomb.identifier
					+ Client.SPLITTER + bomb.cellPoint.x + Client.SPLITTER
					+ bomb.cellPoint.y + Client.SPLITTER + bomb.exactPoint.x
					+ Client.SPLITTER + bomb.exactPoint.y;
			return sendString(bombString, playerIndex);
		}
		return false;
	}

	public boolean bombBounceHead(Bomb bomb, Player playerOrigin, Player playerHit) {
		boolean ourBounce = false;
		if (game.network.isPlayingOnline()) {
			long buffer = game.network.getPingBuffer() + game.currentTimeMillis();
			int playerIndex = playerOrigin.getIndex();
			String bounceString = "BOMBBOUNCEHEAD" + Client.SPLITTER
					+ playerHit.getIndex() + Client.SPLITTER + game.currentTimeMillis()
					+ Client.SPLITTER + game.randomCount + Client.SPLITTER + buffer;
			ourBounce = sendString(bounceString, playerIndex);
			if (ourBounce)
				game.network.addNetworkEvent(NetworkEventType.BOMBBOUNCEHEAD, buffer,
						bounceString.split(Client.SPLITTER));
			if (playerHit.pickedUpBomb != null)
				bombThrow(playerHit.pickedUpBomb, playerHit);
		}
		return ourBounce;
	}

	public boolean bombBounceStop(Bomb bomb, Player playerOrigin) {
		boolean ourBounce = false;
		if (game.network.isPlayingOnline()) {
			long buffer = game.network.getPingBuffer() + game.currentTimeMillis();
			String bombString = "BOMBBOUNCESTOP" + Client.SPLITTER + bomb.identifier
					+ Client.SPLITTER + bomb.cellPoint.x + Client.SPLITTER
					+ bomb.cellPoint.y + Client.SPLITTER + game.currentTimeMillis()
					+ Client.SPLITTER + buffer;
			// This code only runs if we are the host, so force send to everyone
			// instead of checking if it's our bomb
			ourBounce = sendString(bombString, -1);
			game.network.addNetworkEvent(NetworkEventType.BOMBBOUNCESTOP, buffer,
					bombString.split(Client.SPLITTER));
		}
		return ourBounce;
	}

	public boolean bombPickUp(Bomb bomb, Player player, long currentTime) {
		if (game.network.isPlayingOnline()) {
			int playerIndex = player.getIndex();
			String bombString = "BOMBPICKUP" + Client.SPLITTER + bomb.identifier
					+ Client.SPLITTER + playerIndex + Client.SPLITTER + currentTime;
			return sendString(bombString, playerIndex);
		}
		return false;
	}

	public boolean bombThrow(Bomb bomb, Player player) {
		if (game.network.isPlayingOnline()) {
			int playerIndex = player.getIndex();
			long buffer = game.network.getPingBuffer() + game.currentTimeMillis();
			String bombString = "BOMBTHROW" + Client.SPLITTER + player.exactPoint.x
					+ Client.SPLITTER + player.exactPoint.y + Client.SPLITTER
					+ player.character.facing + Client.SPLITTER + playerIndex
					+ Client.SPLITTER + buffer;
			boolean itsOurs = sendString(bombString, playerIndex);
			if (itsOurs)
				game.network.addNetworkEvent(NetworkEventType.BOMBTHROW, buffer,
						bombString.split(Client.SPLITTER));
			return itsOurs;
		}
		return false;
	}

	public boolean bombKick(Bomb bomb, Player player, int direction) {
		if (game.network.isPlayingOnline()) {
			int playerIndex = player.getIndex();
			long buffer = game.network.getPingBuffer() + game.currentTimeMillis();
			String bombString = "BOMBKICK" + Client.SPLITTER + bomb.identifier
					+ Client.SPLITTER + direction + Client.SPLITTER + bomb.exactPoint.x
					+ Client.SPLITTER + bomb.exactPoint.y + Client.SPLITTER + playerIndex
					+ Client.SPLITTER + buffer;
			boolean itsOurs = sendString(bombString, playerIndex);
			if (itsOurs)
				game.network.addNetworkEvent(NetworkEventType.BOMBKICK, buffer,
						bombString.split(Client.SPLITTER));
			return itsOurs;
		}
		return false;
	}

	/**
	 * For Players laying bombs.
	 */
	public boolean layBomb(Player player, Point cellPoint, boolean fromLineBomb,
			long time, long identifier, BombType type) {
		if (game.network.isPlayingOnline()) {
			int playerIndex = player.getIndex();
			String bombString = "BOMBPLACED" + Client.SPLITTER + playerIndex
					+ Client.SPLITTER + cellPoint.x + Client.SPLITTER + cellPoint.y
					+ Client.SPLITTER + fromLineBomb + Client.SPLITTER + time
					+ Client.SPLITTER + identifier + Client.SPLITTER + type.ordinal();
			return sendString(bombString, playerIndex);
		}
		return false;
	}

	public boolean moveLocation(Player player, double locX, double locY,
			int characterFacing, boolean upPressed, boolean downPressed,
			boolean leftPressed, boolean rightPressed) {
		if (game.network.isPlayingOnline()) {
			int playerIndex = player.getIndex();
			String moveString = "PLAYERMOVE" + Client.SPLITTER + playerIndex
					+ Client.SPLITTER + locX + Client.SPLITTER + locY + Client.SPLITTER
					+ characterFacing + Client.SPLITTER
					+ player.character.animation.ordinal() + Client.SPLITTER + upPressed
					+ Client.SPLITTER + downPressed + Client.SPLITTER + leftPressed
					+ Client.SPLITTER + rightPressed;
			return sendString(moveString, playerIndex);
		}
		return false;
	}

	public boolean stopMoving(Player player, double locX, double locY,
			int characterFacing) {
		if (game.network.isPlayingOnline()) {
			int playerIndex = player.getIndex();
			String moveString = "PLAYERSTOPMOVE" + Client.SPLITTER + playerIndex
					+ Client.SPLITTER + locX + Client.SPLITTER + locY + Client.SPLITTER
					+ characterFacing + Client.SPLITTER
					+ player.character.animation.ordinal();
			if (game.network.weAreHost()) {
				boolean messageQueued = true;
				for (int i = 0; i < game.network.server.getHandlers().size(); i++) {
					Handler handler = game.network.server.getHandlers().get(i);
					if (!handler.hasMessageQueued(moveString))
						messageQueued = false;
				}
				if (messageQueued)
					return false; // already sending
			} else {
				if (game.network.client.containsMessageQueued(moveString))
					return false; // already sending
			}
			return sendString(moveString, playerIndex);
		}
		return false;
	}

	public boolean skullInfect(Player origin, Player infected) {
		if (game.network.isPlayingOnline()) {
			int playerIndex = origin.getIndex();
			String infectString = "SKULLINFECT" + Client.SPLITTER + infected.getIndex()
					+ Client.SPLITTER + origin.skullTimer + Client.SPLITTER
					+ origin.skullType;
			return sendString(infectString, playerIndex);
		}
		return false;
	}

	public boolean killPlayer(Player player, int killedBy, boolean throwBomb,
			long randomCount) {
		if (game.network.isPlayingOnline()) {
			int playerIndex = player.getIndex();
			if ((game.network.weAreHost() && player.isCpu)
					|| playerIndex == game.network.myInputTurnP1
					|| playerIndex == game.network.myInputTurnP2) {
				String killedString = "KILLED" + Client.SPLITTER + playerIndex
						+ Client.SPLITTER + killedBy + Client.SPLITTER + randomCount;
				String bombString = "";
				if (player.pickedUpBomb != null)
					if (throwBomb)
						bombString = "BOMBADD" + Client.SPLITTER + player.cellPoint.x
								+ Client.SPLITTER + player.cellPoint.y + Client.SPLITTER
								+ player.pickedUpBomb.type.ordinal() + Client.SPLITTER
								+ playerIndex + Client.SPLITTER
								+ player.pickedUpBomb.maxPower + Client.SPLITTER
								+ game.currentTimeMillis() + Client.SPLITTER
								+ player.pickedUpBomb.identifier;
					else {
						bombString = "BOMBTHROW" + Client.SPLITTER
								+ player.pickedUpBomb.getCellPoint().x + Client.SPLITTER
								+ player.pickedUpBomb.getCellPoint().y + Client.SPLITTER
								+ (3 + (player.character.facing * 5) + Client.SPLITTER
										+ playerIndex);
						game.network.addNetworkEvent(NetworkEventType.BOMBKICK,
								Long.parseLong(bombString.split(Client.SPLITTER)[6]),
								bombString.split(Client.SPLITTER));
					}
				boolean returnVal = sendString(killedString, playerIndex);
				if (player.pickedUpBomb != null)
					returnVal = returnVal && sendString(bombString, playerIndex);
				return returnVal;
			}
		}
		return false;
	}

	/**
	 * For bombs not laid normally by a Player.
	 */
	public boolean bombAdd(Player player, Bomb bomb) {
		if (game.network.isPlayingOnline()) {
			int playerIndex = player.getIndex();
			String bombString = "BOMBADD" + Client.SPLITTER + bomb.cellPoint.x
					+ Client.SPLITTER + bomb.cellPoint.y + Client.SPLITTER
					+ bomb.type.ordinal() + Client.SPLITTER + bomb.playerOrigin.getIndex()
					+ Client.SPLITTER + bomb.maxPower + Client.SPLITTER + bomb.explodeTime
					+ Client.SPLITTER + bomb.identifier;
			return sendString(bombString, playerIndex);
		}
		return false;
	}

	/**
	 * Sets a block to be destroyed.
	 */
	public boolean blockDestroy(Point blockPoint, long timeToBeDestroyed) {
		if (game.network.isPlayingOnline()) {
			String itemString = "BLOCKDESTROY" + Client.SPLITTER + blockPoint.x
					+ Client.SPLITTER + blockPoint.y + Client.SPLITTER
					+ timeToBeDestroyed;
			// This code only runs if we are the host
			return sendString(itemString, -1);
		}
		return false;
	}

	private boolean sendString(String string, int playerIndex) {
		if (game.network.weAreHost()) {
			if (playerIndex == -1 || game.players.get(playerIndex).isCpu
					|| playerIndex == game.network.myInputTurnP1
					|| playerIndex == game.network.myInputTurnP2) {
				game.network.server.sendMessageToAllClients("GAME:" + string);
				return true;
			}
		} else {
			if (playerIndex == game.network.myInputTurnP1
					|| playerIndex == game.network.myInputTurnP2) {
				game.network.client.sendMessageToServer("GAME:" + string);
				return true;
			}
		}
		return false;
	}

	public Bomb getBomb(long identifier) {
		Bomb bomb = null;
		ArrayList<com.github.vegeto079.saturnbomberman.objects.Object> objectList = new ArrayList<com.github.vegeto079.saturnbomberman.objects.Object>(
				game.objects);
		for (int i = 0; i < objectList.size(); i++)
			try {
				bomb = (Bomb) objectList.get(i);
				if (bomb.identifier == identifier)
					// Found the bomb!
					return bomb;
			} catch (Exception e) { // not a Bomb
			}
		return bomb;
	}

	public Item getItem(Point cellPoint, int itemType) {
		Item item = null;
		ArrayList<com.github.vegeto079.saturnbomberman.objects.Object> objectList = new ArrayList<com.github.vegeto079.saturnbomberman.objects.Object>(
				game.objects);
		for (int i = 0; i < objectList.size(); i++)
			try {
				item = (Item) objectList.get(i);
				if (item.cellPoint.distance(cellPoint) == 0
						&& item.type.ordinal() == itemType)
					// Found the bomb!
					return item;
			} catch (Exception e) { // not a Bomb
			}
		return item;
	}
}
