package com.github.vegeto079.saturnbomberman.objects;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;

import com.github.vegeto079.ngcommontools.main.DoublePoint;
import com.github.vegeto079.ngcommontools.main.Logger.LogLevel;
import com.github.vegeto079.ngcommontools.main.Resizer;
import com.github.vegeto079.ngcommontools.main.Tools;
import com.github.vegeto079.ngcommontools.networking.Client;
import com.github.vegeto079.saturnbomberman.main.MainBomberman;
import com.github.vegeto079.saturnbomberman.main.Stage;
import com.github.vegeto079.saturnbomberman.misc.Pictures;
import com.github.vegeto079.saturnbomberman.objects.Bomb.BombType;


/**
 * 
 * @author Nathan
 * @version 1.0: Started tracking version.
 * @version 1.01: {@link #activate(MainBomberman, Player, boolean, long)} added
 *          parameters for compatibility with online play.
 * @version 1.1: Added network compatibility. Added
 *          {@link #playerWhoRetrievedIndex},{@link #retrievalTime},
 *          {@link #playerWhoRetrievedBombType},
 *          {@link #playerWhoRetrievedFirePower},
 *          {@link #playerWhoRetrievedHasKickBomb}, and
 *          {@link #playerWhoRetrievedHasPowerGlove} to store information about
 *          the player who picked up this Item. Any player who contests this
 *          will remove the item from the other Player with
 *          {@link #deactivate(Player)}.
 * @version 1.11: Added int casting to
 *          {@link Resizer#resize(java.awt.Image, int, int)} calls.
 * 
 */
public class Item extends Object {
	public int itemPicX = -1;
	public int itemPicY = -1;
	public ItemType type = null;
	public int animationTick = 0;
	public int colorTick = 0;
	public int tickSkip = 0;
	private Point adjustExactPoint = new Point(-36, -3);
	public boolean visible = false;
	public boolean collectable = false;
	public boolean dying = false;

	private int playerWhoRetrievedIndex = -1;
	private int playerWhoRetrievedBombType = -1;
	private int playerWhoRetrievedFirePower = -1;
	private boolean playerWhoRetrievedHasKickBomb = false;
	private boolean playerWhoRetrievedHasPowerGlove = false;
	private long retrievalTime = 0;

	public enum ItemType {
		FIRE_UP, BOMB_UP, SKATES, KICK_BOMB, POWER_GLOVE, MAX_FIRE, LINE_BOMB, BEAD_BOMB, SPIKED_BOMB, JELLY_BOMB, POWER_BOMB, LAND_MINE_BOMB, SANDALS, SKULL, DEVIL, COMBINE, EGG;
	}

	public Item(Point cellPoint, MainBomberman game, Point stageOffset, int whichStage) {
		this.cellPoint = cellPoint;
		float[] chance = { 0.5f, 0.5f, 0.5f, 0.07f, 0.05f, 0.0001f, 0.05f, 0.02f, 0.03f,
				0.01f, 0.04f, 0.005f, 0.02f, 0.035f, 0f, 0.01f, 0f };
		int[] mustHave = { 7, 7, 7, 2, 2, 0, 2, 2, 0, 0, 2, 2, 1, 2, 1, 2, 2 };
		// int[] mustHave = { 0, 0, 0, 2, 2, 0, 2, 2, 0, 0, 2, 2, 1, 20, 1, 2, 2
		// };
		int[] powerUpCount = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
		for (int i = 0; i < game.objects.size(); i++)
			if (game.objects.get(i) instanceof Item) {
				Item item = (Item) game.objects.get(i);
				powerUpCount[item.type.ordinal()]++;
			}
		for (int i = 0; i < mustHave.length; i++) {
			if (powerUpCount[i] < mustHave[i] && stageCanHaveItem(whichStage, i, game)) {
				type = ItemType.values()[i];
				itemPicX = i;
				game.logger.log(LogLevel.DEBUG, "Forced placing of: " + type);
			}
		}
		while (type == null) {
			int tryItem = Tools.random(game, 0, chance.length - 1,
					"Random item placement (pick itemType)");
			if (stageCanHaveItem(whichStage, tryItem, game) && chance[tryItem] >= Tools
					.random(game, "Random item placement (check item chance)")) {
				type = ItemType.values()[tryItem];
				itemPicX = tryItem;
				game.logger.log(LogLevel.DEBUG, "Randomly placed: " + type);
			}
		}
		itemPicY = 0;
		while (itemPicX >= 4) {
			itemPicX -= 4;
			itemPicY++;
		}
		int temp = itemPicY;
		itemPicY = itemPicX;
		itemPicX = temp;
		exactPoint = new DoublePoint(
				Stage.getExactTileMidPoint(game, cellPoint, stageOffset));
		exactPoint = new DoublePoint(exactPoint.toPoint().x + adjustExactPoint.x,
				exactPoint.toPoint().y + adjustExactPoint.y);
	}

