package com.github.vegeto079.saturnbomberman.objects;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;

import com.github.vegeto079.ngcommontools.main.DoublePoint;
import com.github.vegeto079.ngcommontools.main.Logger.LogLevel;
import com.github.vegeto079.ngcommontools.main.Tools;
import com.github.vegeto079.saturnbomberman.main.Character.Animation;
import com.github.vegeto079.saturnbomberman.main.MainBomberman;
import com.github.vegeto079.saturnbomberman.main.Stage;

/**
 * 
 * @author Nathan
 * @version 1.0: Started tracking version.
 * @version 1.01: {@link FireArm} now retroactively looks at every point it
 *          touched every time it's called (and isn't fading out), so you can't
 *          walk through a "line of fire".
 * @version 1.1: Bomb is now {@link Comparable} by
 *          {@link #getTimeTillExplosion()}.
 * @version 1.2: Network compatibility.
 * @version 1.21: Made {@link #equals(Bomb)} also work with identifier if others
 *          don't match.
 * @version 1.3: Changed all {@link System#currentTimeMillis()} references to
 *          {@link MainBomberman#currentTimeMillis()}. Also made {@link #game}
 *          something we store on creation and use, instead of tossing around
 *          via methods.
 * @version 1.4: {@link #identifier} now sets correctly.
 * @version 1.41: {@link #bounce()} now acts differently when hitting other
 *          bouncing bombs... still not what i want though, as they'll overlap
 *          sometimes
 * @version 1.42: Added {@link #originalFirePower} which tells the bomb how fast
 *          to tick based on it's explosion size, rather than using
 *          {@link Player#firePower} at the time (which can change).
 */
public class Bomb extends Object {
	public static long LIFE_TIME = 2500l;
	private MainBomberman game = null;
	public long explodeTime = System.currentTimeMillis() + LIFE_TIME;
	public boolean exploding = false;
	public BombType type = null;
	public Player playerOrigin = null;
	public Player playerKicked = null;
	public int tickSkip = 0;
	int[] animation = null;
	public int animationTick = 0;
	public int maxAnimationTick = 2;
	public int explosionMaxAnimationTick = 5;
	int bombType = -1;
	public long identifier = -1;
	public Point adjustExactPoint = new Point(-40, -10);
	private Point adjustExactBombPoint = new Point(-36, -4);
	public FireArm[] fireArms = new FireArm[5];
	private int fireArmPower = 4;
	public int maxPower = 2;
	public int originalFirePower = -1;
	public int moving = -1;
	public int pickedUpCount = -1;

	public ArrayList<long[]> explosionPoints = null;
	public long lastExplosionPointsRetrieval = 0;
	public long timeBetweenExplosionPointsRetrieval = 100;

	// used for simulating explosions before they happen, useful for AI and
	// debug visuals
	private FireArm[] potentialFireArms = new FireArm[5];
	public long willBeExplodedByOtherBombTime = -1;

	public enum BombType {
		NORMAL, REMOTE, SPIKED, JELLY, POWER, LAND_MINE, BEAD, GUN, SLOW;
	}

	public boolean equals(Bomb bomb) {
		return identifier == bomb.identifier || (explodeTime == bomb.explodeTime && exploding == bomb.exploding
				&& type.equals(bomb.type) && exactPoint == bomb.exactPoint && cellPoint == bomb.cellPoint
				&& moving == bomb.moving && pickedUpCount == bomb.pickedUpCount);
	}

	public Bomb(Point cellPoint, BombType type, Player playerOrigin, MainBomberman game, int maxPower, long explodeTime,
			long identifier) {
		this.cellPoint = cellPoint;
		this.type = type;
		this.playerOrigin = playerOrigin;
		this.maxPower = maxPower;
		originalFirePower = maxPower;
		if (type.equals(BombType.POWER))
			maxPower = 9;
		exactPoint = new DoublePoint(Stage.getExactTileMidPoint(game, cellPoint));
		exactPoint = new DoublePoint(exactPoint.x + adjustExactPoint.x, exactPoint.y + adjustExactPoint.y);
		if (type.equals(BombType.LAND_MINE))
			this.explodeTime = game.currentTimeMillis() - 5001;
		else
			this.explodeTime = explodeTime;
		this.game = game;
		this.identifier = identifier;
	}

	/**
	 * Used when picking up bombs.
	 */
	public Bomb(Bomb floorBomb, long currentTime) {
		pickedUpCount = 0;
		cellPoint = floorBomb.cellPoint;
		type = floorBomb.type;
		playerOrigin = floorBomb.playerOrigin;
		maxPower = floorBomb.maxPower;
		originalFirePower = floorBomb.originalFirePower;
		exactPoint = floorBomb.exactPoint;
		explodeTime = floorBomb.explodeTime - currentTime;
		identifier = floorBomb.identifier;
		game = floorBomb.game;
	}

	/**
	 * Immediately explode bomb.
	 */
	public void explodeNow() {
		if (!exploding) {
			game.objects.remove(this);
			game.objects.add(this);
			explodeTime = game.currentTimeMillis() - 1l;
			exploding = true;
			tickSkip = -1;
			animationTick = 0;
			tick(game);
		}
	}

	/**
	 * Removes this Bomb from play.
	 */
	public void remove() {
		explodeTime = Long.MAX_VALUE;
		exploding = false;
		moving = -1;
		finished = true;
		if (!game.objects.remove(this))
			game.logger.log(LogLevel.WARNING, "Could not find Bomb to remove when picking up?");
	}

