package com.github.vegeto079.saturnbomberman.main;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

import com.github.vegeto079.ngcommontools.main.DoublePoint;
import com.github.vegeto079.ngcommontools.main.Logger;
import com.github.vegeto079.ngcommontools.main.Logger.LogLevel;
import com.github.vegeto079.ngcommontools.main.Tools;
import com.github.vegeto079.saturnbomberman.misc.Pictures;
import com.github.vegeto079.saturnbomberman.objects.Bomb;
import com.github.vegeto079.saturnbomberman.objects.Bomb.BombType;
import com.github.vegeto079.saturnbomberman.objects.Item;
import com.github.vegeto079.saturnbomberman.objects.Player;
import com.github.vegeto079.saturnbomberman.objects.Weight;

/**
 * 
 * @author Nathan
 * @version 1.0: Started tracking version.
 * @version 1.01: Character head at top of stage now blinks when you're skulled.
 * @version 1.02: Added {@link #getPlayersOnTile(Point)} and
 *          {@link #playerIsOnTile(Point, Player, MainBomberman)}.
 * @version 1.3: Changed all {@link System#currentTimeMillis()} references to
 *          {@link MainBomberman#currentTimeMillis()}.
 * @version 1.4: Collision squares of bombs now warps itself based off of the
 *          nearby player, so you don't get stuck in the bomb. Blocks no longer
 *          drawn here manually, instead they have their own classes:
 *          {@link com.github.vegeto079.saturnbomberman.objects#Block} and {@link com.github.vegeto079.saturnbomberman.objects#SolidBlock}.
 */
public class Stage {

	public final static int SQUARE_WIDTH = 32;
	public final static int SQUARE_HEIGHT = 32;

	private final int[][] ONE = {
			{ 11, 21, 01, 01, 01, 01, 01, 01, 01, 01, 01, 01, 01, 01, 01, 23, 13 },
			{ 21, 00, 01, 00, 25, 00, 01, 00, 01, 00, 01, 00, 27, 00, 01, 00, 23 },
			{ 01, 01, 01, 01, 25, 15, 25, 01, 01, 01, 27, 17, 27, 01, 01, 01, 01 },
			{ 01, 00, 01, 00, 01, 00, 25, 00, 01, 00, 27, 00, 01, 00, 01, 00, 01 },
			{ 01, 01, 01, 01, 01, 01, 01, 01, 01, 01, 01, 01, 01, 01, 01, 01, 01 },
			{ 01, 00, 01, 00, 01, 00, 01, 00, 01, 00, 01, 00, 01, 00, 01, 00, 01 },
			{ 01, 01, 01, 01, 01, 01, 01, 01, 01, 01, 01, 01, 01, 01, 01, 01, 01 },
			{ 01, 00, 01, 00, 28, 00, 01, 00, 01, 00, 01, 00, 26, 00, 01, 00, 01 },
			{ 01, 01, 01, 01, 28, 18, 28, 01, 01, 01, 26, 16, 26, 01, 01, 01, 01 },
			{ 24, 00, 01, 00, 01, 00, 28, 00, 01, 00, 26, 00, 01, 00, 01, 00, 22 },
			{ 14, 24, 01, 01, 01, 01, 01, 01, 01, 01, 01, 01, 01, 01, 01, 22, 12 } };
	private final int[][] TWO = ONE;
	private final int[][] THREE = { { 0 }, { 0 } };
	private final int[][] FOUR = { { 0 }, { 0 } };
	private final int[][] FIVE = { { 0 }, { 0 } };
	private final int[][] SIX = { { 0 }, { 0 } };
	private final int[][] SEVEN = { { 0 }, { 0 } };
	private final int[][] EIGHT = { { 0 }, { 0 } };
	private final int[][] NINE = { { 0 }, { 0 } };

	private int[][] stage;
	public int whichStage = -1;

	public boolean initiated = false;

	private long lastCollisionMapUpdate = 0;
	private long timeBetweenCollisionMapUpdates = 100;
	private long[][] lastCollisionMap = null;

	public Stage(int whichStage, MainBomberman game) {
		initialize(whichStage, game);
	}

	public Stage(boolean fromOurselves, int whichStage, MainBomberman game) {
		if (!fromOurselves)
			initialize(whichStage, game);
	}