	/**
	 * Used for force placing an item, such as an item re-appearing when a
	 * player gets struck by a thrown bomb.
	 */
	public Item(Point cellPoint, MainBomberman game, int itemType) {
		this.cellPoint = cellPoint;
		type = ItemType.values()[itemType];
		itemPicX = itemType;
		itemPicY = 0;
		while (itemPicX >= 4) {
			itemPicX -= 4;
			itemPicY++;
		}
		int temp = itemPicY;
		itemPicY = itemPicX;
		itemPicX = temp;
		exactPoint = new DoublePoint(
				Stage.getExactTileMidPoint(game, cellPoint, game.stage.getOffset()));
		exactPoint = new DoublePoint(exactPoint.toPoint().x + adjustExactPoint.x,
				exactPoint.toPoint().y + adjustExactPoint.y);
	}

	public static boolean stageCanHaveItem(int stage, int itemIndex, MainBomberman game) {
		int[][] stageCanHave = { { 0, 1, 2, 3, 4, 13, 16 }, { 0, 1, 2, 3, 13, 16 },
				{ 0, 1, 2, 3, 11, 13 }, { 0, 1, 2, 3, 4, 6, 10, 13, 16 },
				{ 0, 1, 2, 3, 4, 7, 13, 15, 16 }, { 0, 1, 2, 3, 13, 16 },
				{ 0, 1, 2, 3, 4, 13 }, { 0, 1, 2, 3, 13, 16 },
				{ 0, 1, 2, 3, 4, 5, 6, 13 } };
		int[] thisStage = stageCanHave[stage];
		boolean disableEggs = true;// TODO don't disable eggs when done
		if (disableEggs && itemIndex == 16)
			return false;
		if (game.options.devil) {
			int[] tempStage = new int[thisStage.length + 1];
			for (int i = 0; i < thisStage.length; i++)
				tempStage[i] = thisStage[i];
			tempStage[thisStage.length] = 14;
		}
		for (int i = 0; i < thisStage.length; i++)
			if (thisStage[i] == itemIndex)
				return true;
		return false;
	}

	public void destroy(MainBomberman game) {
		if (game.network.isPlayingOnline() && playerWhoRetrievedIndex != -1) {
			// Someone picked up what should have been destroyed!
			// Let them have it I guess, but don't run the dying animation.
			return;
		}
		if (!dying && !finished) {
			collectable = false;
			dying = true;
			tickSkip = -1;
			animationTick = -1;
			tick(game);
		}
	}

	public String toString() {
		return "Item [" + itemPicX + "," + itemPicY + "," + type + "," + exactPoint + ","
				+ cellPoint + "," + visible + "," + collectable + "," + finished + "]";
	}