	public void tick(MainBomberman game) {
		this.game = game;
		if (finished)
			return;
		if (animation == null)
			setVariables();
		int alivePlayers = 0;
		for (int i = 0; i < game.players.size(); i++)
			if (game.players.get(i).deadTimer == -1)
				alivePlayers++;
		if (alivePlayers < 2) {
			moving = -1;
			tickSkip++;
			if (tickSkip >= 15 && !exploding)
				tickSkip = 0;
			else if (tickSkip >= 3 && pickedUpCount > 2)
				tickSkip = 0;
			if (tickSkip != 0)
				return;
			if (animation != null)
				if (animationTick + 1 > animation.length - 1)
					animationTick = 0;
				else
					animationTick++;
			return;
		} else if (pickedUpCount > -1) {
			if (moving == -1)
				moving = 0;
			exploding = false;
			finished = false;
		} else if (moving != -1) {
			double speed = 4d;
			DoublePoint speedMove = new DoublePoint(0, 0);
			Point cell = getCellPoint();
			Point goingToCell = null;
			if (moving == 0) {
				speedMove = new DoublePoint(0, -speed);
				goingToCell = new Point(cell.x, cell.y - 1);
			} else if (moving == 1) {
				speedMove = new DoublePoint(0, speed);
				goingToCell = new Point(cell.x, cell.y + 1);
			} else if (moving == 2) {
				speedMove = new DoublePoint(-speed, 0);
				goingToCell = new Point(cell.x - 1, cell.y);
			} else if (moving == 3) {
				speedMove = new DoublePoint(speed, 0);
				goingToCell = new Point(cell.x + 1, cell.y);
			}
			if (Stage.canMove(moving, new DoublePoint(exactPoint.x + 23, exactPoint.y + 24), speedMove.x,
					speedMove.y, game, cell, null)) {
				boolean cantMove = false;
				int ping = -1;
				// If it's about to explode, don't let it move
				if (game.network.isPlayingOnline())
					ping = game.network.getPingBuffer();
				if (ping < 200)
					ping = 200;
				if (game.currentTimeMillis() + ping >= explodeTime)
					cantMove = true;
				int[][] stage = game.stage.get();
				if (goingToCell.x < 0 || goingToCell.y < 0 || goingToCell.x > stage[0].length - 1
						|| goingToCell.y > stage.length - 1)
					cantMove = true;
				if (!cantMove) {
					int stageIndex = stage[goingToCell.y][goingToCell.x];
					if (stageIndex == 1 || stageIndex == 0)
						cantMove = true;
				}
				if (!cantMove)
					try {
						ArrayList<com.github.vegeto079.saturnbomberman.objects.Object> objects = new ArrayList<com.github.vegeto079.saturnbomberman.objects.Object>(game.objects);
						for (com.github.vegeto079.saturnbomberman.objects.Object obj : objects) {
							if (obj instanceof com.github.vegeto079.saturnbomberman.objects.Bomb) {
								com.github.vegeto079.saturnbomberman.objects.Bomb bomb = (com.github.vegeto079.saturnbomberman.objects.Bomb) obj;
								if (!bomb.equals(this)) {
									if (bomb.cellPoint.distance(cellPoint) == 0) {
										// on the bomb?
										// cantMove = true;
										// bomb.moving = moving;
									} else if (bomb.cellPoint.distance(goingToCell) == 0) {
										cantMove = true;
										if (game.options.kickedBombsMoveOtherBombs) {
											if (!game.network.talking.bombKick(bomb, playerKicked, bomb.moving))
												bomb.moving = moving;
											game.network.talking.bombMovingStop(this, playerOrigin, playerKicked);
										}
									}
									if (bomb.exploding && !exploding) {
										for (int i = 0; i < bomb.fireArms.length; i++)
											if (bomb.fireArms[i] != null && bomb.fireArms[i].touchedCells != null)
												for (int j = 0; j < bomb.fireArms[i].touchedCells.size(); j++)
													if (bomb.fireArms[i].touchedCells.get(j).distance(cellPoint) == 0) {
														exploding = true;
														return;
													}
									}
								}
							} else if (obj instanceof com.github.vegeto079.saturnbomberman.objects.Item) {
								com.github.vegeto079.saturnbomberman.objects.Item item = (com.github.vegeto079.saturnbomberman.objects.Item) obj;
								if (item.cellPoint.distance(cellPoint) == 0) {
									boolean ourBomb = game.network.talking.itemDestroy(item, playerOrigin,
											playerKicked);
									if (!game.network.isPlayingOnline() || ourBomb) {
										item.destroy(game);
									}
									cantMove = true;
									break;
								}
							}
						}
					} catch (Exception e) {
					}
				if (!cantMove)
					for (Player player : game.players) {
						if (player.getCellPoint().distance(cellPoint) == 0) {
							// cantMove = true;
						} else if (player.getCellPoint().distance(goingToCell) == 0) {
							cantMove = true;
						}
					}
				if (!cantMove) {
					exactPoint = new DoublePoint(exactPoint.x + speedMove.x, exactPoint.y + speedMove.y);
					cellPoint = getCellPoint();
				} else {
					exactPoint = new DoublePoint(Stage.getExactTileMidPoint(game, getCellPoint()));
					exactPoint = new DoublePoint(exactPoint.x + adjustExactPoint.x, exactPoint.y + adjustExactPoint.y);
					if (type == BombType.JELLY) {
						int oldMoving = moving;
						int testMoving = moving;
						while (testMoving == oldMoving)
							testMoving = Tools.random(game, 0, 3, "Jelly Bomb bouncing direction");
						boolean ourBomb = game.network.talking.jellyBombKicked(this, playerOrigin, playerKicked,
								testMoving);
						if (!game.network.isPlayingOnline() || ourBomb) {
							moving = testMoving;
							game.sound.play("Bounce");
						}
					} else {
						boolean ourBomb = game.network.talking.bombMovingStop(this, playerOrigin, playerKicked);
						if (!game.network.isPlayingOnline() || ourBomb) {
							playerKicked = null;
						}
						moving = -1;
					}
				}
			} else {
				boolean ourBomb = game.network.talking.bombMovingStop(this, playerOrigin, playerKicked);
				if (!game.network.isPlayingOnline() || ourBomb) {
					playerKicked = null;
				}
				moving = -1;
			}
		}
		if (pickedUpCount == -1)
			try {
				ArrayList<com.github.vegeto079.saturnbomberman.objects.Object> objects = new ArrayList<com.github.vegeto079.saturnbomberman.objects.Object>(game.objects);
				for (com.github.vegeto079.saturnbomberman.objects.Object obj : objects)
					if (obj instanceof com.github.vegeto079.saturnbomberman.objects.Item) {
						com.github.vegeto079.saturnbomberman.objects.Item item = (com.github.vegeto079.saturnbomberman.objects.Item) obj;
						boolean playerOnSquare = false;
						for (Player player : game.players)
							if (player.getCellPoint().distance(item.cellPoint) == 0)
								playerOnSquare = true;
						if (item.cellPoint.distance(cellPoint) == 0 && !playerOnSquare) {
							boolean ourBomb = game.network.talking.itemDestroy(item, playerOrigin, playerKicked);
							if (!game.network.isPlayingOnline() || ourBomb) {
								item.destroy(game);
							}
							break;
						}
					}
			} catch (Exception e) {
				// concurrent?
			}
		tickSkip++;
		if (type.equals(BombType.SLOW)) {
			if (tickSkip >= 30 && !exploding)
				tickSkip = 0;
			if (tickSkip >= 10 && exploding && originalFirePower < 3)
				tickSkip = 0;
			else if (tickSkip >= 12 && exploding && originalFirePower < 6)
				tickSkip = 0;
			else if (tickSkip >= 14 && exploding && originalFirePower < 9)
				tickSkip = 0;
			else if (tickSkip >= 20 && exploding && originalFirePower >= 9)
				tickSkip = 0;
		} else {
			if (tickSkip >= 15 && !exploding)
				tickSkip = 0;
			if (tickSkip >= 7 && exploding && originalFirePower < 3)
				tickSkip = 0;
			else if (tickSkip >= 6 && exploding && originalFirePower < 6)
				tickSkip = 0;
			else if (tickSkip >= 5 && exploding && originalFirePower < 9)
				tickSkip = 0;
			else if (tickSkip >= 4 && exploding && originalFirePower >= 9)
				tickSkip = 0;
			else if (tickSkip >= 3 && pickedUpCount > 2)
				tickSkip = 0;
		}
		if (tickSkip != 0)
			return;
		if (exploding) {
			if (animationTick == 0)
				game.sound.play("Bomb Explosion");
			if (fireArms[4] == null) {
				moving = -1;
				Point c = getCellPoint();
				fireArms[0] = new FireArm(this, new Point(c.x, c.y - 1), maxPower, 0, false, false);
				fireArms[1] = new FireArm(this, new Point(c.x, c.y + 1), maxPower, 1, false, false);
				fireArms[2] = new FireArm(this, new Point(c.x - 1, c.y), maxPower, 2, false, false);
				fireArms[3] = new FireArm(this, new Point(c.x + 1, c.y), maxPower, 3, false, false);
				fireArms[4] = new FireArm(this, new Point(c.x, c.y), maxPower, 0, true, false);
			} else {
				boolean nobodyCanExtend = true;
				for (int i = 0; i < fireArms.length; i++) {
					if (fireArms[i].canExtendFurther(game) && !fireArms[i].doneExtending)
						nobodyCanExtend = false;
					fireArms[i].tick(game, fireArmPower);
				}
				if (nobodyCanExtend) {
					if (fireArmPower - 1 < 1)
						finished = true;
					else
						fireArmPower--;
				}
			}
			if (explosionMaxAnimationTick - maxAnimationTick + animationTick
					+ 1 > game.pictures.bombs.get(bombType).size() - 1) {
				animationTick = -1;
			} else if (animationTick != -1)
				animationTick++;
		} else {
			if (animation != null) {
				if (animationTick + 1 > animation.length - 1)
					animationTick = 0;
				else
					animationTick++;
				if (pickedUpCount > 2)
					bounce();
				if (pickedUpCount == -1
						&& ((game.currentTimeMillis() >= explodeTime && !type.equals(BombType.LAND_MINE))
								|| (type.equals(BombType.LAND_MINE) && explodeTime - game.currentTimeMillis() > 0
										&& Math.abs(explodeTime - game.currentTimeMillis() - 2000) > 1500))) {
					exploding = true;
					animationTick = 0;
				}
			}
		}
	}

