package com.github.vegeto079.saturnbomberman.misc;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.Line2D;
import java.util.ArrayList;

import com.github.vegeto079.ngcommontools.main.AStar;
import com.github.vegeto079.ngcommontools.main.AStar.AStarPath;
import com.github.vegeto079.ngcommontools.main.DoublePoint;
import com.github.vegeto079.ngcommontools.main.Logger;
import com.github.vegeto079.ngcommontools.main.Logger.LogLevel;
import com.github.vegeto079.ngcommontools.main.Tools;
import com.github.vegeto079.saturnbomberman.main.MainBomberman;
import com.github.vegeto079.saturnbomberman.main.Stage;
import com.github.vegeto079.saturnbomberman.objects.Bomb;
import com.github.vegeto079.saturnbomberman.objects.Player;

/**
 * We currently count ourselves as something we should blow up. This could be
 * fixed later, but right now counting everyone as an equal player is simpler.
 * 
 * @author Nathan
 * @version 1.0: Started tracking version.
 * @version 1.01: Added {@link #reachableLocations},
 *          {@link #lastCheckedReachableLocations},
 *          {@link #checkReachableLocationsInterval},
 *          {@link #getReachableLocations()} and
 *          {@link #drawReachableLocations(Graphics2D)}.
 * @version 1.02: Changed <b>checkReachableLocationsInterval</b> to
 *          {@link #aiReactionTime}, to be used for multiple situations. Added
 *          {@link #tryingToMove}, {@link #tryingToMoveTime},
 *          {@link #tryingToMoveExactPoint}, {@link #tryingToMoveEndPoint} and
 *          {@link #tryingToMoveStartPoint} to try to get bots unstuck when they
 *          can't move.
 * @version 1.1: AI now handled in threads, just tells the game thread what
 *          direction to push with {@link #directionToPress} and where we are
 *          going with {@link #goingToExplodePoint} and
 *          {@link #goingToSafePoint}.
 * @version 1.11 {@link #distanceModifier} added.
 * @version 1.2 {@link #trapEnemies} added. AI now will value bomb placements
 *          that trap an enemy highly (see
 *          {@link #getBombAdjustmentValues(int[][], int, int, Point, Point, AIStarPath, ArrayList)})
 *          . We now also remove slight outliers from the explosion map so we
 *          don't go for 1 extra point, and instead can settle for 1 less but a
 *          closer position.
 * @version 1.21: Added {@link #afraidOfBlowingUpItems} and
 *          {@link #tempSpeedRatio}.
 * @version 1.3: Changed all {@link System#currentTimeMillis()} references to
 *          {@link MainBomberman#currentTimeMillis()}.
 * @version 1.4: Added {@link #walkingThroughFireMultiplier}, made some
 *          decisions based on {@link Player#speed}.
 * @version 1.41: Some slight fixes, added {@link #kickBombsToStayAlive}.
 * @version 1.5: Items are now decided on a per-item basis instead of thinking
 *          items are all the same. {@link #afraidOfBlowingUpItems} removed as
 *          this shouldn't be an issue anymore.
 */
public class AI {
	private MainBomberman game;
	private Player player;
	private boolean aiEnabled = true;
	private long keepMovingUntil = -1;
	private long keepMovingToBombUntil = -1;
	private long disableSafeMovingUntil = -1;

	public static AIStar.Settings aStarDefaultSettings = new AIStar.Settings(100000, false, false, false, true, true,
			100000, 1, .01f, 1000, new Logger(true));
	public static AIStar.Settings aStarReachableLocationsSettings = new AIStar.Settings(100000, false, true, false,
			true, true, 100000, 1, .01f, 1000, new Logger(true));
	private boolean[][] reachableLocations;
	private long[][] reachableExplodableTiles;
	private boolean placingBomb = false;
	private Thread pathFindingThread = null;
	private Thread reachableLocationsThread = null;
	private AStarPath currentPath = null;

	boolean tryingToMove = false;
	long tryingToMoveTime = 0;
	Point tryingToMoveStartPoint = null;
	Point tryingToMoveEndPoint = null;
	DoublePoint tryingToMoveExactPoint = null;

	private Point goingToExplodePoint = null;
	private Point goingToSafePoint = null;
	int directionToPress = -1;

	/**
	 * Whether or not the AI will think ahead of time and move to the next spot they
	 * want to place the bomb, rather than just staying safe until they can place
	 * another.<br>
	 * <b>true</b>: More aggressive and sets self up for death.<br>
	 * <b>false</b>: Plays it safe more often.
	 */
	public boolean walkToBombPointProactively = false;
	/**
	 * How many points a player kill is worth to this AI. The higher this number,
	 * the more likely the AI is to hunt down players.<br>
	 * <b>At zero</b>: Makes no attempt to kill players, only destroys blocks. <br>
	 * <b>At one</b>: Players are essentially one block, will go for kills but not
	 * any more than blocks.<br>
	 * <b>Higher</b>: More aggressive player-killing AI.
	 */
	public int playerHunterLevel = 2;
	/**
	 * This value is applied to the ai's speed value. <br>
	 * <b>At 100</b>: Default speed <br>
	 * <b>At 25</b>: 25% speed <br>
	 * <b>At 200</b>: 2x speed
	 */
	public int speedRatio = 100;
	/**
	 * Temporary variable to hold {@link #speedRatio} if it must be changed
	 * temporarily.
	 */
	public int tempSpeedRatio = 100;
	/**
	 * This value affects the ai's viewpoint of distance.<br>
	 * This number represents the amount of tiles away something is before it's
	 * considered 'far away'.<br>
	 * Therefore, the larger this number, the more this person will possibly go far
	 * away to do something.<br>
	 * By default, this is 3.
	 */
	public int distanceModifier = 3;
	/**
	 * This value is the amount of time (in ms) it takes the ai to react to
	 * something.<br>
	 * If your computer can handle it, this can be set to 1ms. Otherwise you may
	 * want to raise it to 10, or even 100 if you experience a lot of slowdowns.
	 * <br>
	 * The higher this is, the worse the ai will be as it won't respond fast. <br>
	 * Default is 10, which is fine.
	 */
	public long reactionTime = 10;
	/**
	 * This value represents whether or not the ai will try to trap players in with
	 * bombs. This will cause the level of the ai to jump, as it's more like
	 * "thinking ahead".
	 */
	public boolean trapEnemies = true;
	/**
	 * This value changes the amount of time the ai uses to determine if a spot is
	 * walk-through-able. Lower values is a cockier AI.<br>
	 * <b>1.0</b> is default, with the danger-spread being from 700-1500ms.<br>
	 * <b>0.5</b> is half at 350-750ms.
	 */
	public float walkingThroughFireMultiplier = 1.0f;
	/**
	 * This value represents if the ai will try to kick a bomb if it means getting
	 * themselves out of a trapped situation. This should always be <b>true</b> if
	 * you want an AI that utilizes the kickbomb defensively on purpose. Otherwise
	 * they will just accept defeat and let themselves die.
	 */
	public boolean kickBombsToStayAlive = true;