	public void activate(MainBomberman game, Player player, boolean fromOnlinePlay,
			long retrievalTime) {
		if (game.network.isPlayingOnline()) {
			String itemString = "GAME:ITEMACTIVATE" + Client.SPLITTER + cellPoint.x
					+ Client.SPLITTER + cellPoint.y + Client.SPLITTER + type.ordinal()
					+ Client.SPLITTER + player.getIndex() + Client.SPLITTER
					+ retrievalTime;
			if (!game.network.weAreHost()
					&& (player.getIndex() == game.network.myInputTurnP1
							|| player.getIndex() == game.network.myInputTurnP2)) {
				game.network.client.sendMessageToServer(itemString);
			} else if (game.network.weAreHost()
					&& (player.isCpu || player.getIndex() == game.network.myInputTurnP1
							|| player.getIndex() == game.network.myInputTurnP2)) {
				game.network.server.sendMessageToAllClients(itemString);
			} else if (fromOnlinePlay) {
				// Go through as normal
			} else {
				// Player we do not control is trying to activate an item,
				// ignore it.
				return;
			}
		}
		if (!type.equals(ItemType.KICK_BOMB) && !type.equals(ItemType.POWER_GLOVE)
				&& !type.equals(ItemType.SKULL) && !type.equals(ItemType.EGG))
			game.sound.play("Item Get");
		game.logger.log(LogLevel.DEBUG,
				"Player " + player.getIndex() + " picked up " + this);
		visible = false;
		finished = true;
		if (this.retrievalTime > retrievalTime) {
			// Uh oh, someone else rightfully got it first..
			return;
		} else if (playerWhoRetrievedIndex != -1 && this.retrievalTime < retrievalTime) {
			// We got it first, but someone else thought they could claim it!
			deactivate(game.players.get(playerWhoRetrievedIndex));
			game.logger.log(LogLevel.DEBUG,
					"Deactivating Player #" + playerWhoRetrievedIndex + "'s item.");
		}
		playerWhoRetrievedIndex = player.getIndex();
		playerWhoRetrievedBombType = player.bombType.ordinal();
		playerWhoRetrievedFirePower = player.firePower;
		playerWhoRetrievedHasKickBomb = player.kickBomb;
		playerWhoRetrievedHasPowerGlove = player.powerGlove;
		this.retrievalTime = retrievalTime;
		if (type.equals(ItemType.FIRE_UP)) {
			if (player.firePower < 9)
				player.firePower++;
		} else if (type.equals(ItemType.BOMB_UP)) {
			if (player.bombAmt < 9)
				player.bombAmt++;
		} else if (type.equals(ItemType.SKATES)) {
			if (player.speed < 9)
				player.speed++;
		} else if (type.equals(ItemType.KICK_BOMB)) {
			game.sound.play("Hustle");
			player.kickBomb = true;
		} else if (type.equals(ItemType.POWER_GLOVE)) {
			game.sound.play("Power Glove");
			player.powerGlove = true;
		} else if (type.equals(ItemType.MAX_FIRE)) {
			player.firePower = 9;
		} else if (type.equals(ItemType.LINE_BOMB)) {
			player.lineBomb = true;
		} else if (type.equals(ItemType.BEAD_BOMB)) {
			player.bombType = BombType.BEAD;
		} else if (type.equals(ItemType.SPIKED_BOMB)) {
			player.bombType = BombType.SPIKED;
		} else if (type.equals(ItemType.JELLY_BOMB)) {
			player.bombType = BombType.JELLY;
		} else if (type.equals(ItemType.POWER_BOMB)) {
			player.bombType = BombType.POWER;
		} else if (type.equals(ItemType.LAND_MINE_BOMB)) {
			player.bombType = BombType.LAND_MINE;
		} else if (type.equals(ItemType.SANDALS)) {
			if (player.speed > 2)
				player.speed -= 2;
			else if (player.speed > 1)
				player.speed--;
		} else if (type.equals(ItemType.SKULL)) {
			game.sound.play("Skull");
			player.skullTimer = System.currentTimeMillis() + 10000;
			player.skullType = Tools.random(game, 0, 9, "Skull Random");
			if (player.skullType == 7) {
				player.resetControls();
			}
		} else if (type.equals(ItemType.DEVIL)) {
			player.devilTimer = System.currentTimeMillis();
		} else if (type.equals(ItemType.COMBINE)) {
			player.combineTimer = System.currentTimeMillis();
		} else if (type.equals(ItemType.EGG)) {
			// TODO make character ride dino
			game.sound.play("Dinosaur Get");
		}
	}