	/**
	 * Used for any bouncing bomb situation, such as jelly or thrown.
	 */
	private void bounce() {
		boolean hitNewSquare = false;
		if (pickedUpCount < 8) {
			if (pickedUpCount == 7) {
				if (moving == 0) {
					moving = 1;
					cellPoint = new Point(cellPoint.x, cellPoint.y + 2);
				} else
					cellPoint = new Point(cellPoint.x, cellPoint.y + 1);
				pickedUpCount = 3;
				hitNewSquare = true;
			} else {
				int[] yMove = { -2, -6, 6, 16 };
				int[] count = { 4, 5, 6, 7 };
				for (int i = 0; i < count.length; i++)
					if (pickedUpCount == count[i])
						exactPoint = new DoublePoint(exactPoint.x, exactPoint.y + yMove[i] * (moving == 0 ? 2 : 1));
				pickedUpCount++;
			}
		} else if (pickedUpCount < 13) {
			if (pickedUpCount == 12) {
				if (moving == 0) {
					moving = 1;
					cellPoint = new Point(cellPoint.x, cellPoint.y - 2);
				} else
					cellPoint = new Point(cellPoint.x, cellPoint.y - 1);
				pickedUpCount = 9;
				hitNewSquare = true;
			} else {
				int[] yMove = { 2, 6, -6, -16 };
				int[] count = { 9, 10, 11, 12 };
				for (int i = 0; i < count.length; i++)
					if (pickedUpCount == count[i])
						exactPoint = new DoublePoint(exactPoint.x, exactPoint.y - yMove[i] * (moving == 0 ? 2 : 1));
				pickedUpCount++;
			}
		} else if (pickedUpCount < 18) {
			if (pickedUpCount == 17) {
				if (moving == 0) {
					moving = 1;
					cellPoint = new Point(cellPoint.x - 2, cellPoint.y);
				} else
					cellPoint = new Point(cellPoint.x - 1, cellPoint.y);
				pickedUpCount = 14;
				hitNewSquare = true;
			} else {
				int[] xMove = { -4, -12, -4, -8 };
				int[] yMove = { -8, -12, 0, 8 };
				int[] count = { 14, 15, 16, 17 };
				for (int i = 0; i < count.length; i++)
					if (pickedUpCount == count[i])
						exactPoint = new DoublePoint(exactPoint.x + xMove[i] * (moving == 0 ? 2 : 1),
								exactPoint.y + yMove[i] * (moving == 0 ? 2 : 1));
				pickedUpCount++;
			}

		} else if (pickedUpCount < 23) {
			if (pickedUpCount == 22) {
				if (moving == 0) {
					moving = 1;
					cellPoint = new Point(cellPoint.x + 2, cellPoint.y);
				} else
					cellPoint = new Point(cellPoint.x + 1, cellPoint.y);
				pickedUpCount = 19;
				hitNewSquare = true;
			} else {
				int[] xMove = { 4, 12, 4, 8 };
				int[] yMove = { -8, -12, 0, 8 };
				int[] count = { 18, 19, 20, 21 };
				for (int i = 0; i < count.length; i++)
					if (pickedUpCount == count[i])
						exactPoint = new DoublePoint(exactPoint.x + xMove[i] * (moving == 0 ? 2 : 1),
								exactPoint.y + yMove[i] * (moving == 0 ? 2 : 1));
				pickedUpCount++;
			}
		}
		if (hitNewSquare) {
			exactPoint = new DoublePoint(Stage.getExactTileMidPoint(game, cellPoint));
			exactPoint = new DoublePoint(exactPoint.x + adjustExactPoint.x, exactPoint.y + adjustExactPoint.y);
			boolean hitPlayer = false;
			try {
				boolean hitSomething = false;
				if (game.stage.getIndex(cellPoint.y, cellPoint.x) != -1)
					hitSomething = true;
				if (!hitSomething)
					try {
						ArrayList<com.github.vegeto079.saturnbomberman.objects.Object> objects = new ArrayList<com.github.vegeto079.saturnbomberman.objects.Object>(game.objects);
						for (Object obj : objects)
							if (obj instanceof Bomb) {
								Bomb bomb = (Bomb) obj;
								if (!bomb.equals(this) && bomb.getCellPoint().distance(cellPoint) == 0) {
									// Can't land on a bomb
									if (bomb.pickedUpCount > 2) {
										// It's also a bouncing bomb! Let's just
										// land, this can't be good.
										// TODO: Make it so multiple bombs can't
										// land in the same spot :(
									} else
										hitSomething = true;
									break;
								}
							} else if (obj instanceof Weight) {
								Weight weight = (Weight) obj;
								if (weight.solid && weight.cellPoint.distance(cellPoint) == 0) {
									// Can't land on a weight
									hitSomething = true;
									break;
								}
							}
					} catch (Exception e) {
					}
				ArrayList<Player> players = game.players;
				if (!hitSomething)
					for (Player player : players)
						if (player.getCellPoint().distance(cellPoint) == 0) {
							// We landed on a person! Remove an item from them
							// and temporarily stun them.
							game.network.talking.bombBounceHead(this, playerOrigin, player);
							hitSomething = true;
							if (!game.network.isPlayingOnline()) {
								game.sound.play("Ouch");
								player.lastButtonPress = game.currentTimeMillis();
								player.character.animation = Animation.STUNNED_SPINNING;
								player.character.animationTick = 0;
								player.placeRandomItemToBoard();
								hitPlayer = true;
								if (player.pickedUpBomb != null) {
									player.pickedUpBomb.pickedUpCount = 3 + (player.character.facing * 5);
									DoublePoint dp = new DoublePoint(Stage.getExactTileMidPoint(game, getCellPoint()));
									player.pickedUpBomb.exactPoint = new DoublePoint(
											dp.x + player.pickedUpBomb.adjustExactPoint.x,
											dp.y + player.pickedUpBomb.adjustExactPoint.y);
									game.objects.add(player.pickedUpBomb);
									player.pickedUpBomb = null;
								}
								break;
							}
						}
				if (!hitSomething) {
					// Square we bounced on is empty, land on it
					if (!game.network.isPlayingOnline()) {
						moving = -1;
						if (explodeTime < 50000)
							explodeTime += game.currentTimeMillis();
						pickedUpCount = -1;
					} else if (game.network.server != null)
						game.network.talking.bombBounceStop(this, playerOrigin);
				} else {
					if (!hitPlayer)
						game.sound.play("Bounce");
					if (type.equals(BombType.JELLY) && hitSomething && cellPoint.x >= 0
							&& cellPoint.x < game.stage.getHeight() && cellPoint.y >= 0
							&& cellPoint.y < game.stage.getWidth()) {
						if (!game.network.isPlayingOnline() || game.network.weAreHost()) {
							// Jelly bomb! Let's change directions, cause we're
							// crazy. Note that this has a 1/2 chance of heading
							// the same direction.
							int directionBefore = (pickedUpCount - 3) / 5;
							int newDirection = directionBefore;
							if (Tools.random(game, 0, 100, "Jelly Bomb bounce chance") >= 66) {
								newDirection = Tools.random(game, 0, 3, "Jelly Bomb bouncing direction");
								pickedUpCount = 3 + newDirection * 5;
							}
							game.network.talking.jellyBombBounce(this, newDirection);
						}
					}
				}
			} catch (Exception e) {
				// moving off screen
				if (!hitPlayer)
					game.sound.play("Bounce");
			}
		}
		if (exactPoint.x < 0) {
			exactPoint.x += game.getWidth();
			cellPoint = new Point(cellPoint.x + game.stage.getHeight() + 2, cellPoint.y);
		}
		if (exactPoint.y < 0) {
			exactPoint.y += game.getHeight();
			cellPoint = new Point(cellPoint.x, cellPoint.y + game.stage.getWidth() + 2);
		}
		if (exactPoint.x > game.getWidth()) {
			exactPoint.x -= game.getWidth();
			cellPoint = new Point(-2, cellPoint.y);
		}
		if (exactPoint.y > game.getHeight()) {
			exactPoint.y -= game.getHeight();
			cellPoint = new Point(cellPoint.x, -2);
		}
	}