	/**
	 * Initiates a new AI.
	 * 
	 * @param game
	 * @param player
	 * @param level
	 *            Difficulty level (1-5, 0 for random, 6 for terminator, 7 for
	 *            staysafe)
	 */
	public AI(MainBomberman game, Player player, int level) {
		this.game = game;
		this.player = player;
		if (level == 0)
			level = Tools.random(game, 1, 5, "Random AI");
		if (level == 1) {
			playerHunterLevel = Tools.random(game, 0, 2, "Random AI hunter level");
			if (playerHunterLevel > 1)
				playerHunterLevel = 0;
			walkToBombPointProactively = false;
			// speedRatio = Tools.random(game, 40, 70, "Random AI speed ratio");
			distanceModifier = 5;
			reactionTime = Tools.random(game, 800, 1200, "Random AI reaction time");
			trapEnemies = false;
			walkingThroughFireMultiplier = (float) Tools.random(game, 1, 3, "Random AI walkthrough multiplier") * 0.1f;
			kickBombsToStayAlive = false;
		} else if (level == 2) {
			playerHunterLevel = Tools.random(game, 1, 3, "Random AI hunter level");
			walkToBombPointProactively = Tools.random(game, 100, "Random AI walk to bomb proactively") > 50;
			// speedRatio = Tools.random(game, 60, 80, "Random AI speed ratio");
			distanceModifier = Tools.random(game, 2, 4, "Random AI distance modifier");
			reactionTime = Tools.random(game, 350, 900, "Random AI reaction time");
			trapEnemies = false;
			walkingThroughFireMultiplier = (float) Tools.random(game, 3, 5, "Random AI walkthrough multiplier") * 0.1f;
			kickBombsToStayAlive = false;
		} else if (level == 3) {
			playerHunterLevel = Tools.random(game, 1, 8, "Random AI hunter level");
			walkToBombPointProactively = Tools.random(game, 100, "Random AI walk to bomb proactively") > 50;
			// speedRatio = Tools.random(game, 90, 110, "Random AI speed
			// ratio");
			distanceModifier = Tools.random(game, 4, 8, "Random AI distance modifier");
			reactionTime = Tools.random(game, 50, 100, "Random AI reaction time");
			trapEnemies = true;
			walkingThroughFireMultiplier = (float) Tools.random(game, 5, 7, "Random AI walkthrough multiplier") * 0.1f;
			kickBombsToStayAlive = true;
		} else if (level == 4) {
			playerHunterLevel = Tools.random(game, 1, 10, "Random AI hunter level");
			walkToBombPointProactively = Tools.random(game, 100, "Random AI walk to bomb proactively") > 80;
			// speedRatio = Tools.random(game, 125, 150, "Random AI speed
			// ratio");
			distanceModifier = Tools.random(game, 5, 10, "Random AI distance modifier");
			reactionTime = Tools.random(game, 50, 100, "Random AI reaction time");
			trapEnemies = true;
			walkingThroughFireMultiplier = (float) Tools.random(game, 7, 10, "Random AI walkthrough multiplier") * 0.1f;
			kickBombsToStayAlive = true;
		} else if (level == 5) {
			playerHunterLevel = Tools.random(game, 1, 3, "Random AI hunter level");
			walkToBombPointProactively = true;
			// speedRatio = Tools.random(game, 150, 200, "Random AI speed
			// ratio");
			distanceModifier = Tools.random(game, 8, 12, "Random AI distance modifier");
			reactionTime = Tools.random(game, 1, 5, "Random AI reaction time");
			trapEnemies = true;
			walkingThroughFireMultiplier = (float) Tools.random(game, 7, 10, "Random AI walkthrough multiplier") * 0.1f;
			kickBombsToStayAlive = true;
		} else if (level == 6) {
			// They're terminators, the best I can do.
			playerHunterLevel = Tools.random(game, 2, 4, "Random AI hunter level");
			walkToBombPointProactively = true;
			speedRatio = Tools.random(game, 150, 250, "Random AI speed ratio");
			distanceModifier = 100;
			reactionTime = Tools.random(game, 1, 5, "Random AI reaction time");
			trapEnemies = true;
			walkingThroughFireMultiplier = (float) Tools.random(game, 4, 8, "Random AI walkthrough multiplier") * 0.1f;
			kickBombsToStayAlive = true;
		} else if (level == 7) {
			// One function - stay alive!
			playerHunterLevel = 0;
			walkToBombPointProactively = false;
			// speedRatio = 100;
			distanceModifier = 3;
			reactionTime = 5;
			trapEnemies = false;
			walkingThroughFireMultiplier = 10;
			kickBombsToStayAlive = true;
		}
		speedRatio = 100;
		tempSpeedRatio = speedRatio;
		game.logger.log(LogLevel.DEBUG, "New AI added, level " + level);
	}

	private class AIThread extends Thread {
		public void run() {
			// if (!stealReachableLocationsIfPossible())
			while (game.stage != null && player.deadTimer == -1) {
				try {
					updateReachableLocations();
					sleep(reactionTime);
				} catch (Exception e) {
				}
			}
		}
	}

	public void getReachableLocations() {
		if (reachableLocationsThread != null && reachableLocationsThread.isAlive())
			return;
		reachableLocationsThread = new Thread(new AIThread(), "AI Thread p" + player.getIndex());
		reachableLocationsThread.setPriority(Thread.MIN_PRIORITY);
		reachableLocationsThread.start();
	}

	public static int[][] getMap(MainBomberman game, Player player) {
		long[][] stageMap = game.stage.getCollisionMap(game);
		int[][] map = new int[game.stage.getHeight()][game.stage.getWidth()];
		long weAreGoingToExplodeTime = -1;
		for (int i = 0; i < stageMap[0].length; i++) {
			for (int j = 0; j < stageMap.length; j++) {
				int num = (int) stageMap[j][i];
				if (weAreGoingToExplodeTime == -1 && player.getCellPoint().distance(new Point(i, j)) == 0) {
					// System.out.println("found spot we are on. num: " + num);
					if (num >= 0) {
						// If this spot we are standing on is dangerous, make
						// preparations!
						// System.out
						// .println("We're going to explode! Take cautionary
						// measures.");
						weAreGoingToExplodeTime = num;
						i = 0;
						j = 0;
						for (int k = 0; k < map.length; k++)
							for (int z = 0; z < map[k].length; z++)
								map[k][z] = 0;
						num = (int) stageMap[j][i];
					}
				}
				if (num == 100000 && weAreGoingToExplodeTime != -1) {
					// found a bomb
					// System.out.println("Found a bomb at (" + i + "," + j +
					// ")!");
					if (player.getCellPoint().distance(new Point(i, j)) == 0) {
						// If we are on the point with a bomb, we can move
						ArrayList<Bomb> bombs = player.getBombsLaid();
						for (Bomb bomb : bombs)
							if (bomb.getCellPoint().distance(new Point(i, j)) == 0) {
								// System.out
								// .println("We're on a bomb! Ahh! Get off of
								// it!");
								num = 10;
								break;
							}
					} // If the bomb is not under us, ignore it
						// else
						// System.out.println("But we're not on it...");
				} else if (num >= 500 && weAreGoingToExplodeTime != -1) {
					// we're either standing on fire or under weights going to
					// fall or something dangerous like that. To allow ourselves
					// to get out of this hairy situation, make anything
					// dangerous temporarily not dangerous!
					// System.out
					// .println("Temporarily set dangerous area to safe, gotta
					// get outta here!");
					num = 10;
				} else if (num >= 1 && weAreGoingToExplodeTime != -1) {
					// We want to get out of a dangerous situation, but walking
					// into one thats <500ms from exploding (or already
					// exploding) is certain death. Better to wait on the
					// dangerous spot we're on, probably.
					if (weAreGoingToExplodeTime <= 600) {
						// We're on a square that's close to exploding anyway,
						// might as well run for it!
						num = 10;
					} else
						num = 100000;
				} else if (num == 0 && weAreGoingToExplodeTime != -1) {
					// Despite all else, don't walk onto a block that'll
					// instantly kill us.
					num = 100000;
				} else if (num >= 0 && num <= 1000)
					num = 100000;
				else if (num != 100000) {
					// If a place will explode in over 1500ms from now, we can
					// safely walk over it (for now), so just set it to safe.
					// That said, if we can run faster, this timeframe can be
					// smaller.
					// 1 speed = 1500ms
					// 9 speed = 700ms
					// This value gets multiplied based on difficulty.
					int cutoff = 1600 - player.speed * 100;
					if (player.ai != null)
						cutoff = (int) ((float) cutoff * player.ai.walkingThroughFireMultiplier);
					if (num >= cutoff)
						num = 10;
				}
				map[i][j] = num;
			}
		}
		// System.out.println("Returning map.");
		return map;
	}