	private void initialize(int whichStage, MainBomberman game) {
		this.whichStage = whichStage;
		game.loading = true;
		// game.state.set(StateEnum.LOADING, game);
		stage = new Stage(true, whichStage, game).getOriginalMap(whichStage);
		game.logger.log(LogLevel.DEBUG, "Loading stage " + whichStage);
		game.pictures.loadStageToMemory(whichStage);
		for (int i = 0; i < game.objects.size(); i++)
			if (game.objects.get(i) instanceof com.github.vegeto079.saturnbomberman.objects.Player) {
				// Set to normal conditions
				Player p = (com.github.vegeto079.saturnbomberman.objects.Player) game.objects.get(i);
				p.reset();
				p.stats.newStage();
			} else {
				// Remove any objects from previous games
				game.objects.remove(i);
				i--;
			}
		for (int i = 0; i < stage.length; i++)
			for (int j = 0; j < stage[i].length; j++)
				if (stage[i][j] >= 21 && stage[i][j] <= 29) {
					if (stage[i][j] - 20 > game.players.size())
						stage[i][j] = 1;
					else
						stage[i][j] = -1;
				} else if (stage[i][j] >= 11 && stage[i][j] <= 19) {
					if (stage[i][j] - 10 > game.players.size())
						stage[i][j] = 1;
					else {
						if (game.options.shuffle) {
							while (true) {
								int player = Tools.random(game, 0,
										game.players.size() - 1,
										"Shuffling player locations");
								if (game.players.get(player).exactPoint
										.distance(new Point(-1, -1)) == 0) {
									game.players.get(player).exactPoint = new DoublePoint(
											j * SQUARE_WIDTH + getOffset().x + 2,
											(i - 1) * SQUARE_HEIGHT + getOffset().y + 2);
									game.logger.log(LogLevel.DEBUG, "Shuffled Player ("
											+ player + ") to : (" + i + "," + j + ")");
									break;
								}
							}
						} else {
							game.players
									.get(stage[i][j] - 11).exactPoint = new DoublePoint(
											j * SQUARE_WIDTH + getOffset().x + 2,
											(i - 1) * SQUARE_HEIGHT + getOffset().y + 2);
						}
						stage[i][j] = -1;
					}
				}
		int removeBlocks = Tools.random(game, 10, 18, "Remove Blocks Amount");
		while (removeBlocks > 0) {
			int randomI = Tools.random(game, 0, stage.length - 1,
					"Removing Blocks I [" + removeBlocks + "]");
			int randomJ = Tools.random(game, 0, stage[randomI].length - 1,
					"Removing Blocks J [" + removeBlocks + "]");
			if (stage[randomI][randomJ] == 1) {
				removeBlocks--;
				game.logger.log(LogLevel.DEBUG,
						"Removed Block: (" + randomI + "," + randomJ + ")");
				stage[randomI][randomJ] = -1;
			}
		}
		for (int i = 0; i < stage.length; i++)
			for (int j = 0; j < stage[i].length; j++) {
				if (stage[i][j] == 1) {
					game.objects.add(new com.github.vegeto079.saturnbomberman.objects.Block(i, j));
				} else if (stage[i][j] == 0) {
					game.objects.add(new com.github.vegeto079.saturnbomberman.objects.SolidBlock(i, j));
				}
			}
		int thisI = -1, thisJ = -1;
		boolean[][] itemDetermined = new boolean[stage.length][stage[0].length];
		while (true) {
			thisI = Tools.random(game, 0, stage.length - 1,
					"Random item placement (picking random tile, i)");
			thisJ = Tools.random(game, 0, stage[0].length - 1,
					"Random item placement (picking random tile, j)");
			if (itemDetermined[thisI][thisJ]) {
				boolean foundNonDetermined = false;
				for (int i = 0; i < itemDetermined.length; i++)
					for (int j = 0; j < itemDetermined[i].length; j++)
						if (!itemDetermined[i][j])
							foundNonDetermined = true;
				if (!foundNonDetermined)
					break;
				continue;
			}
			if (stage[thisI][thisJ] == 1) {
				if (Tools.random(game,
						"Random item placement (no item chance)") >= .57f) {
					com.github.vegeto079.saturnbomberman.objects.Item newItem = new com.github.vegeto079.saturnbomberman.objects.Item(new Point(thisJ, thisI), game,
							getOffset(), whichStage);
					game.objects.add(newItem);
				}
			}
			itemDetermined[thisI][thisJ] = true;
		}
		// for(int i=0;i<stage.length;i++)
		// for(int j=0;j<stage[i].length;j++)
		// if(stage[i][j] == 1)
		// stage[i][j] = -1;
		// TODO: set above to clear stage of breakable blocks
		updateCollisionMap(game);
		initiated = true;
		game.loading = false;
	}

	public int[][] get() {
		return stage;
	}

	public void set(int x, int y, int set) {
		stage[x][y] = set;
	}

	public int[][] getOriginalMap(int whichStage) {
		if (whichStage == 0)
			return ONE;
		else if (whichStage == 1)
			return TWO;
		else if (whichStage == 2)
			return THREE;
		else if (whichStage == 3)
			return FOUR;
		else if (whichStage == 4)
			return FIVE;
		else if (whichStage == 5)
			return SIX;
		else if (whichStage == 6)
			return SEVEN;
		else if (whichStage == 7)
			return EIGHT;
		else if (whichStage == 8)
			return NINE;
		else
			return null;
	}

	public int getIndex(int x, int y) {
		return stage[x][y];
	}