	public void draw(Graphics2D g, MainBomberman game) {
		if (finished)
			return;
		if (exploding) {
			if (fireArms[4] != null) {
				Point middlePoint = Stage.getExactTileMidPoint(game, getCellPoint());
				if (!type.equals(BombType.GUN))
					g.drawImage(game.pictures.bombFire.get(6).get(4 - fireArmPower),
							middlePoint.x + adjustExactBombPoint.x, middlePoint.y + adjustExactBombPoint.y, game);
				for (int i = 0; i < fireArms.length; i++)
					fireArms[i].draw(g, game, fireArmPower);
			}
			if (animationTick >= 0 && animationTick < explosionMaxAnimationTick - maxAnimationTick)
				try {
					g.drawImage(
							game.pictures.bombs.get(bombType)
									.get(explosionMaxAnimationTick - maxAnimationTick + animationTick),
							exactPoint.toPoint().x, exactPoint.toPoint().y, game);
				} catch (Exception e) {
					game.logger.log(LogLevel.WARNING, "Exception! (" + bombType + "," + explosionMaxAnimationTick + ","
							+ maxAnimationTick + "," + animationTick + ")");
					e.printStackTrace();
				}
		} else {
			if (animation == null)
				setVariables();
			if (type.equals(BombType.LAND_MINE)) {
				BufferedImage img = game.pictures.bombs.get(bombType).get(animation[animationTick]);
				float minVisibility = .20f;
				float maxVisibility = 1f;
				float visibility = maxVisibility;
				if (game.currentTimeMillis() - explodeTime - 5000 >= 6000) {
					visibility = minVisibility;
				} else if (game.currentTimeMillis() > explodeTime + 5000) {
					visibility = 1f - (((float) (game.currentTimeMillis() - explodeTime - 5000)) / 6000f);
				}
				if (explodeTime - game.currentTimeMillis() > 0) {
					visibility = maxVisibility;
				}
				if (visibility < minVisibility)
					visibility = minVisibility;
				for (int i = 0; i < img.getWidth(); i++)
					for (int j = 0; j < img.getHeight(); j++)
						if (img.getRGB(i, j) != 1) {
							Color oldColor = new Color(img.getRGB(i, j));
							Color newColor = new Color(oldColor.getRed(), oldColor.getGreen(), oldColor.getBlue(),
									(int) (255f * visibility));
							img.setRGB(i, j, newColor.getRGB());
						}
				g.drawImage(img, exactPoint.toPoint().x, exactPoint.toPoint().y, game);
			} else
				g.drawImage(game.pictures.bombs.get(bombType).get(animation[animationTick]), exactPoint.toPoint().x,
						exactPoint.toPoint().y, game);
		}
	}