	public void updateReachableLocations() throws Exception {
		long start = game.currentTimeMillis();
		long timeTook = (game.currentTimeMillis() - start);
		int[][] map = getMap(game, player);
		Point playerPoint = player.getCellPoint();
		AIStarPath aStarPath = null;
		aStarPath = AIStar.getAIStarPath(AI.aStarReachableLocationsSettings, map, playerPoint, new Point(-1, -1), 0, 0,
				10, 10, 100000, 100000, -1, 2, -2, 100000, -3, 100000, -4, 2, -5, 100000, -10, 0, -11, 0, -12, 0, -13,
				0, -14, 2, -15, 0, -16, 2, -17, 2, -18, 2, -19, 2, -20, 2, -21, 0, -22, 10, -23, 10, -24, 10, -25, 0,
				-26, 0, -27, 0);
		aStarPath.startAI(currentPath, pathFindingThread);
		timeTook = aStarPath.getTime();
		if (timeTook > 300)
			System.err.println("Took " + timeTook + "ms to calculate astar!");
		boolean newReachableLocations[][] = new boolean[game.stage.getHeight()][game.stage.getWidth()];
		long newReachableExplodableTiles[][] = new long[game.stage.getHeight()][game.stage.getWidth()];
		aStarPath.tiles.add(new Point(playerPoint.x, playerPoint.y));
		ArrayList<ArrayList<Point>> possibleExplosions = new ArrayList<ArrayList<Point>>();
		for (Point tile : aStarPath.tiles) {
			newReachableLocations[tile.x][tile.y] = true;
			ArrayList<Point> possibleExplosionsOnPoint = new ArrayList<Point>();
			possibleExplosionsOnPoint.add(tile);
			int adj = getBombAdjustmentValues(map, tile.x, tile.y, tile, tile, aStarPath, possibleExplosionsOnPoint);
			newReachableExplodableTiles[tile.x][tile.y] += adj;
			if (adj < 0 && newReachableExplodableTiles[tile.x][tile.y] < 0)
				newReachableExplodableTiles[tile.x][tile.y] = 0;
			boolean[] foundExplodableTile = new boolean[4];
			for (int i = 1; i <= player.firePower; i++) {
				Point up = new Point(tile.x, tile.y - i);
				Point down = new Point(tile.x, tile.y + i);
				Point left = new Point(tile.x - i, tile.y);
				Point right = new Point(tile.x + i, tile.y);
				if (!foundExplodableTile[0] && up.y > -1) {
					possibleExplosionsOnPoint.add(new Point(up.x, up.y));
					if (map[up.x][up.y] != -1) {
						long before = newReachableExplodableTiles[tile.x][tile.y];
						newReachableExplodableTiles[tile.x][tile.y] += getBombAdjustmentValues(map, up.x, up.y, null,
								tile, aStarPath, possibleExplosionsOnPoint);
						if (before != newReachableExplodableTiles[tile.x][tile.y] || map[up.x][up.y] == -2)
							foundExplodableTile[0] = true;
					}
				}
				if (!foundExplodableTile[1] && down.y < newReachableExplodableTiles[0].length) {
					possibleExplosionsOnPoint.add(new Point(down.x, down.y));
					if (map[down.x][down.y] != -1) {
						long before = newReachableExplodableTiles[tile.x][tile.y];
						newReachableExplodableTiles[tile.x][tile.y] += getBombAdjustmentValues(map, down.x, down.y,
								null, tile, aStarPath, possibleExplosionsOnPoint);
						if (before != newReachableExplodableTiles[tile.x][tile.y] || map[down.x][down.y] == -2)
							foundExplodableTile[1] = true;
					}
				}
				if (!foundExplodableTile[2] && left.x > -1) {
					possibleExplosionsOnPoint.add(new Point(left.x, left.y));
					if (map[left.x][left.y] != -1) {
						long before = newReachableExplodableTiles[tile.x][tile.y];
						newReachableExplodableTiles[tile.x][tile.y] += getBombAdjustmentValues(map, left.x, left.y,
								null, tile, aStarPath, possibleExplosionsOnPoint);
						if (before != newReachableExplodableTiles[tile.x][tile.y] || map[left.x][left.y] == -2)
							foundExplodableTile[2] = true;
					}
				}
				if (!foundExplodableTile[3] && right.x < newReachableExplodableTiles.length) {
					possibleExplosionsOnPoint.add(new Point(right.x, right.y));
					if (map[right.x][right.y] != -1) {
						long before = newReachableExplodableTiles[tile.x][tile.y];
						newReachableExplodableTiles[tile.x][tile.y] += getBombAdjustmentValues(map, right.x, right.y,
								null, tile, aStarPath, possibleExplosionsOnPoint);
						if (before != newReachableExplodableTiles[tile.x][tile.y] || map[right.x][right.y] == -2)
							foundExplodableTile[3] = true;
					}
				}
			}
			possibleExplosions.add(possibleExplosionsOnPoint);
		}
		for (int k = 0; k < aStarPath.tiles.size(); k++) {
			// Make sure we don't trap ourselves. To do this, we make a few
			// assumptions. We don't have to run astar again because we already
			// have the amount of walkable tiles. We're able to walk in any
			// direction from a bomb we place. By marking every "fire" tile and
			// the bomb tile as dangerous, as long as one walkable tile remains,
			// we can reach it.
			Point tile = aStarPath.tiles.get(k);
			ArrayList<Point> possibleExplosionsOnPoint = possibleExplosions.get(k);
			boolean testMap[][] = new boolean[game.stage.getHeight()][game.stage.getWidth()];
			for (int i = 0; i < testMap.length; i++)
				for (int j = 0; j < testMap[i].length; j++)
					testMap[i][j] = new Boolean(newReachableLocations[i][j]);
			for (int i = 0; i < map.length; i++)
				for (int j = 0; j < map[i].length; j++)
					if (map[i][j] >= 0)
						testMap[i][j] = false;
			for (Point explosion : possibleExplosionsOnPoint)
				testMap[explosion.x][explosion.y] = false;
			int movementSpotsCount = 0;
			for (int i = 0; i < testMap.length; i++)
				for (int j = 0; j < testMap[i].length; j++)
					if (testMap[i][j])
						movementSpotsCount++;
			if (movementSpotsCount == 0)
				newReachableExplodableTiles[tile.x][tile.y] = 0;
		}
		// Kill outliers that are +1 everything else
		int[] amtCount = new int[10];
		int total = 0;
		for (Point tile : aStarPath.tiles) {
			Point p = new Point(tile.x, tile.y);
			double explode = newReachableExplodableTiles[p.x][p.y];
			if (explode < 2)
				continue;
			boolean found = false;
			for (int i = 0; i < amtCount.length && !found; i++)
				if (explode < i) {
					found = true;
					amtCount[i]++;
				}
			if (!found)
				amtCount[amtCount.length - 1]++;
			total++;
		}
		double[] percentCount = new double[amtCount.length];
		for (int i = 0; i < percentCount.length; i++)
			percentCount[i] = ((double) amtCount[i] / (double) total) * 100d;
		int highestOverTenPercent = 0;
		for (int i = 0; i < percentCount.length; i++)
			if (percentCount[i] >= 10)
				highestOverTenPercent = i;
		if (highestOverTenPercent != 0)
			for (Point tile : aStarPath.tiles) {
				Point p = new Point(tile.x, tile.y);
				if (newReachableExplodableTiles[p.x][p.y] <= highestOverTenPercent + 1
						&& newReachableExplodableTiles[p.x][p.y] > highestOverTenPercent) {
					// System.out.println("WOW it worked! (" + p.x + "," + p.y
					// + ") was gonna be (" +
					// newReachableExplodableTiles[p.x][p.y]
					// + ") but we're reducing it to (" + highestOverTenPercent
					// + ") because it was an outlier.");
					newReachableExplodableTiles[p.x][p.y] = highestOverTenPercent;
				}
			}
		// Done killing outliers
		long biggestExplosion = -1;
		double closestExplosion = -1;
		Point biggestExplosionPoint = null;
		for (Point tile : aStarPath.tiles) {
			// Do distance modification
			Point p = new Point(tile.x, tile.y);
			if (newReachableExplodableTiles[p.x][p.y] > 0) {
				double explosion = newReachableExplodableTiles[p.x][p.y];
				double dist = p.distance(playerPoint);
				for (int i = distanceModifier; i < 50; i += distanceModifier)
					if (dist > i && explosion > 1)
						explosion -= 0.6;
				if (biggestExplosionPoint == null || biggestExplosion <= explosion) {
					if (closestExplosion == -1 || closestExplosion > dist || biggestExplosion < explosion) {
						biggestExplosionPoint = p;
						closestExplosion = dist;
						biggestExplosion = (long) explosion;
					}
				}
			} else
				newReachableExplodableTiles[p.x][p.y] = 0;
		}
		// if (biggestExplosionPoint != null)
		timeTook = (game.currentTimeMillis() - start);
		if (disableSafeMovingUntil > game.currentTimeMillis() || !staySafe()) {
			if (tempSpeedRatio != speedRatio)
				speedRatio = tempSpeedRatio;
			goingToExplodePoint = biggestExplosionPoint;
			int newDirectionToPress = getDirectionToTile(game, player, goingToExplodePoint, this);
			if (directionToPress == -1 && newDirectionToPress == -1) {

			} else
				directionToPress = newDirectionToPress;
		} else
			goingToExplodePoint = null;
		long thisTimeTook = (game.currentTimeMillis() - start);
		if (thisTimeTook - timeTook > 300)
			System.err.println("Took " + (thisTimeTook - timeTook) + "ms to calculate stay safe method!");
		reachableLocations = newReachableLocations;
		reachableExplodableTiles = newReachableExplodableTiles;
		// for (int i = 0; i < 2; i++)
		// for (int j = 0; j < 2; j++) {
		// System.out.println("reachableLocations(" + i + "," + j + "): "
		// + reachableLocations[i][j]);
		// }
		// for (int i = 0; i < 2; i++)
		// for (int j = 0; j < 2; j++) {
		// System.out.println("reachableExplodableTiles(" + i + "," + j + "): "
		// + reachableExplodableTiles[i][j]);
		// }
		// System.out.println("goingToExplodePoint: " + goingToExplodePoint);
		// System.out.println("map returned.");
		timeTook = (game.currentTimeMillis() - start);
		if (timeTook > 1000)
			System.err.println("FINISHED: Took " + timeTook + " ms to compute");
		else if (timeTook > 500)
			System.out.println("FINISHED: Took " + timeTook + " ms to compute");
	}