	public int getIndex(Point p) {
		return stage[p.x][p.y];
	}

	public int getWidth() {
		return stage.length;
	}

	public int getHeight() {
		return stage[0].length;
	}

	public static int getTileWidth() {
		return SQUARE_WIDTH;
	}

	public static int getTileHeight() {
		return SQUARE_HEIGHT;
	}

	public static Dimension getTileSize() {
		return new Dimension(SQUARE_WIDTH, SQUARE_HEIGHT);
	}

	public Point getTileWithNumber(int num) {
		return getUnoccupiedTileWithNumber(num, null);
	}

	public Point getUnoccupiedTileWithNumber(int num, MainBomberman game) {
		for (int i = 0; i < stage.length; i++)
			for (int j = 0; j < stage[i].length; j++) {
				// Common.log(null, "Comparing num(" + num + ") to map[" + i
				// + "][" + j + "]: " + map[i][j]);
				if (stage[i][j] == num) {
					boolean found = false;
					if (game.players != null)
						for (int k = 0; k < game.players.size(); k++)
							if (game.players.get(k).getCellPoint()
									.distance(new Point(j, i)) == 0)
								found = true;
					// Common.log(null, "Number " + num + " found, point: " +
					// new Point(j, i));
					if (found)
						continue;
					return new Point(j, i);
				}
			}
		// Common.log(null, "Number " + num + " not found, returning null.");
		return null;
	}

	/**
	 * Returns tile given coordinates (such as a mouse click)
	 */
	public Point getTileAtActualPoint(Point p, MainBomberman game) {
		for (int i = 0; i < stage.length; i++)
			for (int j = 0; j < stage[i].length; j++) {
				Rectangle rect = new Rectangle(
						j * SQUARE_WIDTH + game.stage.getOffset().x,
						i * SQUARE_HEIGHT + game.stage.getOffset().y, SQUARE_WIDTH,
						SQUARE_HEIGHT);
				if (rect.contains(p))
					return new Point(j, i);
			}
		return null;
	}

	public void changeTileWithNumber(int num, int changeTo) {
		Point tile = getTileWithNumber(num);
		if (tile != null)
			stage[tile.y][tile.x] = changeTo;
	}

	public void changeTile(Point p, int changeTo) {
		stage[p.y][p.x] = changeTo;
	}

	public static Point getExactTileMidPoint(MainBomberman game, Point tile) {
		return getExactTileMidPoint(game, tile, game.stage.getOffset());
	}

	public static Point getExactTileMidPoint(MainBomberman game, Point tile,
			Point stageOffset) {
		Point middleOfBodyOffset = new Point(18, 17);
		return new Point(tile.x * SQUARE_WIDTH + stageOffset.y + middleOfBodyOffset.x,
				tile.y * SQUARE_HEIGHT + stageOffset.x + middleOfBodyOffset.y);
	}

	public void resetMap(Logger logger, int whichStage) {
		logger.log(LogLevel.DEBUG, "Map reset.");
		stage = null;
		stage = getOriginalMap(whichStage);
	}

	public String toString() {
		String toReturn = "" + stage[0][0];
		for (int i = 0; i < stage.length; i++) {
			for (int j = 0; j < stage[i].length; j++) {
				if (i == 0 && j == 0)
					j++;
				toReturn += "," + stage[i][j];
			}
			toReturn += ";|;";
		}
		return toReturn;
	}

	public Point getOffset() {
		// if (whichStage == 0)
		// return new Point(44, 60);
		return new Point(46, 62);
		/*
		 * else if (whichStage == 1) return new Point(); else if (whichStage ==
		 * 2) return new Point(); else if (whichStage == 3) return new Point();
		 * else if (whichStage == 4) return new Point(); else if (whichStage ==
		 * 5) return new Point(); else if (whichStage == 6) return new Point();
		 * else if (whichStage == 7) return new Point(); return new Point(0, 0);
		 */
	}