	private void setVariables() {
		if (type == BombType.NORMAL) {
			animation = new int[] { 1, 2, 1, 0 };
			bombType = 0;
			maxAnimationTick = 2;
			explosionMaxAnimationTick = 5;
		} else if (type == BombType.REMOTE) {
			animation = new int[] { 0, 1, 2, 3 };
			bombType = 1;
			maxAnimationTick = 3;
			explosionMaxAnimationTick = 6;
		} else if (type == BombType.SPIKED) {
			animation = new int[] { 1, 2, 1, 0 };
			bombType = 2;
			maxAnimationTick = 2;
			explosionMaxAnimationTick = 4;
		} else if (type == BombType.JELLY) {
			animation = new int[] { 1, 2, 1, 0 };
			bombType = 3;
			maxAnimationTick = 2;
			explosionMaxAnimationTick = 4;
		} else if (type == BombType.POWER) {
			animation = new int[] { 1, 2, 1, 0 };
			bombType = 4;
			maxAnimationTick = 2;
			explosionMaxAnimationTick = 4;
		} else if (type == BombType.LAND_MINE) {
			animation = new int[] { 0, 1, 2, 3, 4, 5, 4, 3, 2, 1 };
			bombType = 5;
			maxAnimationTick = 5;
			explosionMaxAnimationTick = 8;
		} else if (type == BombType.BEAD) {
			animation = new int[] { 1, 2, 1, 0 };
			bombType = 0;
			maxAnimationTick = 2;
			explosionMaxAnimationTick = 5;
		} else if (type == BombType.GUN) {
			animation = new int[] { 1, 2, 1, 0 };
			bombType = 0;
			maxAnimationTick = 2;
			explosionMaxAnimationTick = 5;
		} else if (type == BombType.SLOW) {
			animation = new int[] { 1, 2, 1, 0 };
			bombType = 0;
			maxAnimationTick = 2;
			explosionMaxAnimationTick = 5;
		}
	}