	public int getBombAdjustmentValues(int[][] map, int mapx, int mapy, Point pointOnMap, Point bombOrigin,
			AIStarPath aStarPath, ArrayList<Point> possibleExplosions) {
		long mapValue = map[mapx][mapy];
		if (pointOnMap == null)
			pointOnMap = new Point(mapx, mapy);
		boolean isOriginOfBombSpot = bombOrigin.distance(pointOnMap) == 0;
		int adjustment = 0;
		ArrayList<Player> alivePlayers = Stage
				.getAlivePlayersOnTileExcludingPlayer(new Point(pointOnMap.x, pointOnMap.y), player, game);
		if (alivePlayers.size() > 0) {
			adjustment += playerHunterLevel;
			if (!isOriginOfBombSpot && trapEnemies) {
				// Value trapping enemies highly. We gotta make a lot of
				// assumptions. First off, since this is the fire touching the
				// enemy, the fire will stop, so we don't need to wait for all
				// fire paths to complete, we're assuming the path already found
				// would be the path blocking the enemy.
				// Then we find all walkable tiles in the current astar-list
				// that the player could walk to. If we can walk to him, we
				// already have his walkable tiles.
				// If our fire line blocks all of his walkable tiles, then he
				// will be stuck if we place the bomb, as our fireline ends with
				// our bomb and is naturally unpassable.

				ArrayList<Point> walkableTiles = new ArrayList<Point>();
				Point middle = new Point(pointOnMap.x, pointOnMap.y);
				Point left = new Point(middle.x - 1, middle.y);
				Point right = new Point(middle.x + 1, middle.y);
				Point up = new Point(middle.x, middle.y - 1);
				Point down = new Point(middle.x, middle.y + 1);
				for (Point p : aStarPath.tiles)
					if (p.distance(left) == 0 || p.distance(right) == 0 || p.distance(up) == 0 | p.distance(down) == 0)
						walkableTiles.add(p);
				if (walkableTiles.size() == 0) {
					// ignore them, they're already trapped or something
				} else {
					for (Point p : possibleExplosions)
						for (int i = 0; i < walkableTiles.size(); i++)
							if (p.distance(walkableTiles.get(i)) == 0) {
								walkableTiles.remove(i);
								i--;
							}
					if (walkableTiles.size() == 0) {
						// We can block somebody in and kill them. Let's value
						// that highly.
						adjustment += playerHunterLevel * 2;
					} else {
						// This won't block them :(
					}
				}
			}
		}
		if (mapValue == -3 || (mapValue == -23 && !isOriginOfBombSpot))
			adjustment++;
		if (isOriginOfBombSpot) {
			// Looking at the potential of walking on this spot
			if (mapValue == -23) {
				// skull
				adjustment -= 2;
			} else if (mapValue == -22) {
				// sandals
				adjustment -= 1;
			} else if (mapValue == -15) {
				// max fire
				adjustment += 9 - player.firePower + 2;
			} else if (mapValue == -24) {
				// devil
				adjustment += 2;
			} else if (mapValue == -26) {
				// egg
				adjustment += 10;
			} else if (mapValue <= -10) {
				// other item
				adjustment += 1 + (playerHunterLevel / 2);
				// Don't really want it to scale with player hunter level, this
				// is more so player-hungry bots don't ignore items
			}
			if (player.getCellPoint().distance(pointOnMap) != 0) {
				// If items are on the nearby squares, prioritize going here.
				// Don't want to actually put a bomb here though, so don't do
				// this if we're already on the spot (pick up the item instead
				// of laying down a bomb to destroy it)
				long mapValueUp = 0;
				long mapValueDown = 0;
				long mapValueLeft = 0;
				long mapValueRight = 0;
				try {
					mapValueUp = map[mapx][mapy - 1];
				} catch (Exception e) {
				}
				try {
					mapValueDown = map[mapx][mapy + 1];
				} catch (Exception e) {
				}
				try {
					mapValueLeft = map[mapx - 1][mapy];
				} catch (Exception e) {
				}
				try {
					mapValueRight = map[mapx + 1][mapy];
				} catch (Exception e) {
				}
				long[] mapValues = new long[] { mapValueUp, mapValueDown, mapValueLeft, mapValueRight };
				for (int i = 0; i < mapValues.length; i++) {
					long m = mapValues[i];
					if (m <= -10 && m != -23 && m != -22) {
						// Not a skull or sandals
						// Has to outweigh the below decrease for blowing the
						// item up
						adjustment += 5 + (playerHunterLevel / 2);
						// Don't really want it to scale with player hunter
						// level, this is more so player-hungry bots don't
						// ignore items
					}
				}
			}
		} else {
			// Looking at blowing this tile up
			if (mapValue == -23) {
				// skull
				adjustment += 1;
			} else if (mapValue == -22) {
				// sandals
				adjustment += 1;
			} else if (mapValue == -15) {
				// max fire
				adjustment -= 5;
			} else if (mapValue == -24) {
				// devil
				adjustment -= 1;
			} else if (mapValue == -26) {
				// egg
				adjustment -= 10;
			} else if (mapValue <= -10) {
				// other item
				adjustment -= 2;
			}
		}
		if (mapValue > 0)
			adjustment -= 2;
		return adjustment;
	}