	public void draw(Graphics2D g, MainBomberman game) {
		g.drawImage(game.pictures.stageBackground, -4, -4, game);
		ArrayList<com.github.vegeto079.saturnbomberman.objects.Object> objects = game.objects;
		for (int i = 0; i < objects.size(); i++)
			if (objects.get(i) instanceof com.github.vegeto079.saturnbomberman.objects.Bomb) {
				com.github.vegeto079.saturnbomberman.objects.Bomb bomb = (com.github.vegeto079.saturnbomberman.objects.Bomb) objects.get(i);
				if (bomb.pickedUpCount < 3) {
					if (!bomb.exploding && !bomb.finished
							&& !bomb.type.equals(BombType.LAND_MINE))
						g.drawImage(game.pictures.shadow, bomb.exactPoint.toPoint().x + 8,
								bomb.exactPoint.toPoint().y + 28, game);
					bomb.draw(g, game);
				}
			}
		for (int i = 0; i < objects.size(); i++)
			if (objects.get(i) instanceof com.github.vegeto079.saturnbomberman.objects.Block) {
				objects.get(i).draw(g, game);
			} else if (objects.get(i) instanceof com.github.vegeto079.saturnbomberman.objects.SolidBlock) {
				objects.get(i).draw(g, game);
			}
		g.drawImage(game.pictures.stageBackgroundOverlap, 0, 0, game);
		g.drawImage(game.pictures.stageTimerOverlap, 0, 0, game);
		int alivePlayers = 0;
		for (int i = 0; i < game.players.size(); i++)
			if (game.players.get(i).deadTimer == -1)
				alivePlayers++;
		int size = game.players.size();
		for (int i = 0; i < size; i++) {
			BufferedImage headImage = game.pictures.characters
					.get(game.players.get(i).character.characterIndex).get(5)
					.get(game.players.get(i).deadTimer == -1 ? (alivePlayers > 1 ? 4 : 1)
							: 0);
			boolean skull = false;
			if (game.players.get(i).skullTimer > game.currentTimeMillis()
					&& game.players.get(i).deadTimer == -1) {
				double count = game.tickcounter;
				if (game.players.get(i).skullTimer - game.currentTimeMillis() > 3000) {
					if ((count > 10 && count <= 20) || (count > 30 && count <= 40)
							|| (count > 50 && count <= 60) || (count > 70 && count <= 80)
							|| (count > 90 && count <= 100))
						skull = true; // More than 3 sec left in skull
				} else if (game.players.get(i).skullTimer
						- game.currentTimeMillis() <= 3000)
					if ((count > 25 && count <= 50) || (count > 75 && count <= 100))
						skull = true; // Skull almost done
			}
			if (skull)
				headImage = Pictures.darken(headImage);
			g.drawImage(headImage, 128 + i * (450 / size), 8, game);
			int killAmt = game.players.get(i).stats.getThisStageKilledPlayersAmt();
			if (killAmt > 9)
				killAmt = 9;
			BufferedImage number = game.pictures.textNumbers.get(0).get(killAmt);
			number = com.github.vegeto079.saturnbomberman.misc.Pictures.changeColor(number, new Color(80, 104, 120).getRGB(),
					Color.BLACK.getRGB());
			g.drawImage(number, 158 + i * (450 / size), 13, game);
			number.flush();
			number = null;
		}
		long matchEndTime = (long) (game.counter[2] + game.options.time * 60000);
		long timeLeft = matchEndTime - game.currentTimeMillis();
		if (game.temp[1].contains("Played Match End"))
			timeLeft = matchEndTime - Long
					.parseLong(game.temp[1].split(".Played Match End:")[1].split(":")[0]);
		timeLeft = timeLeft + 4000; // What's wrong with it, why's it
									// need this now..
		if (!game.network.isPlayingOnline())
			timeLeft += 1450;
		int minutesLeft = (int) Math.floor((double) timeLeft / 60000d);
		int secondsLeft = (int) Math.floor((double) timeLeft / 1000d) - minutesLeft * 60;
		int millisLeft = (int) ((long) Math.floor((double) timeLeft / 10d)
				- (long) minutesLeft * 60000l - (long) secondsLeft * 100l);
		if (millisLeft > 99) {
			// this means we went past the timeLeft,
			// so we should be ending the game.
			// just leaving black as a transition just in case i guess
		} else {
			if (minutesLeft == game.options.time) {
				long milli = (long) Math.floor((double) (timeLeft + 1100) / 10d)
						- (long) (200 + 6000 * game.options.time);
				float percent = (float) milli / 300f;
				secondsLeft = 0;

				int[] animationReady = { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 2, 11, 2, 12,
						1, 0 };
				int[] animationGo = { 0, 1, 2, 3, 1, 4, 1, 5, 1, 4, 1, 5, 1, 4, 1, -2,
						-1 };

				int index = (animationReady.length - 1 + animationGo.length - 1)
						- (int) (percent
								* (animationReady.length - 1 + animationGo.length - 1))
						+ 1;
				if (index < 0)
					index = 0;
				else if (index > animationReady.length - 1 + animationGo.length - 1)
					index = -1;
				BufferedImage i = null;
				if (index != -1) {
					if (index > animationReady.length - 1) {
						index -= animationReady.length - 1;
						if (animationGo[index] < 0) {
							i = game.pictures.stageReadyGo.get(0)
									.get(Math.abs(animationGo[index]) - 1);
						} else
							i = game.pictures.stageReadyGo.get(1).get(animationGo[index]);
					} else {
						i = game.pictures.stageReadyGo.get(0).get(animationReady[index]);
					}
					g.drawImage(i, game.getWidth() / 2 - i.getWidth() / 2,
							game.getHeight() / 2 - i.getHeight() / 2, game);
					i.flush();
					i = null;
				}
			}
			if (minutesLeft > 0) {
				ArrayList<BufferedImage> numbers = game.pictures.textNumbers.get(0);
				int secondsLeftLeft = Integer
						.parseInt(String.valueOf(("" + secondsLeft).charAt(0)));
				int secondsLeftRight = 0;
				try {
					secondsLeftRight = Integer
							.parseInt(String.valueOf(("" + secondsLeft).charAt(1)));
				} catch (Exception e) {
					secondsLeftRight = secondsLeftLeft;
					secondsLeftLeft = 0;
				}
				g.drawImage(numbers.get(secondsLeftRight), 95, 13, game);
				g.drawImage(numbers.get(secondsLeftLeft), 79, 13, game);
				g.drawImage(numbers.get(numbers.size() - 2), 69, 13, game);
				try {
					g.drawImage(numbers.get(minutesLeft), 49, 13, game);
				} catch (Exception e) {
					// too high?
				}
			} else {
				try {
					ArrayList<BufferedImage> numbers = game.pictures.textNumbers.get(0);
					int millisLeftLeft = Integer
							.parseInt(String.valueOf(("" + millisLeft).charAt(0)));
					int millisLeftRight = 0;
					try {
						millisLeftRight = Integer
								.parseInt(String.valueOf(("" + millisLeft).charAt(1)));
					} catch (Exception e) {
						millisLeftRight = millisLeftLeft;
						millisLeftLeft = 0;
					}
					int secondsLeftLeft = Integer
							.parseInt(String.valueOf(("" + secondsLeft).charAt(0)));
					int secondsLeftRight = 0;
					try {
						secondsLeftRight = Integer
								.parseInt(String.valueOf(("" + secondsLeft).charAt(1)));
					} catch (Exception e) {
						secondsLeftRight = secondsLeftLeft;
						secondsLeftLeft = 0;
					}
					g.drawImage(numbers.get(millisLeftRight), 95, 13, game);
					g.drawImage(numbers.get(millisLeftLeft), 79, 13, game);
					g.drawImage(numbers.get(numbers.size() - 1), 63, 13, game);
					g.drawImage(numbers.get(secondsLeftRight), 47, 13, game);
					g.drawImage(numbers.get(secondsLeftLeft), 31, 13, game);
				} catch (Exception e) {
					// sometimes throws a NumberFormatException for
					// millisLeftLeft when we clear the stage, just ignore it
				}
			}
		}
		drawEndOfStage(game, objects, g);
		if (minutesLeft == 1 && secondsLeft < 10) {
			long milli = (long) Math.floor((double) timeLeft / 10d) - 6320;
			float percent = (float) milli / 200f;
			int upAndDownIndex = -1;
			for (int i = 0; i < 10; i++)
				if (percent * 100f % i == 0)
					upAndDownIndex = i;
			if (upAndDownIndex == -1)
				upAndDownIndex = 0;
			if (upAndDownIndex > 5)
				upAndDownIndex -= 5;
			float percentTill = (1f - percent) / .225f;
			while (percentTill > 1)
				percentTill -= 1;
			int[] adjustY = { 1, 2, 1, 0, -1, -2, -1, 0, 1, 2, 1, 0, -1, -2, -1, 0 };
			upAndDownIndex = (int) (percentTill * (adjustY.length / 2 - 1));
			if (percent < 1f)
				if (percent > .8f || percent < .2f) {
					// moving
					percentTill = (1f - percent) / .2f;
					if (percentTill > 4)// exiting
						percentTill -= 3;
					for (int i = 0; i < game.pictures.textHurry.get(0).size(); i++)
						g.drawImage(game.pictures.textHurry.get(0).get(i),
								game.getWidth() / 2
										+ (int) ((1 - percentTill)
												* ((float) game.getWidth() / 2f + 52))
										+ i * 21 - 52,
								game.getHeight() / 2 - 25 + adjustY[upAndDownIndex + i],
								game);
				} else {
					// moving up and down in center
					for (int i = 0; i < game.pictures.textHurry.get(0).size(); i++)
						g.drawImage(game.pictures.textHurry.get(0).get(i),
								game.getWidth() / 2 + i * 21 - 52, game.getHeight() / 2
										- 25 + 5 * adjustY[upAndDownIndex + i],
								game);
				}
		}
	}