	public Point getCellPoint() {
		if (game.stage == null)
			return new Point(-1, -1);
		Point adjustBombMiddle = new Point(12, -40);
		Point ourPoint = new Point(
				(int) Math.floor(((double) exactPoint.x + (double) adjustExactBombPoint.x + (double) adjustBombMiddle.x)
						/ (double) Stage.SQUARE_WIDTH),
				(int) Math.floor(((double) exactPoint.y + (double) adjustExactBombPoint.y + (double) adjustBombMiddle.y)
						/ (double) Stage.SQUARE_HEIGHT));
		if (ourPoint.x < 0)
			ourPoint = new Point(0, ourPoint.y);
		if (ourPoint.y < 0)
			ourPoint = new Point(ourPoint.x, 0);
		int maxX = game.stage.getHeight() - 1;
		int maxY = game.stage.getWidth() - 1;
		if (ourPoint.x > maxX)
			ourPoint = new Point(maxX, ourPoint.y);
		if (ourPoint.y > maxY)
			ourPoint = new Point(ourPoint.x, maxY);
		return ourPoint;
	}

	public long getTimeTillExplosion() {
		long explosion = explodeTime - game.currentTimeMillis();
		if (willBeExplodedByOtherBombTime != -1) {
			explosion = willBeExplodedByOtherBombTime - game.currentTimeMillis();
			if (explosion < 1)
				explosion = 1;
		}
		if (explosion < 0)
			explosion = 0;
		return explosion;
	}

	public ArrayList<long[]> getExplosionPoints() {
		if (explosionPoints != null
				&& game.currentTimeMillis() - lastExplosionPointsRetrieval < timeBetweenExplosionPointsRetrieval)
			return explosionPoints;
		return updateExplosionPoints();
	}

	public ArrayList<long[]> updateExplosionPoints() {
		ArrayList<long[]> newExplosionPoints = new ArrayList<long[]>();
		Point c = getCellPoint();
		potentialFireArms[0] = new FireArm(this, new Point(c.x, c.y - 1), maxPower, 0, false, true);
		potentialFireArms[1] = new FireArm(this, new Point(c.x, c.y + 1), maxPower, 1, false, true);
		potentialFireArms[2] = new FireArm(this, new Point(c.x - 1, c.y), maxPower, 2, false, true);
		potentialFireArms[3] = new FireArm(this, new Point(c.x + 1, c.y), maxPower, 3, false, true);
		potentialFireArms[4] = new FireArm(this, new Point(c.x, c.y), maxPower, 0, true, true);

		long explosion = getTimeTillExplosion();

		for (int i = 0; i < potentialFireArms.length; i++) {
			while (!potentialFireArms[i].doneExtending)
				potentialFireArms[i].tick(game, 3); // >2, all that matters

			for (int j = 0; j < potentialFireArms[i].touchedCells.size(); j++) {
				long x = potentialFireArms[i].touchedCells.get(j).x;
				long y = potentialFireArms[i].touchedCells.get(j).y;
				if (x < 0 || y < 0 || x >= game.stage.getHeight() || y >= game.stage.getWidth())
					continue;

				// Calculate distance from main point and how long it will take
				// for the explosion to get there. 1000/fps=ms per frame, *5
				// because we skip from 5-7 frames before progressing on an
				// explosion
				long thisExplosion = explosion;
				if (game.currentTimeMillis() < thisExplosion)
					thisExplosion += (long) (new Point((int) x, (int) y).distance(new Point(c.x, c.y))
							* (1000 / game.getFps() * 5));

				newExplosionPoints.add(new long[] { x, y, thisExplosion });
			}
		}

		// Get all current bombs (besides self) and tell them they'll explode
		// sooner (if they will).
		ArrayList<com.github.vegeto079.saturnbomberman.objects.Object> objects = new ArrayList<com.github.vegeto079.saturnbomberman.objects.Object>(game.objects);
		for (com.github.vegeto079.saturnbomberman.objects.Object obj : objects) {
			try {
				if (obj instanceof Bomb) {
					Bomb bomb = (Bomb) obj;
					for (long[] explosionPoint : newExplosionPoints) {
						double dist = new Point((int) explosionPoint[0], (int) explosionPoint[1])
								.distance(bomb.getCellPoint());
						if (dist == 0 && !bomb.equals(this) && explodeTime < bomb.explodeTime) {
							dist = bomb.getCellPoint().distance(getCellPoint());
							long add = (long) (dist * (1000d / (double) game.getFps() * 5d));
							bomb.willBeExplodedByOtherBombTime = explodeTime + add;
							// System.out.println("other bomb told when it'll
							// explode ("
							// + explodeTime + "+" + add + "="
							// + bomb.willBeExplodedByOtherBombTime + ")");
						}
					}
				}
			} catch (Exception e) {
				// Something probably changed in game.objects. Not too
				// important, don't worry about it.
				break;
			}
		}
		lastExplosionPointsRetrieval = game.currentTimeMillis();
		explosionPoints = newExplosionPoints;
		return explosionPoints;
	}