	public void drawReachableLocations(Graphics2D g) {
		// TODO make look better
		try {
			if (player.deadTimer != -1)
				return;
			g.setFont(game.defaultFont);
			if (reachableLocations != null)
				for (int i = 0; i < reachableLocations.length; i++)
					for (int j = 0; j < reachableLocations[i].length; j++)
						if (reachableLocations[i][j]) {
							// Point mid = Stage.getExactTileMidPoint(game, new
							// Point(j, i));
							g.setColor(new Color(150, 0, 150, 20));
							// g.fillRect(mid.y - 15, mid.x - 15, 30, 30);
						}
			if (reachableExplodableTiles != null)
				for (int i = 0; i < reachableExplodableTiles.length; i++)
					for (int j = 0; j < reachableExplodableTiles[i].length; j++) {
						if (reachableExplodableTiles[i][j] != 0) {
							// Point mid = Stage.getExactTileMidPoint(game, new
							// Point(j, i));
							g.setColor(new Color(150, 150, 0, 20));
							// g.fillRect(mid.y - 15, mid.x - 15, 30, 30);
						}
						if (reachableExplodableTiles[i][j] != 0) {
							g.setColor(new Color(0, 0, 255, 255));
							Point mid = Stage.getExactTileMidPoint(game, new Point(j, i));
							g.drawString("" + reachableExplodableTiles[i][j], mid.y - 10, mid.x + 7);
						}
					}
			Graphics2D g2d = (Graphics2D) g;
			g2d.setStroke(new BasicStroke(10));
			DoublePoint p = player.getExactPoint();
			if (goingToExplodePoint != null) {
				Point mid = Stage.getExactTileMidPoint(game, new Point(goingToExplodePoint.x, goingToExplodePoint.y));
				g.setColor(new Color(255, 0, 50, 200));
				// g.fillRect(mid.y - 15, mid.x - 15, 30, 30);
				g2d.draw(new Line2D.Double(mid.x - 15, mid.y + 15, p.x + 15, p.y + 40));
			}
			if (goingToSafePoint != null) {
				Point mid = Stage.getExactTileMidPoint(game, new Point(goingToSafePoint.x, goingToSafePoint.y));
				g.setColor(new Color(255, 255, 0, 150));
				// g.fillRect(mid.y - 15, mid.x - 15, 30, 30);
				g2d.draw(new Line2D.Double(mid.x - 15, mid.y + 15, p.x + 15, p.y + 40));
			}
		} catch (Exception e) {
		}
	}

	/**
	 * 
	 * @param canPlaceBombs
	 *            Whether or not we're allowed to place bombs (ie. skull)
	 * @param canMove
	 *            Whether or not we're allowed to move (ie. not being host of game)
	 */
	public void tick(boolean canPlaceBombs, boolean canMove) {
		if (!aiEnabled)
			return;
		getReachableLocations();
		if (reachableLocations == null && reachableLocationsThread != null && reachableLocationsThread.isAlive()) {
			// System.out.println("wait for location thread");
			return;
		}
		if (directionToPress == -1)
			player.resetControls();
		else {
			Point goingTo = goingToExplodePoint;
			if (goingTo == null)
				goingTo = goingToSafePoint;
			if (goingTo != null && goingTo.distance(player.getCellPoint()) <= 1) {
				int newDirectionToPress = getDirectionToTile(game, player, goingTo, this);
				if (newDirectionToPress == -1 && directionToPress != -1) {
				} else if (directionToPress != newDirectionToPress)
					directionToPress = newDirectionToPress;
			}
		}
		if (placingBomb) {
			player.cPressed = false;
			goingToExplodePoint = null;
			placingBomb = false;
			directionToPress = -1;
		}
		if (goingToExplodePoint != null && walkToBombPointProactively && canMove && directionToPress != -1) {
			long[][] collision = game.stage.getCollisionMap(game);
			boolean walked = false;
			try {
				if (collision[goingToExplodePoint.y][goingToExplodePoint.x] == -1)
					walked = goToTile(game, player, goingToExplodePoint, directionToPress);
			} catch (Exception e) {
				e.printStackTrace();
				System.err.println("AI issue " + goingToExplodePoint);
			}
			if (walked)
				return;
		}
		if (!staySafe(canMove) && canPlaceBombs) {
			if (player.pickedUpBomb != null) {
				// We are carrying a bomb
				// TODO throw at people? right now we just throw immediately.
				player.aPressed = true;
				return;
			} else if (player.powerGlove) {
				// TODO Make use of power glove
			}
			if (player.bombAmt <= player.getBombsLaidAmt() && !walkToBombPointProactively)
				return;
			if (goingToExplodePoint == null) {
				disableSafeMovingUntil = -1;
				keepMovingToBombUntil = -1;
				return;
			} else if (goingToExplodePoint.distance(player.getCellPoint()) == 0
					&& keepMovingToBombUntil <= game.currentTimeMillis()) {
				// place bomb!
				placingBomb = true;
				player.cPressed = true;
				disableSafeMovingUntil = -1;
				keepMovingToBombUntil = -1;
			} else {
				ArrayList<Bomb> bombs = new ArrayList<Bomb>();
				for (int i = 0; i < game.players.size(); i++)
					try {
						if (!game.players.get(i).equals(player))
							for (int j = 0; j < game.players.get(i).getBombsLaid().size(); j++)
								bombs.add(game.players.get(i).getBombsLaid().get(j));
					} catch (Exception e) {
						// concurrent exception, retry
						i = -1;
						bombs = new ArrayList<Bomb>();
					}
				boolean bombOnOurSpot = false;
				for (Bomb bomb : bombs)
					try {
						if (bomb.getCellPoint().distance(goingToExplodePoint) == 0)
							bombOnOurSpot = true;
					} catch (Exception e) {
					}
				if (bombOnOurSpot) {
					// Someone already put a bomb there. Gotta make a different
					// decision.
					goingToExplodePoint = null;
					disableSafeMovingUntil = -1;
				} else {
					// walk to bomb spot
					if (directionToPress != -1 && goToTile(game, player, goingToExplodePoint, directionToPress)) {
						disableSafeMovingUntil = game.currentTimeMillis() + 500;
						keepMovingToBombUntil = game.currentTimeMillis() + 2000;
					} else {
						disableSafeMovingUntil = -1;
						keepMovingToBombUntil = -1;
					}
				}
			}
		}
	}

