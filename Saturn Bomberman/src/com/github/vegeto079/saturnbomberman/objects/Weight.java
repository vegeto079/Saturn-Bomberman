package com.github.vegeto079.saturnbomberman.objects;

import java.awt.Graphics2D;
import java.awt.Point;
import java.util.ArrayList;

import com.github.vegeto079.ngcommontools.main.DoublePoint;
import com.github.vegeto079.ngcommontools.main.Logger.LogLevel;
import com.github.vegeto079.saturnbomberman.main.Character.Animation;
import com.github.vegeto079.saturnbomberman.main.MainBomberman;
import com.github.vegeto079.saturnbomberman.main.Stage;

/**
 * 
 * @author Nathan
 * @version 1.0: Started tracking version.
 * @version 1.01: No longer kills people if they are already dead.
 * @version 1.02: Removed final point from {@link #rightDropCourse}, as it was
 *          also in {@link #leftDropCourse}. Only need one falling weight for
 *          the last square.
 * @version 1.1: Added network support.
 */
public class Weight extends Object {
	long creationTime = -1;
	public boolean solid = false;

	public final static Point[] leftDropCourse = { new Point(0, 0), new Point(0, 1), new Point(0, 2), new Point(0, 3),
			new Point(0, 4), new Point(0, 5), new Point(0, 6), new Point(0, 7), new Point(0, 8), new Point(0, 9),
			new Point(0, 10), new Point(1, 10), new Point(1, 9), new Point(1, 8), new Point(1, 7), new Point(1, 6),
			new Point(1, 5), new Point(1, 4), new Point(1, 3), new Point(1, 2), new Point(1, 1), new Point(1, 0),
			new Point(2, 0), new Point(2, 1), new Point(2, 2), new Point(2, 3), new Point(2, 4), new Point(2, 5),
			new Point(2, 6), new Point(2, 7), new Point(2, 8), new Point(2, 9), new Point(2, 10), new Point(3, 10),
			new Point(3, 9), new Point(3, 8), new Point(3, 7), new Point(3, 6), new Point(3, 5), new Point(3, 4),
			new Point(3, 3), new Point(3, 2), new Point(3, 1), new Point(3, 0), new Point(4, 0), new Point(5, 0),
			new Point(6, 0), new Point(7, 0), new Point(8, 0), new Point(9, 0), new Point(10, 0), new Point(11, 0),
			new Point(12, 0), new Point(12, 1), new Point(11, 1), new Point(10, 1), new Point(9, 1), new Point(8, 1),
			new Point(7, 1), new Point(6, 1), new Point(5, 1), new Point(4, 1), new Point(4, 2), new Point(4, 3),
			new Point(4, 4), new Point(4, 5), new Point(4, 6), new Point(4, 7), new Point(4, 8), new Point(5, 8),
			new Point(6, 8), new Point(7, 8), new Point(8, 8), new Point(9, 8), new Point(10, 8), new Point(11, 8),
			new Point(11, 7), new Point(11, 6), new Point(11, 5), new Point(11, 4), new Point(11, 3), new Point(10, 3),
			new Point(9, 3), new Point(8, 3), new Point(7, 3), new Point(6, 3), new Point(6, 4), new Point(6, 5),
			new Point(6, 6), new Point(7, 6), new Point(8, 6), new Point(9, 6), new Point(9, 5), new Point(8, 5) };
	public final static Point[] rightDropCourse = { new Point(16, 10), new Point(16, 9), new Point(16, 8),
			new Point(16, 7), new Point(16, 6), new Point(16, 5), new Point(16, 4), new Point(16, 3), new Point(16, 2),
			new Point(16, 1), new Point(16, 0), new Point(15, 0), new Point(15, 1), new Point(15, 2), new Point(15, 3),
			new Point(15, 4), new Point(15, 5), new Point(15, 6), new Point(15, 7), new Point(15, 8), new Point(15, 9),
			new Point(15, 10), new Point(14, 10), new Point(14, 9), new Point(14, 8), new Point(14, 7),
			new Point(14, 6), new Point(14, 5), new Point(14, 4), new Point(14, 3), new Point(14, 2), new Point(14, 1),
			new Point(14, 0), new Point(13, 0), new Point(13, 1), new Point(13, 2), new Point(13, 3), new Point(13, 4),
			new Point(13, 5), new Point(13, 6), new Point(13, 7), new Point(13, 8), new Point(13, 9), new Point(13, 10),
			new Point(12, 10), new Point(11, 10), new Point(10, 10), new Point(9, 10), new Point(8, 10),
			new Point(7, 10), new Point(6, 10), new Point(5, 10), new Point(4, 10), new Point(4, 9), new Point(5, 9),
			new Point(6, 9), new Point(7, 9), new Point(8, 9), new Point(9, 9), new Point(10, 9), new Point(11, 9),
			new Point(12, 9), new Point(12, 8), new Point(12, 7), new Point(12, 6), new Point(12, 5), new Point(12, 4),
			new Point(12, 3), new Point(12, 2), new Point(11, 2), new Point(10, 2), new Point(9, 2), new Point(8, 2),
			new Point(7, 2), new Point(6, 2), new Point(5, 2), new Point(5, 3), new Point(5, 4), new Point(5, 5),
			new Point(5, 6), new Point(5, 7), new Point(6, 7), new Point(7, 7), new Point(8, 7), new Point(9, 7),
			new Point(10, 7), new Point(10, 6), new Point(10, 5), new Point(10, 4), new Point(9, 4), new Point(8, 4),
			new Point(7, 4), new Point(7, 5) };