	public class FireArm {
		Bomb parent = null;
		public ArrayList<Point> touchedCells = new ArrayList<Point>();
		public ArrayList<Point> explodedTileCells = new ArrayList<Point>();
		int direction = -1;
		int maxPower = -1;
		boolean doneExtending = false;
		int explodedThrough = 0;
		boolean isCenterPiece = false;
		boolean isTest = false;

		public FireArm(Bomb parent, Point startCell, int maxPower, int direction, boolean isCenterPiece,
				boolean isTest) {
			this.parent = parent;
			touchedCells.add(startCell);
			this.maxPower = maxPower;
			this.direction = direction;
			this.isCenterPiece = isCenterPiece;
			if (isCenterPiece)
				doneExtending = true;
			this.isTest = isTest;
		}

		public void tick(MainBomberman game, int power) {
			Point p = touchedCells.get(touchedCells.size() - 1);
			ArrayList<com.github.vegeto079.saturnbomberman.objects.Object> objects = new ArrayList<com.github.vegeto079.saturnbomberman.objects.Object>();
			try {
				for (com.github.vegeto079.saturnbomberman.objects.Object obj : game.objects)
					objects.add(obj);
			} catch (Exception e) {
				// retry
				tick(game, power);
				return;
			}
			ArrayList<Point> tempTouchedCells = new ArrayList<Point>(touchedCells);
			if (type.equals(BombType.GUN)) {
				tempTouchedCells = new ArrayList<Point>();
				tempTouchedCells.add(p);
			}
			if (power > 2)
				for (Point arm : tempTouchedCells) {
					// Go through every point to kill anything crossing the fire
					// but didn't initially get hit by it (often can happen on
					// big explosions)
					try {
						for (com.github.vegeto079.saturnbomberman.objects.Object obj : objects)
							if (arm.distance(obj.cellPoint) == 0)
								if (obj instanceof com.github.vegeto079.saturnbomberman.objects.Player) {
									Player player = (com.github.vegeto079.saturnbomberman.objects.Player) obj;
									if (player.deadTimer == -1) {
										boolean destroyedItemOnThisCell = false;
										for (Point explodedTileCell : explodedTileCells)
											if (explodedTileCell.distance(arm) == 0)
												destroyedItemOnThisCell = true;
										if (destroyedItemOnThisCell) {
											// We exploded this tile, opening it up for walking on. Someone could be
											// waiting to jump in on the item that's underneath this tile we're
											// exploding, and we shouldn't be able to kill them if that occurs.
											continue;
										}
										if (isTest) {
											// This is a test FireArm, not a real one. Don't actually do anything.
											continue;
										}
										// Killed a player!
										if (!game.options.canBombSelf && player.equals(playerOrigin))
											continue;
										boolean ourBomb = game.network.talking.killPlayer(player,
												playerOrigin.getIndex(), true, game.randomCount);
										if (!game.network.isPlayingOnline() || ourBomb) {
											player.deadTimer = 0;
											// System.out.println(player.getIndex()
											// + " killed by " +
											// playerOrigin.getIndex());
											playerOrigin.stats.killedPlayer(player.getIndex());
											player.stats.killedByPlayer(playerOrigin.getIndex());
											player.character.animation = Animation.IDLE;
											if (player.pickedUpBomb != null) {
												player.pickedUpBomb.pickedUpCount = 3 + (player.character.facing * 5);
												DoublePoint dp = new DoublePoint(
														Stage.getExactTileMidPoint(game, getCellPoint()));
												player.pickedUpBomb.exactPoint = new DoublePoint(
														dp.x + player.pickedUpBomb.adjustExactPoint.x,
														dp.y + player.pickedUpBomb.adjustExactPoint.y);
												game.objects.add(player.pickedUpBomb);
												player.pickedUpBomb = null;
											}
										}
									}
								} else if (obj instanceof com.github.vegeto079.saturnbomberman.objects.Bomb) {
									Bomb bomb = (com.github.vegeto079.saturnbomberman.objects.Bomb) obj;
									if (bomb.equals(parent) || bomb.pickedUpCount > -1)
										continue;
									if (!bomb.exploding) {
										doneExtending = true;
										explodedThrough += 2;
										if (isTest) {
											// This is a test FireArm, not a
											// real
											// one. Don't actually do anything.
											return;
										}
										bomb.explodeNow();
										return;
									}
								} else if (obj instanceof com.github.vegeto079.saturnbomberman.objects.Item) {
									Item item = (com.github.vegeto079.saturnbomberman.objects.Item) obj;
									if (item.collectable) {
										boolean weCreatedThisItem = false;
										for (Point explodedTileCell : explodedTileCells) {
											// Makes sure we don't explode
											// (destroy) an item that we created
											// (ie. blew up the block to). May
											// need to remove if creates
											// conflicts.
											if (explodedTileCell.distance(arm) == 0)
												weCreatedThisItem = true;
										}
										if (weCreatedThisItem)
											continue;
										doneExtending = true;
										explodedThrough += 2;
										if (isTest) {
											// This is a test FireArm, not a real one. Don't actually do anything.
											return;
										}
										item.destroy(game);
										return;
									}
								}
					} catch (Exception e) {
						// touching an object that we shouldnt be?
					}
				}
			if (!canExtendFurther(game) || doneExtending && !isCenterPiece) {
				// final point for this arm
				doneExtending = true;
				int[][] stage = game.stage.get();
				try {
					p = new Point(p.y, p.x);
				} catch (Exception e) {
					// retry
					tick(game, power);
					return;
				}
				if (p.x >= 0 && p.y >= 0 && p.x < stage.length && p.y < stage[0].length) {
					Block blockToExplode = Block.get(p, game);
					if (blockToExplode != null) {
						explodedThrough++;
						explodedTileCells.add(new Point(p.y, p.x));
						if (isTest) {
							// This is a test FireArm, not a real
							// one. Don't actually do anything.
						} else {
							blockToExplode.explode();
						}

					}
				}
			} else {
				// we can still move
				Point newPoint = null;
				try {
					if (direction == 0) {
						newPoint = new Point(p.x, p.y - 1);
					} else if (direction == 1) {
						newPoint = new Point(p.x, p.y + 1);
					} else if (direction == 2) {
						newPoint = new Point(p.x - 1, p.y);
					} else if (direction == 3) {
						newPoint = new Point(p.x + 1, p.y);
					}
				} catch (Exception e) {
					// retry
					tick(game, power);
					return;
				}
				if (!isCenterPiece)
					touchedCells.add(newPoint);
				int[][] stage = game.stage.get();
				p = new Point(p.y, p.x);
				if (p.x >= 0 && p.y >= 0 && p.x < stage.length && p.y < stage[0].length && stage[p.x][p.y] == 1
						&& type.equals(BombType.SPIKED) && explodedThrough < 2) {
					explodedThrough++;
					explodedTileCells.add(new Point(p.y, p.x));
					if (isTest) {
						// This is a test FireArm, not a real
						// one. Don't actually do anything.
					} else {
						game.stage.set(p.x, p.y, 2);
					}
				}
			}
		}