	private void deactivate(Player player) {
		if (type.equals(ItemType.FIRE_UP)) {
			if (player.firePower > 1)
				player.firePower--;
		} else if (type.equals(ItemType.BOMB_UP)) {
			if (player.bombAmt > 1)
				player.bombAmt--;
		} else if (type.equals(ItemType.SKATES)) {
			if (player.speed > 1)
				player.speed--;
		} else if (type.equals(ItemType.KICK_BOMB)) {
			player.kickBomb = playerWhoRetrievedHasKickBomb;
		} else if (type.equals(ItemType.POWER_GLOVE)) {
			player.powerGlove = playerWhoRetrievedHasPowerGlove;
		} else if (type.equals(ItemType.MAX_FIRE)) {
			player.firePower = playerWhoRetrievedFirePower;
		} else if (type.equals(ItemType.LINE_BOMB)) {
			player.bombType = BombType.values()[playerWhoRetrievedBombType];
		} else if (type.equals(ItemType.BEAD_BOMB)) {
			player.bombType = BombType.values()[playerWhoRetrievedBombType];
		} else if (type.equals(ItemType.SPIKED_BOMB)) {
			player.bombType = BombType.values()[playerWhoRetrievedBombType];
		} else if (type.equals(ItemType.JELLY_BOMB)) {
			player.bombType = BombType.values()[playerWhoRetrievedBombType];
		} else if (type.equals(ItemType.POWER_BOMB)) {
			player.bombType = BombType.values()[playerWhoRetrievedBombType];
		} else if (type.equals(ItemType.LAND_MINE_BOMB)) {
			player.bombType = BombType.values()[playerWhoRetrievedBombType];
		} else if (type.equals(ItemType.SANDALS)) {
			if (player.speed == 1)
				player.speed++;
			else
				player.speed += 2;
		} else if (type.equals(ItemType.SKULL)) {
			player.skullTimer = -1;
			player.skullType = -1;
		} else if (type.equals(ItemType.DEVIL)) {
			player.devilTimer = -1;
		} else if (type.equals(ItemType.COMBINE)) {
			player.combineTimer = -1;
		} else if (type.equals(ItemType.EGG)) {
			// TODO remove character's dino
		}
	}

	public void tick(MainBomberman game) {
		if (!visible || finished)
			return;
		tickSkip++;
		if (tickSkip >= 2 && !dying)
			tickSkip = 0;
		else if (tickSkip >= 6 && dying)
			tickSkip = 0;
		if (tickSkip != 0)
			return;
		animationTick++;
		if (animationTick % 5 == 0)
			if (colorTick + 1 > 4)
				colorTick = 0;
			else
				colorTick++;
		if (animationTick >= 17)
			collectable = true;
		if (animationTick >= 30)
			animationTick = 20;
		if (dying) {
			if (animationTick >= 4) {
				visible = false;
				finished = true;
			}
		}
	}