	private void drawEndOfStage(MainBomberman game, ArrayList<com.github.vegeto079.saturnbomberman.objects.Object> objects,
			Graphics2D g) {
		objects = bubbleSortObjectsByCellY(objects);
		for (int i = 0; i < objects.size(); i++)
			if (objects.get(i) instanceof com.github.vegeto079.saturnbomberman.objects.Player)
				objects.get(i).draw(g, game);
			else if (objects.get(i) instanceof com.github.vegeto079.saturnbomberman.objects.Item) {
				com.github.vegeto079.saturnbomberman.objects.Item item = (com.github.vegeto079.saturnbomberman.objects.Item) objects.get(i);
				if (item.visible && !item.dying && !item.finished && item.itemPicX != -1
						&& item.itemPicY != -1)
					g.drawImage(game.pictures.shadow,
							objects.get(i).exactPoint.toPoint().x + 4,
							objects.get(i).exactPoint.toPoint().y + 20, game);
				objects.get(i).draw(g, game);
			} else if (objects.get(i) instanceof com.github.vegeto079.saturnbomberman.objects.Bomb) {
				com.github.vegeto079.saturnbomberman.objects.Bomb bomb = (com.github.vegeto079.saturnbomberman.objects.Bomb) objects.get(i);
				if (bomb.pickedUpCount >= 3) {
					// Bouncing bomb
					// Was going to draw bomb shadow, but it's not worth it
					// g.drawImage(game.pictures.shadow,
					// bomb.exactPoint.toPoint().x + 8,
					// bomb.exactPoint.toPoint().y + 28, game);
					bomb.draw(g, game);
				}
			} else if (objects.get(i) instanceof com.github.vegeto079.saturnbomberman.objects.Weight) {
				com.github.vegeto079.saturnbomberman.objects.Weight weight = (com.github.vegeto079.saturnbomberman.objects.Weight) objects.get(i);
				weight.draw(g, game);
			}
	}