		public void draw(Graphics2D g, MainBomberman game, int power) {
			if (isCenterPiece)
				return;
			if (isTest) {
				// This is a test FireArm, not a real
				// one. Don't actually do anything.
				return;
			}
			int edgeIndex = -1, armIndex = -1;
			if (direction == 0) {
				edgeIndex = 2;
				armIndex = 0;
			} else if (direction == 1) {
				edgeIndex = 3;
				armIndex = 0;
			} else if (direction == 2) {
				edgeIndex = 4;
				armIndex = 1;
			} else if (direction == 3) {
				edgeIndex = 5;
				armIndex = 1;
			}
			Point midPoint = null, drawTo = null;
			for (int i = 0; i < touchedCells.size(); i++) {
				midPoint = Stage.getExactTileMidPoint(game, touchedCells.get(i));
				drawTo = new Point(midPoint.x + adjustExactBombPoint.x, midPoint.y + adjustExactBombPoint.y);
				if (i == touchedCells.size() - 1)
					g.drawImage(game.pictures.bombFire.get(edgeIndex).get(4 - power), drawTo.x, drawTo.y, game);
				else if (!type.equals(BombType.GUN))
					g.drawImage(game.pictures.bombFire.get(armIndex).get(4 - power), drawTo.x, drawTo.y, game);
			}
		}

		public boolean canExtendFurther(MainBomberman game) {
			try {
				int[][] stage = game.stage.get();
				Point p = touchedCells.get(touchedCells.size() - 1);
				p = new Point(p.y, p.x);
				if (!type.equals(BombType.SPIKED))
					return !(p.x < 0 || p.y < 0 || p.x >= stage.length || p.y >= stage[0].length || stage[p.x][p.y] == 1
							|| stage[p.x][p.y] == 0 || touchedCells.size() + 1 > maxPower);
				else
					return !(p.x < 0 || p.y < 0 || p.x >= stage.length || p.y >= stage[0].length
							|| touchedCells.size() + 1 > maxPower || stage[p.x][p.y] == 0 || explodedThrough > 1);
			} catch (Exception e) {
				return false;
				// e.printStackTrace();
			}
		}

	}

	public static enum BombComparator implements Comparator<Bomb> {
		EXPLOSION_SORT {
			public int compare(Bomb o1, Bomb o2) {
				return (int) (o1.getTimeTillExplosion() - o2.getTimeTillExplosion());
			}
		};

		public static Comparator<Bomb> decending(final Comparator<Bomb> other) {
			return new Comparator<Bomb>() {
				public int compare(Bomb o1, Bomb o2) {
					return other.compare(o1, o2);
				}
			};
		}

		public static Comparator<Bomb> getComparator(final BombComparator... multipleOptions) {
			return new Comparator<Bomb>() {
				public int compare(Bomb o1, Bomb o2) {
					for (BombComparator option : multipleOptions) {
						int result = option.compare(o1, o2);
						if (result != 0) {
							return result;
						}
					}
					return 0;
				}
			};
		}
	}

	public static Bomb get(int cellX, int cellY, MainBomberman game) {
		return get(new Point(cellX, cellY), game);
	}

	public static Bomb get(Point cellPoint, MainBomberman game) {
		int tryCount = 10;
		do
			try {
				tryCount--;
				for (int i = 0; i < game.objects.size(); i++) {
					com.github.vegeto079.saturnbomberman.objects.Object obj = game.objects.get(i);
					if (obj instanceof Bomb && obj.cellPoint.distance(cellPoint) == 0
							&& ((Bomb) obj).pickedUpCount == -1)
						return (Bomb) obj;
				}
			} catch (Exception e) {
			}
		while (tryCount > 0);
		return null;
	}
}