	public void draw(Graphics2D g, MainBomberman game) {
		if (itemPicX == -1 || itemPicY == -1 || !visible || finished)
			return;
		if (dying) {
			if (type.equals(ItemType.EGG)) {
				// TODO: draw egg melting
			} else {
				BufferedImage img = game.pictures.items.get(itemPicX).get(itemPicY);
				int picSizeX = img.getWidth();
				int picSizeY = img.getHeight();
				if (animationTick > 1)
					img = Resizer.resize(img, img.getWidth() / 2, img.getWidth() / 2);
				if (animationTick > 2)
					img = Resizer.resize(img, img.getWidth() / 2, img.getWidth() / 2);
				if (animationTick < 0)
					animationTick = 0;
				if (animationTick > game.pictures.itemExplosion.get(0).size())
					animationTick = game.pictures.itemExplosion.get(0).size() - 1;
				g.drawImage(img, exactPoint.toPoint().x + (picSizeX - img.getWidth()) / 2,
						exactPoint.toPoint().y - 17 + (picSizeY - img.getHeight()) / 2,
						game);
				g.drawImage(game.pictures.itemExplosion.get(0).get(animationTick),
						exactPoint.toPoint().x - 10, exactPoint.toPoint().y - 17, game);
			}
		} else {
			if (type.equals(ItemType.EGG)) {
				// TODO: draw egg
			} else {
				BufferedImage img = game.pictures.items.get(itemPicX).get(itemPicY);
				int picSizeX = img.getWidth();
				int picSizeY = img.getHeight();
				if (colorTick == 1)
					img = Pictures.changeColor(img, img.getRGB(5, 5),
							new Color(255, 197, 0).getRGB());
				else if (colorTick == 2)
					img = Pictures.changeColor(img, img.getRGB(5, 5),
							new Color(123, 164, 16).getRGB());
				else if (colorTick == 3)
					img = Pictures.changeColor(img, img.getRGB(5, 5),
							new Color(0, 115, 214).getRGB());
				else if (colorTick == 4)
					img = Pictures.changeColor(img, img.getRGB(5, 5),
							new Color(123, 41, 148).getRGB());
				if (animationTick >= 0 && animationTick <= 3) {
					img = Resizer.resize(img,
							(int) (picSizeX * (1 + .1 * (animationTick + 1))),
							(int) (picSizeY * (.9 - (.1 * animationTick))));
					g.drawImage(img,
							exactPoint.toPoint().x - (img.getWidth() - picSizeX) / 2,
							exactPoint.toPoint().y + animationTick * 3, game);
				} else if (animationTick >= 4 && animationTick <= 6) {
					img = Resizer.resize(img,
							(int) (picSizeX * (1.4 - (.1 * (animationTick - 3)))),
							(int) (picSizeY * (.6 + (.1 * (animationTick - 3)))));
					g.drawImage(img,
							exactPoint.toPoint().x - (img.getWidth() - picSizeX) / 2,
							exactPoint.toPoint().y + 12 - ((animationTick - 2) * 3),
							game);
				} else if (animationTick == 7) {
					g.drawImage(img, exactPoint.toPoint().x, exactPoint.toPoint().y,
							game);
				} else if (animationTick == 8 || animationTick == 9) {
					img = Resizer.resize(img, (int) (picSizeX * .5),
							(int) (picSizeY * 1.2));
					g.drawImage(img,
							exactPoint.toPoint().x + (picSizeX - img.getWidth()) / 2,
							exactPoint.toPoint().y - 12 - (animationTick - 8) * 6, game);
				} else if (animationTick == 10) {
					img = Resizer.resize(img, (int) (picSizeX * .75),
							(int) (picSizeY * 1.15));
					g.drawImage(img,
							exactPoint.toPoint().x + (picSizeX - img.getWidth()) / 2,
							exactPoint.toPoint().y - 20, game);
				} else if (animationTick == 11 || animationTick == 15) {
					img = Resizer.resize(img, (int) (picSizeX * 1.1),
							(int) (picSizeY * 0.85));
					g.drawImage(img,
							exactPoint.toPoint().x - (img.getWidth() - picSizeX) / 2,
							exactPoint.toPoint().y - 20, game);
				} else if (animationTick == 12 || animationTick == 14) {
					img = Resizer.resize(img, (int) (picSizeX * 1.15),
							(int) (picSizeY * 0.70));
					g.drawImage(img,
							exactPoint.toPoint().x - (img.getWidth() - picSizeX) / 2,
							exactPoint.toPoint().y - 21, game);
				} else if (animationTick == 13) {
					img = Resizer.resize(img, (int) (picSizeX * 1.25),
							(int) (picSizeY * 0.55));
					g.drawImage(img,
							exactPoint.toPoint().x - (img.getWidth() - picSizeX) / 2,
							exactPoint.toPoint().y - 22, game);
				} else {
					g.drawImage(img, exactPoint.toPoint().x, exactPoint.toPoint().y - 17,
							game);
				}
			}
		}
	}

	public ItemType getType() {
		return type;
	}
}