	public Weight(Point cellPoint, MainBomberman game) {
		this.cellPoint = cellPoint;
		creationTime = System.currentTimeMillis();
		DoublePoint tempExactPoint = new DoublePoint(Stage.getExactTileMidPoint(game, cellPoint));
		exactPoint = new DoublePoint(tempExactPoint.x - Stage.getTileWidth(), tempExactPoint.y - game.getHeight());
		game.logger.log(LogLevel.DEBUG, "Weight created: " + this);
	}

	public String toString() {
		return "Weight[" + cellPoint.x + "," + cellPoint.y + "," + exactPoint.x + "," + exactPoint.y + ","
				+ creationTime + "," + solid + "]";
	}

	public double getTicksTillExplosion(MainBomberman game) {
		if (game.stage.getIndex(cellPoint.y, cellPoint.x) == 00)
			return -1; // Refuse to tick a weight that's on a hard block
		Point centerPoint = Stage.getExactTileMidPoint(game, cellPoint);
		// at 37 it explodes
		double dist = exactPoint.distance(centerPoint) - 37;
		dist = dist / 5;
		if (dist < 1)
			dist = -1;
		return dist;
	}

	public long getTimeTillExplosion(MainBomberman game) {
		if (game.stage.getIndex(cellPoint.y, cellPoint.x) == 00)
			return -1; // Refuse to tick a weight that's on a hard block
		double ticksTillExplosion = getTicksTillExplosion(game);
		long timeTillExplosion = (long) (1000d / game.getFps() * ticksTillExplosion);
		if (timeTillExplosion < 1)
			timeTillExplosion = -1;
		return timeTillExplosion;
	}

	public void tick(MainBomberman game) {
		if (game.stage.getIndex(cellPoint.y, cellPoint.x) == 00)
			return; // Refuse to tick a weight that's on a hard block
		int alivePlayers = 0;
		for (int i = 0; i < game.players.size(); i++)
			if (game.players.get(i).deadTimer == -1)
				alivePlayers++;
		if (alivePlayers < 2)
			return;
		if (!solid)
			exactPoint = new DoublePoint(exactPoint.x, exactPoint.y + 5d);
		Point centerPoint = Stage.getExactTileMidPoint(game, cellPoint);
		if (!solid && exactPoint.distance(centerPoint) < 37) {
			// We're hitting the ground!
			game.sound.play("Weight");
			DoublePoint tempExactPoint = new DoublePoint(centerPoint);
			exactPoint = new DoublePoint(tempExactPoint.x - Stage.getTileWidth(), tempExactPoint.y);
			solid = true;
			ArrayList<Player> players = game.players;
			for (int i = 0; i < players.size(); i++) {
				// Kill players if they're under us
				if (players.get(i).getCellPoint().distance(cellPoint) > 0)
					continue;
				if (players.get(i).deadTimer != -1) // already dead
					continue;
				boolean killPlayer = game.network.talking.killPlayer(players.get(i), Integer.MAX_VALUE, false,
						game.randomCount);
				if (!game.network.isPlayingOnline() || killPlayer) {
					players.get(i).deadTimer = 0;
					players.get(i).stats.killedByPlayer(Integer.MAX_VALUE);
					players.get(i).character.animation = Animation.IDLE;
					if (players.get(i).pickedUpBomb != null) {
						// Explode a bomb if it was being carried by the player
						DoublePoint dp = new DoublePoint(
								Stage.getExactTileMidPoint(game, players.get(i).getCellPoint()));
						players.get(i).pickedUpBomb.exactPoint = new DoublePoint(
								dp.x + players.get(i).pickedUpBomb.adjustExactPoint.x,
								dp.y + players.get(i).pickedUpBomb.adjustExactPoint.y);
						players.get(i).pickedUpBomb.pickedUpCount = -1;
						players.get(i).pickedUpBomb.explodeNow();
						game.objects.add(players.get(i).pickedUpBomb);
						players.get(i).pickedUpBomb = null;
					}
				}
			}
			ArrayList<Object> objects = game.objects;
			for (int i = 0; i < objects.size(); i++) {
				Object obj = objects.get(i);
				// Destroy all Items and blow up Bombs under us
				if (obj.cellPoint.distance(cellPoint) == 0)
					if (obj instanceof Item) {
						Item item = (Item) obj;
						item.destroy(game);
					} else if (obj instanceof Bomb) {
						Bomb bomb = (Bomb) obj;
						if (!bomb.exploding) {
							bomb.explodeNow();
						}
					}
			}
			game.stage.set(cellPoint.y, cellPoint.x, -1);// Remove block under
		}
	}

	public void draw(Graphics2D g, MainBomberman game) {
		if (game.stage.getIndex(cellPoint.y, cellPoint.x) == 00)
			return; // Refuse to display a weight that's on a hard block
		Point shadowPoint = Stage.getExactTileMidPoint(game, cellPoint);
		g.drawImage(game.pictures.shadow, shadowPoint.x - Stage.getTileWidth(), shadowPoint.y + 20, game);
		g.drawImage(game.pictures.weight, (int) exactPoint.x, (int) exactPoint.y, game);
	}

}