	private static ArrayList<com.github.vegeto079.saturnbomberman.objects.Object> bubbleSortObjectsByCellY(
			ArrayList<com.github.vegeto079.saturnbomberman.objects.Object> objects) {
		int j;
		boolean flag = true;
		com.github.vegeto079.saturnbomberman.objects.Object temp;

		while (flag) {
			flag = false;
			for (j = 0; j < objects.size() - 1; j++) {
				try {
					if (objects.get(j).cellPoint.y > objects.get(j + 1).cellPoint.y) {
						temp = objects.get(j);
						objects.set(j, objects.get(j + 1));
						objects.set(j + 1, temp);
						flag = true;
					}
				} catch (Exception e) {
					e.printStackTrace();
					// Maybe one of the objects didn't have a cellPoint?
				}
			}
		}
		return objects;
	}

	public Area getBoundings(MainBomberman game, Point cellObjPoint, Point middleObjPoint,
			Player player) {
		Area boundings = new Area();
		int diff = 35;
		int amtToCheckAround = 1;
		ArrayList<com.github.vegeto079.saturnbomberman.objects.Bomb> bombs = new ArrayList<com.github.vegeto079.saturnbomberman.objects.Bomb>();
		ArrayList<com.github.vegeto079.saturnbomberman.objects.Object> objects = new ArrayList<com.github.vegeto079.saturnbomberman.objects.Object>(game.objects);
		for (int i = 0; i < objects.size(); i++)
			if (objects.get(i) instanceof com.github.vegeto079.saturnbomberman.objects.Bomb) {
				bombs.add((com.github.vegeto079.saturnbomberman.objects.Bomb) objects.get(i));
				if (bombs.get(bombs.size() - 1).type.equals(BombType.LAND_MINE))
					bombs.remove(bombs.size() - 1);
			}
		ArrayList<com.github.vegeto079.saturnbomberman.objects.Weight> weights = new ArrayList<com.github.vegeto079.saturnbomberman.objects.Weight>();
		for (int i = 0; i < objects.size(); i++)
			if (objects.get(i) instanceof com.github.vegeto079.saturnbomberman.objects.Weight)
				weights.add((com.github.vegeto079.saturnbomberman.objects.Weight) objects.get(i));
		for (int i = cellObjPoint.y - amtToCheckAround; i <= cellObjPoint.y
				+ amtToCheckAround; i++)
			for (int j = cellObjPoint.x - amtToCheckAround; j <= cellObjPoint.x
					+ amtToCheckAround; j++)
				if (i < 0 || j < 0 || i > stage.length - 1 || j > stage[0].length - 1
						|| (stage[i][j] >= 0 && stage[i][j] <= 5)
						|| getWeightOnPoint(weights, new Point(j, i)) != null) {
					Point cellPoint = Stage.getExactTileMidPoint(game, new Point(i, j));
					cellPoint = new Point(cellPoint.y, cellPoint.x);
					boundings.add(new Area(new Rectangle(cellPoint.x - diff / 2,
							cellPoint.y - diff / 2, diff + 2, diff + 2)));
				} else if (getBombOnPoint(bombs, new Point(j, i)) != null
						&& cellObjPoint.distance(new Point(j, i)) > 0) {
					diff = 25;
					com.github.vegeto079.saturnbomberman.objects.Bomb bomb = getBombOnPoint(bombs, new Point(j, i));
					ArrayList<Bomb> bombsOnTop = null;
					boolean onTopOfBomb = false;
					if (player != null && player.onTopBombs != null) {
						bombsOnTop = new ArrayList<Bomb>(player.onTopBombs);
						for (int k = 0; k < bombsOnTop.size(); k++)
							if (bomb.equals(bombsOnTop.get(k)))
								onTopOfBomb = true;
					}
					if (onTopOfBomb) {
						// Don't add boundaries to this bomb: we've just placed
						// it and are still on top of it
					} else if (bomb.type == BombType.LAND_MINE) {
						// Don't add boundaries to this bomb: it's a land mine
					} else {
						// Point cellPoint = Stage.getExactTileMidPoint(game,
						// new Point(i, j));
						// cellPoint = new Point(cellPoint.y, cellPoint.x);
						Point[] playerCollisionPoints = Player
								.getCollisionPoints(middleObjPoint);
						Point topRight = playerCollisionPoints[0];
						Point bottomLeft = playerCollisionPoints[1];
						Point bottomRight = playerCollisionPoints[2];
						Point topLeft = playerCollisionPoints[3];

						Point p = new Point(bomb.exactPoint.toPoint().x + 26,
								bomb.exactPoint.toPoint().y + 26);
						Rectangle bounds = new Rectangle(p.x - diff / 2, p.y - diff / 2,
								diff, diff);
						if (middleObjPoint.x - p.x <= 0 && (bounds.contains(topRight)
								|| bounds.contains(bottomRight))) {
							while (bounds.x < topRight.x)
								bounds.x += 2;
							while (bounds.x < bottomRight.x)
								bounds.x += 2;
						}
						if (middleObjPoint.x - p.x >= 0 && (bounds.contains(topLeft)
								|| bounds.contains(bottomLeft))) {
							int oldX = bounds.x;
							while (oldX + bounds.width > topLeft.x)
								bounds.width -= 2;
							while (oldX + bounds.width > bottomLeft.x)
								bounds.width -= 2;
						}
						if (middleObjPoint.y - p.y > 0 && (bounds.contains(topLeft)
								|| bounds.contains(topRight))) {
							while (bounds.y + bounds.height > topLeft.y)
								bounds.height -= 2;
							while (bounds.y + bounds.height > topRight.y)
								bounds.height -= 2;
						}
						if (middleObjPoint.y - p.y <= 0 && (bounds.contains(bottomLeft)
								|| bounds.contains(bottomRight))) {
							while (bounds.y < bottomLeft.y)
								bounds.y += 2;
							while (bounds.y < bottomRight.y)
								bounds.y += 2;
						}
						boundings.add(new Area(bounds));
						diff = 35;
					}
				}
		return boundings;
	}