	public void reset() {
		reachableLocations = null;
		reachableExplodableTiles = null;
		goingToExplodePoint = null;
		placingBomb = false;
		player.resetControls();
	}

	/**
	 * Attempts to move to stay safe.
	 * 
	 * @param canMove
	 *            Whether or not we are allowed to move.
	 * @return <b>true</b> if we had to move to stay safe, or are unable to
	 *         move.<br>
	 *         <b>false</b> if we are already safe.
	 */
	private boolean staySafe(boolean canMove) {
		if (goingToSafePoint != null) {
			if (canMove)
				goToTile(game, player, goingToSafePoint, directionToPress);
			return true;
		}
		return false;
	}

	private boolean staySafe() {
		Point loc = player.getCellPoint();
		// loc = new Point(loc.y, loc.x);
		int[][] map = getMap(game, player);
		long ourPosCollision = map[loc.x][loc.y];
		if (ourPosCollision != -4 && ourPosCollision != -1) {
			// Not just a player on the square, maybe danger or bomb?
			// game.logger.log(LogLevel.DEBUG,
			// System.out.println("We're in danger! Find a place to move. ("
			// + ourPosCollision + ")");
			getReachableLocations();
			Point closestSafePoint = null;
			if (directionToPress == 0 || directionToPress == 3
					|| (directionToPress == -1 && Tools.random(game, 0, 10, "Random direction of safety") >= 5)) {
				// We're currently travelling down or right. Maybe we're trying
				// to pass fire this direction - so let's sort the map backwards
				// to find the best safespot that's to the down-right instead of
				// upper-left
				for (int i = map.length - 1; i >= 0; i--)
					for (int j = map[i].length - 1; j >= 0; j--) {
						if (reachableLocations != null && !reachableLocations[i][j])
							continue;
						long collision = map[i][j];
						// System.out.println("checking spot (" + i + "," + j +
						// "): "
						// + collision);
						if (collision == -1 || (collision <= -10 && collision >= -30))
							if (closestSafePoint == null
									|| new Point(i, j).distance(loc) < loc.distance(closestSafePoint)) {
								// System.out.println("ITS GOOD!");
								closestSafePoint = new Point(i, j);
							} else {
								// System.out.println("its not close enough");
							}
					}
			} else
				for (int i = 0; i < map.length; i++)
					for (int j = 0; j < map[i].length; j++) {
						if (reachableLocations != null && !reachableLocations[i][j])
							continue;
						long collision = map[i][j];
						// System.out.println("checking spot (" + i + "," + j +
						// "): "
						// + collision);
						if (collision == -1 || (collision <= -10 && collision >= -30))
							if (closestSafePoint == null
									|| new Point(i, j).distance(loc) < loc.distance(closestSafePoint)) {
								// System.out.println("ITS GOOD!");
								closestSafePoint = new Point(i, j);
							} else {
								// System.out.println("its not close enough");
							}
					}
			if (closestSafePoint != null) {
				// Found a safe place to go
				// game.logger.log(LogLevel.DEBUG,
				// System.out.println("Found a safe place to go! (" +
				// closestSafePoint.x
				// + "," + closestSafePoint.y + ") our loc (" + loc.x + "," +
				// loc.y
				// + ")");
				goingToExplodePoint = null;
				goingToSafePoint = closestSafePoint;
				directionToPress = getDirectionToTile(game, player, closestSafePoint, this);
				keepMovingUntil = game.currentTimeMillis() + 2000;
			} else {
				// game.logger.log(LogLevel.ERROR,
				// "There's no safe place to go.. finding path of least
				// resistance.");
				ArrayList<Integer> possibleDirections = new ArrayList<Integer>();
				ArrayList<Long> collisionRatings = new ArrayList<Long>();
				ArrayList<Point> points = new ArrayList<Point>();
				long currentCollisionRating = map[player.getCellPoint().x][player.getCellPoint().y];
				for (int i = 0; i < map.length; i++)
					for (int j = 0; j < map[i].length; j++) {
						if (reachableLocations != null && !reachableLocations[i][j])
							continue;
						long rating = map[i][j];
						if (rating == -2 || rating == -3)
							continue;
						if (Math.abs(currentCollisionRating - rating) < 50)
							continue;
						int dir = getDirectionToTile(game, player, new Point(i, j), this);
						if (dir != -1) {
							possibleDirections.add(dir);
							collisionRatings.add(rating);
							points.add(new Point(i, j));
							// game.logger.log(LogLevel.DEBUG,
							// System.out.println("Got a possible move (" + i +
							// "," + j
							// + "," + dir + "," + map[i][j] + ")");
						}
					}
				// game.logger.log(LogLevel.DEBUG,
				// "Got all possible moves, sorting and finding best one..");
				Point goingToPoint = null;
				int bestMove = -1;
				for (int i = 0; i < collisionRatings.size(); i++)
					if (bestMove == -1 || collisionRatings.get(i) > collisionRatings.get(bestMove)) {
						bestMove = i;
						goingToPoint = points.get(i);
					}
				if (bestMove == -1) {
					boolean tryingToKickBomb = false;
					if (player.kickBomb && kickBombsToStayAlive) {
						ArrayList<Point> reachable = new ArrayList<Point>();
						reachable.add(player.getCellPoint());
						for (int i = 0; i < map.length; i++)
							for (int j = 0; j < map[i].length; j++) {
								if (reachableLocations != null && !reachableLocations[i][j])
									continue;
								Point p = new Point(i, j);
								if (!reachable.contains(p))
									reachable.add(p);
							}
						for (int i = 0; i < reachable.size(); i++) {
							Point reach = reachable.get(i);
							Point top = new Point(0, -1);
							Point down = new Point(0, 1);
							Point left = new Point(-1, 0);
							Point right = new Point(1, 0);
							Point[] allPoints = new Point[] { top, down, left, right };
							for (int j = 0; j < allPoints.length; j++) {
								Point thisDir = allPoints[j];
								Point p = new Point(reach.x + thisDir.x, reach.y + thisDir.y);
								Bomb b = Bomb.get(p, game);
								if (b != null) {
									Point kickedPoint = new Point(reachable.get(i).x + thisDir.x * 2,
											reachable.get(i).y + thisDir.y * 2);
									if (kickedPoint.x < 0 || kickedPoint.x > game.stage.get()[0].length
											|| kickedPoint.y < 0 || kickedPoint.y > game.stage.get().length) {
										continue;
									}
									int tile = game.stage.get()[kickedPoint.y][kickedPoint.x];
									if (tile == -1) {
										tryingToKickBomb = true;
										goingToSafePoint = p;
										if (player.getCellPoint().distance(p) == 1) {
											if (thisDir.x == 1)
												directionToPress = 3; // right
											else if (thisDir.x == -1)
												directionToPress = 2; // left
											else if (thisDir.y == 1)
												directionToPress = 0; // down
											else if (thisDir.y == -1)
												directionToPress = 1; // up
											else {
											}
										} else {
											goingToSafePoint = reach;
											directionToPress = getDirectionToTile(game, player, reach, this);
										}
									}
								}
							}
						}
					}
					if (!tryingToKickBomb) {
						// game.logger.log(LogLevel.DEBUG,
						// System.out.println("Damn, not a single move? We're
						// dead meat.");
						// goingToSafePoint = player.getCellPoint();
						goingToSafePoint = new Point(-1, -1);
						directionToPress = -1;
					}
				} else {
					// game.logger.log(LogLevel.DEBUG,
					// System.out.println("Got the best move of a shitty
					// situation! ("
					// + possibleDirections.get(bestMove) + ","
					// + collisionRatings.get(bestMove) + ")");
					goingToSafePoint = goingToPoint;
					directionToPress = possibleDirections.get(bestMove);
				}
			}
		} else if (game.currentTimeMillis() < keepMovingUntil) {
			int dir = getDirectionToTile(game, player, player.getCellPoint(), this);
			if (dir != -1) {
				goingToSafePoint = player.getCellPoint();
				directionToPress = dir;
			} else {
				goingToSafePoint = null;
				directionToPress = -1;
				keepMovingUntil = game.currentTimeMillis();
				if (tempSpeedRatio != speedRatio)
					speedRatio = tempSpeedRatio;
			}
			// Move closer to center of tile
			if (keepMovingUntil - game.currentTimeMillis() < 1500 && tempSpeedRatio == speedRatio && speedRatio > 100) {
				tempSpeedRatio = speedRatio;
				speedRatio = 50;
			}
		} else {
			// System.out.println("We're safe!");
			if (goingToSafePoint != null && goingToExplodePoint == null)
				directionToPress = -1;
			goingToSafePoint = null;
			player.resetControls();
			return false;
		}
		return true;
	}