	private com.github.vegeto079.saturnbomberman.objects.Bomb getBombOnPoint(ArrayList<com.github.vegeto079.saturnbomberman.objects.Bomb> bombs,
			Point pointToCheck) {
		for (com.github.vegeto079.saturnbomberman.objects.Bomb obj : bombs)
			if (obj.cellPoint.distance(pointToCheck) == 0
					&& !obj.type.equals(BombType.LAND_MINE))
				return obj;
		return null;
	}

	private com.github.vegeto079.saturnbomberman.objects.Weight getWeightOnPoint(ArrayList<com.github.vegeto079.saturnbomberman.objects.Weight> weights,
			Point pointToCheck) {
		for (com.github.vegeto079.saturnbomberman.objects.Weight obj : weights)
			if (obj.cellPoint.distance(pointToCheck) == 0 && obj.solid)
				return obj;
		return null;
	}

	public static boolean canMove(int direction, DoublePoint ourCenterPoint,
			double movingX, double movingY, MainBomberman game, Point cellObjPoint,
			Player player) {
		DoublePoint movingToPointD = new DoublePoint(ourCenterPoint.x + movingX,
				ourCenterPoint.y + movingY);
		Point movingToPoint = movingToPointD.toPoint();
		Point topRightPoint = Player.getCollisionPoints(movingToPoint)[0];
		Point bottomLeftPoint = Player.getCollisionPoints(movingToPoint)[1];
		Point bottomRightPoint = Player.getCollisionPoints(movingToPoint)[2];
		Point topLeftPoint = Player.getCollisionPoints(movingToPoint)[3];
		Rectangle boundingBox = new Rectangle(topLeftPoint.x, topLeftPoint.y,
				bottomRightPoint.x - topLeftPoint.x, bottomRightPoint.y - topLeftPoint.y);
		Area bounds = game.stage.getBoundings(game, cellObjPoint,
				ourCenterPoint.toPoint(), player);
		if (bounds.contains(movingToPoint) || bounds.contains(topRightPoint)
				|| bounds.contains(bottomLeftPoint) || bounds.contains(bottomRightPoint)
				|| bounds.contains(topLeftPoint) || bounds.contains(boundingBox))
			return false;
		return true;
	}

	public void drawCollisionMap(Graphics2D g, MainBomberman game) {
		getCollisionMap(game);
		g.setFont(game.defaultFont);
		for (int i = 0; i < lastCollisionMap.length; i++)
			for (int j = 0; j < lastCollisionMap[i].length; j++) {
				String text = "" + lastCollisionMap[i][j];
				int width = Tools.getTextWidth(g, text);
				Point mid = Stage.getExactTileMidPoint(game, new Point(j, i));
				g.setColor(Color.BLACK);
				g.drawString(text, mid.x - width / 2 + 1, mid.y + 13);
				g.setColor(Color.WHITE);
				g.drawString(text, mid.x - width / 2, mid.y + 12);
			}
	}

	public long[][] getCollisionMap(MainBomberman game) {
		if (game.currentTimeMillis()
				- lastCollisionMapUpdate >= timeBetweenCollisionMapUpdates)
			updateCollisionMap(game);
		return lastCollisionMap;
	}

	public void updateCollisionMap(MainBomberman game) {
		long[][] collisionMap = new long[stage.length][stage[0].length];
		for (int i = 0; i < stage.length; i++)
			for (int j = 0; j < stage[i].length; j++)
				if (stage[i][j] == -1) { // Empty space
					collisionMap[i][j] = -1;
				} else if (stage[i][j] == 0) { // Solid
					collisionMap[i][j] = -2;
				} else if (stage[i][j] == 1) { // Explodable
					collisionMap[i][j] = -3;
				}
		ArrayList<com.github.vegeto079.saturnbomberman.objects.Object> objects = new ArrayList<com.github.vegeto079.saturnbomberman.objects.Object>(game.objects);
		for (com.github.vegeto079.saturnbomberman.objects.Object obj : objects) {
			if (obj.cellPoint.x == -1 || obj.cellPoint.y == -1)
				continue;
			if (obj instanceof Weight) {
				Weight weight = (Weight) obj;
				if (weight.solid)
					collisionMap[weight.cellPoint.y][weight.cellPoint.x] = -2;
				// -2: solid, cant move there
				else {
					long timeTillFallDown = weight.getTimeTillExplosion(game);
					if (timeTillFallDown != -1)
						collisionMap[weight.cellPoint.y][weight.cellPoint.x] = timeTillFallDown;
					// above 0: time till dangerous
				}
			} else if (obj instanceof Player) {
				Player player = (Player) obj;
				if (player.deadTimer == -1) // not dead
					collisionMap[player.cellPoint.y][player.cellPoint.x] = -4;
				// -4: player
			} else if (obj instanceof Item) {
				Item item = (Item) obj;
				if (item.visible && item.collectable)
					collisionMap[item.cellPoint.y][item.cellPoint.x] = -10
							- item.getType().ordinal();
				// -10+: items
			}
		}
		for (com.github.vegeto079.saturnbomberman.objects.Object obj : objects)
			if (obj instanceof Bomb) {
				// Handle bombs after everything else: their danger is most
				// important
				Bomb bomb = (Bomb) obj;
				// if (collisionMap[bomb.cellPoint.y][bomb.cellPoint.x] == -4)
				// collisionMap[bomb.cellPoint.y][bomb.cellPoint.x] = 1000;
				// else
				try {
					collisionMap[bomb.cellPoint.y][bomb.cellPoint.x] = 100000;
				} catch (Exception e) {
					// Bomb bouncing off map?
					continue;
				}
				// above 0: time till dangerous
				try {
					ArrayList<long[]> explosionPoints = bomb.getExplosionPoints();
					for (long[] explosionPoint : explosionPoints) {
						int x = (int) explosionPoint[1];
						int y = (int) explosionPoint[0];
						if (collisionMap[x][y] != -2 && collisionMap[x][y] != -3
								&& collisionMap[x][y] != 100000)
							collisionMap[x][y] = explosionPoint[2];
					}
				} catch (Exception e) {
					// Some issue getting points, just ignore
				}
			}
		lastCollisionMap = collisionMap;
	}

	public static ArrayList<Player> getPlayersOnTile(Point tile, MainBomberman game) {
		ArrayList<Player> players = new ArrayList<Player>();
		for (int i = 0; i < game.players.size(); i++)
			if (game.players.get(i).getCellPoint().distance(tile) == 0)
				players.add(game.players.get(i));
		return players;
	}

	public static ArrayList<Player> getAlivePlayersOnTileExcludingPlayer(Point tile,
			Player excludePlayer, MainBomberman game) {
		ArrayList<Player> players = new ArrayList<Player>();
		for (int i = 0; i < game.players.size(); i++)
			if (game.players.get(i).getCellPoint().distance(tile) == 0
					&& !game.players.get(i).equals(excludePlayer)
					&& game.players.get(i).deadTimer == -1)
				players.add(game.players.get(i));
		return players;
	}

	public static boolean playerIsOnTile(Point tile, Player player, MainBomberman game) {
		ArrayList<Player> playersOnTile = getPlayersOnTile(tile, game);
		for (Player p : playersOnTile)
			if (p.equals(player))
				return true;
		return false;
	}
}