	@SuppressWarnings("unused")
	private void randomMovement(boolean canPlaceBombs) {
		int move = Tools.random(game, 0, 1000, "AI");
		if (move == 0)
			player.aPressed = true;
		else if (move == 1)
			player.bPressed = true;
		else if (move == 2 && canPlaceBombs)
			player.cPressed = true;
		else if (move < 100)
			player.upPressed = true;
		else if (move < 200)
			player.downPressed = true;
		else if (move < 300)
			player.leftPressed = true;
		else if (move < 400)
			player.rightPressed = true;
		else if (move < 600) {
			player.resetControls();
		}
	}

	public boolean moveInDirection(Player player, int dir) {
		if (dir == -1)
			return false;
		if (dir == 0)
			player.downPressed = true;
		else if (dir == 1)
			player.upPressed = true;
		else if (dir == 2)
			player.leftPressed = true;
		else if (dir == 3)
			player.rightPressed = true;
		else if (dir == 4) {
			player.upPressed = true;
			player.rightPressed = true;
		} else if (dir == 5) {
			player.downPressed = true;
			player.leftPressed = true;
		} else if (dir == 6) {
			player.downPressed = true;
			player.rightPressed = true;
		} else if (dir == 7) {
			player.upPressed = true;
			player.leftPressed = true;
		}
		return true;
	}

	public boolean goToTile(MainBomberman game, Player player, Point goal, int direction) {
		if (direction != -1)
			if (!tryingToMove) {
				tryingToMove = true;
				tryingToMoveTime = game.currentTimeMillis();
				tryingToMoveStartPoint = player.getCellPoint();
				tryingToMoveEndPoint = goal;
				tryingToMoveExactPoint = player.getExactPoint();
			} else if (tryingToMoveExactPoint.distance(player.getExactPoint()) < 8
					&& tryingToMoveEndPoint.distance(goal) == 0 && game.currentTimeMillis() - tryingToMoveTime > 500) {
				int lastDir = new Integer(direction);
				while (lastDir == direction)
					direction = Tools.random(null, 7, "Get unstuck");
			} else if (tryingToMoveEndPoint.distance(goal) > 0
					|| tryingToMoveExactPoint.distance(player.getExactPoint()) >= 8) {
				tryingToMove = false;
			}
		player.resetControls();
		return moveInDirection(player, direction);
	}

	public boolean goToTileOld(MainBomberman game, Player player, Point goal) {
		// System.out.println("player (" + player.getCellPoint() +
		// ") trying to go to (" + goal + ")");
		int dir = getDirectionToTile(game, player, goal, this);
		// System.out.println("dir: " + dir);
		if (dir != -1)
			if (!tryingToMove) {
				tryingToMove = true;
				tryingToMoveTime = game.currentTimeMillis();
				tryingToMoveStartPoint = player.getCellPoint();
				tryingToMoveEndPoint = goal;
				tryingToMoveExactPoint = player.getExactPoint();
			} else if (tryingToMoveExactPoint.distance(player.getExactPoint()) < 8
					&& tryingToMoveEndPoint.distance(goal) == 0 && game.currentTimeMillis() - tryingToMoveTime > 500) {
				int lastDir = new Integer(dir);
				while (lastDir == dir)
					dir = Tools.random(null, 7, "Get unstuck");
			} else if (tryingToMoveEndPoint.distance(goal) > 0
					|| tryingToMoveExactPoint.distance(player.getExactPoint()) >= 8) {
				tryingToMove = false;
			}
		player.resetControls();
		return moveInDirection(player, dir);
	}

	ArrayList<Point> lastSearchedStart = new ArrayList<Point>();
	ArrayList<Point> lastSearchedEnd = new ArrayList<Point>();
	ArrayList<Long> lastSearchedTime = new ArrayList<Long>();
	ArrayList<Integer> lastSearchedDirection = new ArrayList<Integer>();
	long lastSearchedTimeCutoff = 300;

	public static int getDirectionToTile(MainBomberman game, Player player, Point end, AI aiClass) {
		try {
			if (end == null)
				return -1;
			Point start = player.getCellPoint();
			if (end.distance(start) == 0) {
				Point playerMid = player.getMiddleOfBodyPoint();
				Point stageMid = Stage.getExactTileMidPoint(game, start);
				stageMid = new Point(stageMid.x - 19, stageMid.y + 15);
				if (Math.abs(playerMid.x - stageMid.x) < 4 && Math.abs(playerMid.y - stageMid.y) < 4)
					return -1;
				// System.out.println("Adjusting (" + playerMid + ") to (" +
				// stageMid + ")");
				double xDist = Math.abs(playerMid.x - stageMid.x);
				double yDist = Math.abs(playerMid.y - stageMid.y);
				if (xDist >= yDist)
					if (stageMid.x > playerMid.x)
						return 3; // right
					else
						return 2; // left
				else if (stageMid.y > playerMid.y)
					return 0; // down
				else
					return 1; // up
			}
			for (int i = 0; i < aiClass.lastSearchedStart.size(); i++) {
				Point s = aiClass.lastSearchedStart.get(i);
				if (s.distance(start) == 0) {
					Point e = aiClass.lastSearchedEnd.get(i);
					if (e.distance(end) == 0) {
						long t = aiClass.lastSearchedTime.get(i);
						if (Math.abs(t - game.currentTimeMillis()) < aiClass.lastSearchedTimeCutoff) {
							return aiClass.lastSearchedDirection.get(i);
						}
					}
				}
			}
			for (int i = 0; i < 1 && i < aiClass.lastSearchedStart.size(); i++) {
				// Continue looping until the first one is recent
				long t = aiClass.lastSearchedTime.get(i);
				if (Math.abs(t - game.currentTimeMillis()) >= aiClass.lastSearchedTimeCutoff) {
					aiClass.lastSearchedStart.remove(i);
					aiClass.lastSearchedEnd.remove(i);
					aiClass.lastSearchedTime.remove(i);
					aiClass.lastSearchedDirection.remove(i);
					i--;
				}
			}
			int[][] map = getMap(game, player);
			// System.out.println("Trying to get from (" + start + ") to (" +
			// end +
			// ")");
			AIStarPath aStarPath = AIStar.getAIStarPath(AI.aStarDefaultSettings, map, start, end, 0, 0, 10, 10, 100000,
					100000, -1, 2, -2, 100000, -3, 100000, -4, 2, -5, 100000, -10, 0, -11, 0, -12, 0, -13, 0, -14, 1,
					-15, 0, -16, 1, -17, 1, -18, 1, -19, 1, -20, 1, -21, 0, -22, 10, -23, 10, -24, 10, -25, 0, -26, 0,
					-27, 0);
			aStarPath.startAI(aiClass.currentPath, aiClass.pathFindingThread);
			// for (int i = 0; i < aStarPath.tiles.size(); i++)
			// System.out.println("Got path tile: " + aStarPath.tiles.get(i));
			if (aStarPath.tiles.size() < 2)
				return -1;
			Point step = aStarPath.getNextPathPoint(start);
			if (step == null)
				return -1;
			// System.out.println("start (" + start + ") step (" + step + ")");
			// aStarPath.log();
			double xDist = Math.abs(start.x - step.x);
			double yDist = Math.abs(start.y - step.y);
			int answer = -1;
			if (xDist >= yDist)
				if (step.x > start.x)
					answer = 3; // right
				else
					answer = 2; // left
			else if (step.y > start.y)
				answer = 0; // down
			else
				answer = 1; // up
			aiClass.lastSearchedStart.add(start);
			aiClass.lastSearchedEnd.add(end);
			aiClass.lastSearchedTime.add(game.currentTimeMillis());
			aiClass.lastSearchedDirection.add(answer);
			return answer;
		} catch (Exception e) {
			e.printStackTrace();
			return -1;
		}
	}

	public void mouseMover(MainBomberman game) {
		String[] counterParse = ("" + game.counter[7]).split("97079");
		if (counterParse.length < 2)
			return;
		int num1 = -1, num2 = -1;
		if (counterParse[0].equals(""))
			num1 = 0; // it trims front zeroes
		else
			num1 = Integer.parseInt(counterParse[0]);
		num2 = Integer.parseInt(counterParse[1]);
		Point goingTo = new Point(num1, num2);
		goToTileOld(game, game.players.get(0), goingTo);
	}

	public static class AIStar extends AStar {
		public static AIStarPath getAIStarPath(int[][] map, Point start, Point end, float... numberWeights) {
			return new AIStarPath(map, start, end, numberWeights);
		}

		public static AIStarPath getAIStarPath(Settings settings, int[][] map, Point start, Point end,
				float... numberWeights) {
			AIStarPath path = new AIStarPath(map, start, end, numberWeights);
			path.setSettings(settings);
			return path;
		}
	}

	public static class AIStarPath extends AStarPath {

		Thread pathFinder = null;
		boolean finished = false;

		public AIStarPath(int[][] map, Point start, Point end, float[] numberWeights) {
			super(map, start, end, numberWeights);
		}

		public void startAI(AStarPath currentPath, Thread pathFindingThread) {
			if (currentPath != null && end.distance(currentPath.end) == 0) {
				closed = currentPath.closed;
				end = currentPath.end;
				map = currentPath.map;
				numbers = currentPath.numbers;
				open = currentPath.open;
				pathFound = currentPath.pathFound;
				settings = currentPath.settings;
				start = currentPath.start;
				tiles = currentPath.tiles;
				weights = currentPath.weights;
				pathFinder = pathFindingThread;
				System.out.println("Efficiency!");
				finished = true;
			} else {
				// pathFinder = new Thread(new PathFinder(this), "Path Finder");
				// pathFinder.run();
				start();
			}
		}

		public class PathFinder extends Thread {
			AIStarPath ourSuper = null;

			public PathFinder(Thread thread, String name) {
				super(thread, name);
			}

			public PathFinder(AIStarPath ourSuper) {
				this.ourSuper = ourSuper;
			}

			public void run() {
				ourSuper.start();
				finished = true;
			}
		}

		@Override
		protected float getG(Tile tile) {
			float amt = super.getG(tile);
			// System.out.println("getG: " + amt);
			return amt;
		}

		// Dunno if needed? old method
		protected float getCostB(float currentCost, int mapOriginalCost, float weight) {
			float originalAnswer = super.getCost(currentCost, mapOriginalCost, weight);
			// System.out.println("getCost(" + currentCost + "," +
			// mapOriginalCost + ","
			// + weight + ")");
			// System.out.println("getCost: " + mapOriginalCost);
			if (originalAnswer > 0 && originalAnswer <= 4500) {
				// Find all other bombs and rank ourselves against them
				// depending on how soon we will explode vs them.
				// System.out.println("we got a bomb! ahhh!!!!");
				int ourPlaceInBombs = 100;
				// long start = game.currentTimeMillis();
				ArrayList<Integer> checked = new ArrayList<Integer>();
				for (int i = 0; i < map.length; i++) {
					for (int j = 0; j < map[i].length; j++) {
						int m = map[i][j];
						if (m >= 0 && m <= 4500 && m < originalAnswer && !checked.contains(m)) {
							// System.out.println("found other, better bomb!");
							ourPlaceInBombs += 100;
							checked.add(m);
						} else if (m >= 0 && m <= 4500) {
							// System.out.println("found ourselves or wat?");
						}
					}
				}
				// if (game.currentTimeMillis() - start > 1)
				// System.out.println("took extra time on our bomb calculation..
				// ("
				// + (game.currentTimeMillis() - start) + "ms)");
				// System.out.println("changed our answer to: " +
				// ourPlaceInBombs);
				return ourPlaceInBombs;
			}
			return originalAnswer;
		}

	}
}
