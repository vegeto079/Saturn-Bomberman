package com.github.vegeto079.saturnbomberman.objects;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;

import com.github.vegeto079.ngcommontools.main.DoublePoint;
import com.github.vegeto079.ngcommontools.main.Logger.LogLevel;
import com.github.vegeto079.ngcommontools.main.Tools;
import com.github.vegeto079.saturnbomberman.main.Character.Animation;
import com.github.vegeto079.saturnbomberman.main.MainBomberman;
import com.github.vegeto079.saturnbomberman.main.Stage;
import com.github.vegeto079.saturnbomberman.misc.AI;
import com.github.vegeto079.saturnbomberman.misc.State.StateEnum;
import com.github.vegeto079.saturnbomberman.objects.Bomb.BombType;

/**
 * 
 * @author Nathan
 * @version 1.0: Started tracking version.
 * @version 1.01: {@link #setExactPoint(DoublePoint)} added, in conjunction with
 *          skull type number 8 being able to switch two player's positions.
 * @version 1.02: Now plays "hey!" sound when spreading skull virus.
 * @version 1.03: Added {@link #isOnlinePlayer} to differentiate from regular
 *          players.
 * @version 1.04: Working linebomb added.
 *          {@link #layBomb(MainBomberman, Point, boolean)} now has a boolean
 *          for whether or not the laid bomb is from the line bomb. If it is, it
 *          will not play the sound for each bomb (as such would create lag).
 * @version 1.041: Linebomb press time changed to under 200ms. Accidental
 *          linebombs are scary!
 * @version 1.05: We are now {@link Comparable}, with {@link #compareTo(Player)}
 *          , and therefore sortable by rank in an {@link ArrayList}. Also added
 *          {@link Stats#cacheRank} and {@link Stats#getCacheRank()} to get our
 *          most recently determined rank without needing variables, so we can
 *          use {@link #compareTo(Player)} properly.
 * @version 1.06: Now when adding our {@link Player}'s win to our stats at
 *          endgame, we also add a loss to everyone else's stats. This function
 *          is probably best held in a different class, but works as is,
 *          especially considering this class is also currently in charge of
 *          playing the "I won!" sound. These events should both be guaranteed
 *          better.
 * @version 1.07: "I won!" sound now plays at 2.5sec after winning, instead of
 *          1.5sec. Winning animation now waits for these 2.5sec as well, before
 *          playing.
 * @version 1.08: Added {@link #index} so we can find our placement in
 *          {@link MainBomberman#players}.
 * @version 1.09: Added {@link PlayerComparator} to sort players in various
 *          ways. Added {@link Stats#copy()}, {@link #copy()} and
 *          {@link #copyList(ArrayList)} to create a copy of players without the
 *          original player being modified or attached to the new one.
 * @version 1.1: No longer skulls dead people at position 0,0 (could tell
 *          because of the "Heyyy!" sound playing).
 * @version 1.11: Added {@link #setDirection(int)} and
 *          {@link #setButtonsPushed(boolean[])} for multiplayer use.
 * @version 1.12: Added {@link #setIndex(int)}, {@link #getIndex(MainBomberman)}
 *          , and updated {@link #getIndex()}.
 * @version 1.121: Removed <b>isOnlinePlayer</b>.
 * @version 1.13: Changed {@link #getBombsLaid(MainBomberman)} to
 *          {@link #getBombsLaidAmt(MainBomberman)} and made the former return
 *          all the actual Bomb objects.
 * @version 1.2: Changed all {@link System#currentTimeMillis()} references to
 *          {@link MainBomberman#currentTimeMillis()}. Also made {@link #game}
 *          something we store on creation and use, instead of tossing around
 *          via methods.
 * 
 */
public class Player extends com.github.vegeto079.saturnbomberman.objects.Object {
	public com.github.vegeto079.saturnbomberman.main.Character character = null;
	public boolean isCpu = false;
	public boolean isLocal = false;
	public AI ai = null;
	public MainBomberman game = null;
	public long lastButtonPress = System.currentTimeMillis();

	public int deadTimer = -1;

	public int firePower = 2;
	public int speed = 1;
	public int bombAmt = 1;
	public BombType bombType = BombType.NORMAL;

	public boolean kickBomb = false;
	public boolean powerGlove = false;
	public boolean lineBomb = false;
	public int absorptionTimer = -1;
	public int skullType = -1; // 10 types, from 0 to 9
	public long skullTimer = -1;
	public long devilTimer = -1;
	public long combineTimer = -1;
	public int carryingBombTick = -1;
	public com.github.vegeto079.saturnbomberman.objects.Bomb lastKickedBomb = null;
	public long lastKickedBombTime = -1;
	public ArrayList<com.github.vegeto079.saturnbomberman.objects.Bomb> onTopBombs = null;
	public com.github.vegeto079.saturnbomberman.objects.Bomb pickedUpBomb = null;

	public int onDinosaur = -1;

	public DoublePoint exactPoint = new DoublePoint(-1, -1);

	public boolean upPressed = false;
	public boolean downPressed = false;
	public boolean leftPressed = false;
	public boolean rightPressed = false;
	public boolean aPressed = false;
	public boolean bPressed = false;
	public boolean cPressed = false;
	public boolean lPressed = false;
	public boolean rPressed = false;

	public boolean moving = false;

	public Stats stats = new Stats();

	private long lastCPress = 0;
	public boolean isReset = false;
	private int index = -1;

	public Player(MainBomberman game) {
		this.game = game;
		reset();
	}

	public boolean isReset() {
		return isReset;
	}

	public void reset() {
		isReset = true;
		if (character != null) {
			character.animation = Animation.IDLE;
			character.facing = 0;
		}
		cellPoint = new Point(-1, -1);
		exactPoint = new DoublePoint(-1, -1);
		deadTimer = -1;
		firePower = 2;
		speed = 1;
		bombAmt = 1;
		bombType = BombType.NORMAL;
		kickBomb = false;
		powerGlove = false;
		lineBomb = false;
		absorptionTimer = -1;
		skullType = -1;
		skullTimer = -1;
		devilTimer = -1;
		combineTimer = -1;
		carryingBombTick = -1;
		lastKickedBomb = null;
		onTopBombs = null;
		pickedUpBomb = null;
		onDinosaur = -1;
		moving = false;
		resetControls();
	}

	public void resetControls() {
		upPressed = false;
		downPressed = false;
		leftPressed = false;
		rightPressed = false;
		aPressed = false;
		bPressed = false;
		cPressed = false;
		lPressed = false;
		rPressed = false;
	}

	public void tick(MainBomberman game) {
		// deadTimer = -1;
		// TODO
		// bombType = BombType.JELLY;
		// bombAmt = 1;
		// kickBomb = true;
		// powerGlove = true;
		this.game = game;
		if (game.state.is(StateEnum.STAGE)) {
			long timeLeft = (long) (game.counter[2] + game.options.time * 60000 - game.currentTimeMillis());
			timeLeft = timeLeft + 4000;
			if (!game.network.isPlayingOnline())
				timeLeft += 1450;
			int minutesLeft = (int) Math.floor((double) timeLeft / 60000d);
			if (minutesLeft == game.options.time) {
				lastButtonPress = game.currentTimeMillis(); // Prevent idle
															// animation
				return; // 'GO!' text on screen, don't move
			}
			if (index == 0 && game.counter[7] > 1000) {
				new AI(game, this, 0).mouseMover(game);
			}
			if (skullTimer > game.currentTimeMillis()) {
				ArrayList<Player> players = game.players;
				if (skullType == 8) {
					skullTimer = -1;
					int i = Tools.random(game, players.size(), "Skull random player switching positions");
					Player otherPlayer = null;
					while (otherPlayer == null) {
						i++;
						if (i >= players.size())
							i = 0;
						if (players.get(i).equals(this))
							continue; // don't pick self!
						if (players.get(i).deadTimer != -1)
							continue; // pick someone alive!
						otherPlayer = players.get(i);
					}
					DoublePoint otherPoint = otherPlayer.getExactPoint();
					otherPlayer.setExactPoint(getExactPoint());
					otherPlayer.lastButtonPress = game.currentTimeMillis();
					setExactPoint(otherPoint);
				} else if (skullType == 9)
					if (upPressed == false && downPressed == false && leftPressed == false && rightPressed == false)
						switch (character.facing) {
						case 0:
							downPressed = true;
						case 1:
							upPressed = true;
						case 2:
							leftPressed = true;
						case 3:
							rightPressed = true;
						}
				// 0: Slows your Bomberman down
				// 1: Speeds your Bomberman up
				// 2: Makes your bomb's fuse very short
				// 3: Makes your bomb's fuse very long
				// 4: Makes the size of your explosions small
				// 5: Your character is no longer able to lay bombs
				// 6: Your character can't stop laying bombs
				// 7: Your controls are reversed
				// 8: You switch places with another Bomberman (including
				// switching Tyras if applicable)
				// 9: Your character can't stop moving
				if (skullType != -1) // Could have been switched by 8 above
					for (Player p : players)
						if (!p.equals(this) && p.getCellPoint().distance(getCellPoint()) == 0
								&& p.skullTimer <= game.currentTimeMillis() && p.deadTimer == -1) {
							boolean ourSkull = game.network.talking.skullInfect(this, p);
							if (!game.network.isPlayingOnline() || ourSkull) {
								game.sound.play("Hey");
								p.skullTimer = skullTimer;
								p.skullType = skullType;
							}
						}
			} else if (skullType != -1) {
				if (skullType == 9)
					resetControls();
				// only reset controls if we've been force moved (skull type 9)
				skullType = -1;
			}
		} else if (game.state.is(StateEnum.MATCH_SELECTION)
				&& (game.counter[0] == 10 || game.counter[0] == 11 || game.counter[0] == 12)) {
			if (character != null)
				character.stepAnimation(this);
			return; // Tick if on character select
		} else {
			return; // Don't need to tick when not on Stage
		}
		isReset = false;
		int alivePlayers = 0;
		for (int i = 0; i < game.players.size(); i++)
			if (game.players.get(i).deadTimer == -1)
				alivePlayers++;
		boolean stunnedSpinningAnimaton = character.animation == Animation.STUNNED_SPINNING;
		boolean bombThrowingAnimation = character.animation == Animation.HOLDING_BOMB_THROWING;
		if (deadTimer != -1 && character.animation != Animation.DYING) {
			if (alivePlayers < 2 && !game.temp[1].contains("Played Match End")) {
				game.sound.play("Match End");
				game.temp[1] += ".Played Match End:" + game.currentTimeMillis() + ":";
			} else
				game.sound.play("Dying");
			character.animation = Animation.DYING;
			character.animationTick = 0;
		}
		if (game.temp[1].contains("Played Match End") && !game.temp[1].contains("Played I Won")
				&& game.currentTimeMillis()
						- Long.parseLong(game.temp[1].split(".Played Match End:")[1].split(":")[0]) >= 2500
				&& deadTimer == -1) {
			game.temp[1] += ".Played I Won";
			game.sound.play("I Won");
			stats.increaseWins();
			for (int i = 0; i < game.players.size(); i++)
				if (!game.players.get(i).equals(this))
					game.players.get(i).stats.increaseLosses();
		}
		boolean dyingAnimation = character.animation == Animation.DYING;
		if (dyingAnimation && deadTimer == 0) {
			boolean stripItems = true;
			while (stripItems)
				stripItems = placeRandomItemToBoard();
			deadTimer = 1;
		}
		if (dyingAnimation && character.animationTick == -1) {
			exactPoint = new DoublePoint(-10, -10);
		}
		if (upPressed || downPressed || leftPressed || rightPressed || aPressed || bPressed || cPressed || lPressed
				|| rPressed) {
			lastButtonPress = game.currentTimeMillis();
			if (character.animation.equals(Animation.IDLE_ANIMATION))
				character.animation = Animation.IDLE;
		} else if (game.currentTimeMillis() - lastButtonPress >= 5000)
			character.animation = Animation.IDLE_ANIMATION;
		if (character != null) {
			if (pickedUpBomb != null) {
				lastButtonPress = game.currentTimeMillis();
				pickedUpBomb.exactPoint = exactPoint;
				if (upPressed)
					character.animation = Animation.HOLDING_BOMB_WALKING_UP;
				else if (downPressed)
					character.animation = Animation.HOLDING_BOMB_WALKING_DOWN;
				else if (leftPressed)
					character.animation = Animation.HOLDING_BOMB_WALKING_LEFT;
				else if (rightPressed)
					character.animation = Animation.HOLDING_BOMB_WALKING_RIGHT;
				else if (character.animation.equals(Animation.HOLDING_BOMB_WALKING_UP)
						|| character.animation.equals(Animation.HOLDING_BOMB_WALKING_DOWN)
						|| character.animation.equals(Animation.HOLDING_BOMB_WALKING_LEFT)
						|| character.animation.equals(Animation.HOLDING_BOMB_WALKING_RIGHT))
					character.animation = Animation.HOLDING_BOMB_IDLE;
				if (pickedUpBomb.pickedUpCount < 1) {
					character.animation = Animation.HOLDING_BOMB_PICKING_UP;
					pickedUpBomb.pickedUpCount = 1;
				}
			} else {
				if (upPressed)
					character.animation = Animation.WALKING_UP;
				else if (downPressed)
					character.animation = Animation.WALKING_DOWN;
				else if (leftPressed)
					character.animation = Animation.WALKING_LEFT;
				else if (rightPressed)
					character.animation = Animation.WALKING_RIGHT;
				else if (aPressed || bPressed || cPressed || lPressed || rPressed)
					character.animation = Animation.WALKING_IN_PLACE;
				else if (character.animation.equals(Animation.WALKING_UP)
						|| character.animation.equals(Animation.WALKING_DOWN)
						|| character.animation.equals(Animation.WALKING_LEFT)
						|| character.animation.equals(Animation.WALKING_RIGHT))
					character.animation = Animation.IDLE;
			}
			if (bombThrowingAnimation) {
				if (character.animationTick == 2) {
					// Let us change out of the throwing animation since it
					// moved far enough
					character.animation = Animation.IDLE;
				} else
					character.animation = Animation.HOLDING_BOMB_THROWING;
			}
			if (stunnedSpinningAnimaton) {
				if (character.animationTick > 8) {
					character.animation = Animation.IDLE;
				} else
					character.animation = Animation.STUNNED_SPINNING;
			}
			if (dyingAnimation) {
				character.animation = Animation.DYING;
				if (character.animationTick == -1) {
					// You are dead, do not draw.
					if (isCpu)
						ai.reset();
					return;
				} else {
					// In process of dying, let draw.
				}
			}
			if (alivePlayers == 1 && deadTimer == -1 && game.temp[1].contains(".Played Match End:")
					&& game.currentTimeMillis()
							- Long.parseLong(game.temp[1].split(".Played Match End:")[1].split(":")[0]) >= 2500) {
				if (game.currentTimeMillis()
						- Long.parseLong(game.temp[1].split(".Played Match End:")[1].split(":")[0]) >= 2500
						&& game.currentTimeMillis()
								- Long.parseLong(game.temp[1].split(".Played Match End:")[1].split(":")[0]) <= 2520)
					character.animationTick = 0;
				else
					character.animation = Animation.WINNING;
			}
			character.stepAnimation(this);
			if (character.animation == Animation.HOLDING_BOMB_THROWING
					|| character.animation == Animation.STUNNED_SPINNING || character.animation == Animation.DYING
					|| character.animation == Animation.WINNING || deadTimer != -1)
				return;
		}
		ArrayList<com.github.vegeto079.saturnbomberman.objects.Object> objects = new ArrayList<com.github.vegeto079.saturnbomberman.objects.Object>();
		objects = new ArrayList<com.github.vegeto079.saturnbomberman.objects.Object>(game.objects);
		ArrayList<com.github.vegeto079.saturnbomberman.objects.Bomb> bombList = new ArrayList<com.github.vegeto079.saturnbomberman.objects.Bomb>();
		for (int i = 0; i < objects.size(); i++)
			if (objects.get(i) instanceof com.github.vegeto079.saturnbomberman.objects.Item) {
				com.github.vegeto079.saturnbomberman.objects.Item item = (com.github.vegeto079.saturnbomberman.objects.Item) objects
						.get(i);
				if (getCellPoint().distance(item.cellPoint) == 0) {
					if (item.collectable)
						item.activate(game, this, false, game.currentTimeMillis());
				}
			} else if (objects.get(i) instanceof com.github.vegeto079.saturnbomberman.objects.Bomb) {
				com.github.vegeto079.saturnbomberman.objects.Bomb bomb = (com.github.vegeto079.saturnbomberman.objects.Bomb) objects
						.get(i);
				bombList.add(bomb);
				if (getCellPoint().distance(bomb.cellPoint) == 0) {
					if (bomb.type.equals(BombType.LAND_MINE) && !bomb.playerOrigin.equals(this)
							&& bomb.explodeTime < game.currentTimeMillis())
						bomb.explodeTime = game.currentTimeMillis() + 2000;
				}
			}
		if (isCpu && game.stage != null && game.stage.initiated && game.pictures.initialized) {
			if (ai == null) {
				int thisDifficulty = 1;
				if (game.options.comLevel == 0)
					thisDifficulty = Tools.random(game, 1, 5, "Difficulty");
				else if (game.options.comLevel == 1)
					thisDifficulty = Tools.random(game, 1, 2, "Difficulty");
				else if (game.options.comLevel == 2)
					thisDifficulty = Tools.random(game, 2, 3, "Difficulty");
				else if (game.options.comLevel == 3)
					thisDifficulty = Tools.random(game, 2, 4, "Difficulty");
				else if (game.options.comLevel == 4)
					thisDifficulty = Tools.random(game, 3, 4, "Difficulty");
				else if (game.options.comLevel == 5)
					thisDifficulty = Tools.random(game, 4, 5, "Difficulty");
				else if (game.options.comLevel == 6)
					thisDifficulty = 6;
				else if (game.options.comLevel == 7)
					thisDifficulty = 7;
				ai = new AI(game, this, thisDifficulty);
			}
			if (!game.network.isPlayingOnline() || game.network.weAreHost())
				ai.tick(!game.freezeAI, true);
		} else if (game.stage != null && game.stage.initiated && game.pictures.initialized) {
			if (ai == null)
				ai = new AI(game, this, 5);
			// player. use cpu as intermediary?
		}
		DoublePoint ourCenterPoint = new DoublePoint(getMiddleOfBodyPoint());
		double move = (double) speed;
		move = 1 + speed * 0.1d;
		if (isCpu && ai != null)
			move *= ((double) ai.speedRatio / 100d);
		if (skullTimer > game.currentTimeMillis())
			if (skullType == 0)
				move = .25d;
			else if (skullType == 1)
				move = 5;
		move += .7d;
		int correctAmt = 24;

		Point cell = getCellPoint();
		Point goingToCell = null;
		int direction = -1;
		if (upPressed == true) {
			goingToCell = new Point(cell.x, cell.y - 1);
			direction = 0;
		} else if (downPressed == true) {
			goingToCell = new Point(cell.x, cell.y + 1);
			direction = 1;
		} else if (leftPressed == true) {
			goingToCell = new Point(cell.x - 1, cell.y);
			direction = 2;
		} else if (rightPressed == true) {
			goingToCell = new Point(cell.x + 1, cell.y);
			direction = 3;
		}
		if (onTopBombs != null) {
			// Remove them if we're not colliding anymore
			Point movingToPoint = new Point(getMiddleOfBodyPoint().x, getMiddleOfBodyPoint().y);
			Point topRightPoint = new Point(movingToPoint.x + 14, movingToPoint.y - 8);
			Point bottomLeftPoint = new Point(movingToPoint.x - 9, movingToPoint.y + 14);
			Point bottomRightPoint = new Point(movingToPoint.x + 14, movingToPoint.y + 14);
			Point topLeftPoint = new Point(movingToPoint.x - 9, movingToPoint.y - 8);

			int diff = 25;
			for (int i = 0; onTopBombs != null && i < onTopBombs.size(); i++) {
				Point bombMidPoint = new Point(onTopBombs.get(i).exactPoint.toPoint().x + 26,
						onTopBombs.get(i).exactPoint.toPoint().y + 26);
				Rectangle bombBounds = new Rectangle(bombMidPoint.x - diff / 2, bombMidPoint.y - diff / 2, diff + 1,
						diff + 1);
				boolean bombContainsPlayer = bombBounds.contains(topRightPoint) || bombBounds.contains(bottomLeftPoint)
						|| bombBounds.contains(bottomRightPoint) || bombBounds.contains(topLeftPoint);
				if (!bombContainsPlayer || onTopBombs.get(i).exploding || onTopBombs.get(i).finished) {
					onTopBombs.remove(i);
					if (onTopBombs.size() == 0)
						onTopBombs = null;
				}
			}
		}
		if (goingToCell != null) {
			for (com.github.vegeto079.saturnbomberman.objects.Bomb bomb : bombList) {
				if (onTopBombs != null) {
					boolean isBombOnTop = false;
					for (int i = 0; i < onTopBombs.size(); i++)
						if (onTopBombs.get(i).getCellPoint().distance(bomb.getCellPoint()) == 0)
							isBombOnTop = true;
					if (isBombOnTop)
						continue;
				}
				if (goingToCell.distance(bomb.getCellPoint()) == 0) {
					boolean closeEnoughToHit = false;
					// TODO: fix collision/kick points? they seem to be a bit
					// off but idk
					int diff = 30;
					Point bombMidPoint = new Point(bomb.exactPoint.toPoint().x + 26, bomb.exactPoint.toPoint().y + 26);
					Rectangle bombBounds = new Rectangle(bombMidPoint.x - diff / 2, bombMidPoint.y - diff / 2 - 3, diff,
							diff + 3);
					Point movingToPoint = new Point(getMiddleOfBodyPoint().x, getMiddleOfBodyPoint().y);
					Point topRightPoint = new Point(movingToPoint.x + 14, movingToPoint.y - 8);
					Point bottomLeftPoint = new Point(movingToPoint.x - 9, movingToPoint.y + 14);
					Point bottomRightPoint = new Point(movingToPoint.x + 14, movingToPoint.y + 14);
					Point topLeftPoint = new Point(movingToPoint.x - 9, movingToPoint.y - 8);

					if (bombBounds.contains(topRightPoint) || bombBounds.contains(bottomLeftPoint)
							|| bombBounds.contains(bottomRightPoint) || bombBounds.contains(topLeftPoint))
						closeEnoughToHit = true;

					if (closeEnoughToHit) {
						// Don't spam kick bombs
						if (lastKickedBomb != null && lastKickedBomb.equals(bomb)
								&& game.currentTimeMillis() - lastKickedBombTime < 250)
							continue;
						if (kickBomb && !bomb.exploding && !bomb.finished) {
							Point bombGoingToCell = null;
							if (upPressed == true)
								bombGoingToCell = new Point(goingToCell.x, goingToCell.y - 1);
							else if (downPressed == true)
								bombGoingToCell = new Point(goingToCell.x, goingToCell.y + 1);
							else if (leftPressed == true)
								bombGoingToCell = new Point(goingToCell.x - 1, goingToCell.y);
							else if (rightPressed == true)
								bombGoingToCell = new Point(goingToCell.x + 1, goingToCell.y);
							int[][] stage = game.stage.get();
							boolean playSound = false;
							if (bomb.moving != direction && bombGoingToCell.x >= 0 && bombGoingToCell.y >= 0
									&& bombGoingToCell.x < stage[0].length && bombGoingToCell.y < stage.length) {
								playSound = true;
							}
							if (playSound) {
								game.network.talking.bombKick(bomb, this, direction);
								if (!game.network.isPlayingOnline()) {
									game.sound.play("Bomb GO");
									bomb.playerKicked = this;
									bomb.moving = direction;
								}
								lastKickedBomb = bomb;
								lastKickedBombTime = game.currentTimeMillis();
							}
						}
						return;
					}
				}
			}
		}
		if (upPressed) {
			if (canMove(1, ourCenterPoint, 0, -move))
				movePoint(0, -move);
			else if (move > 1 && canMove(1, ourCenterPoint, 0, -1))
				movePoint(0, -1);
			else if ((!rightPressed && !leftPressed) || (Tools.random(null, 0, 100, "Moving Correction") > 95
					&& !cellContainsBomb(bombList, goingToCell))) {
				for (int i = 0; i < correctAmt; i++)
					if (canMove(0, ourCenterPoint, -i, -1)) {
						movePoint(-1, 0);
						break;
					} else if (canMove(0, ourCenterPoint, i, -1)) {
						movePoint(1, 0);
						break;
					}
			}
		}
		if (downPressed) {
			if (canMove(0, ourCenterPoint, 0, move))
				movePoint(0, move);
			else if (move > 1 && canMove(0, ourCenterPoint, 0, 1))
				movePoint(0, 1);
			else if ((!rightPressed && !leftPressed) || (Tools.random(null, 0, 100, "Moving Correction") > 95
					&& !cellContainsBomb(bombList, goingToCell))) {
				for (int i = 0; i < correctAmt; i++)
					if (canMove(0, ourCenterPoint, -i, 1)) {
						movePoint(-1, 0);
						break;
					} else if (canMove(0, ourCenterPoint, i, 1)) {
						movePoint(1, 0);
						break;
					}
			}
		}
		if (leftPressed) {
			if (canMove(2, ourCenterPoint, -move, 0))
				movePoint(-move, 0);
			else if (move > 1 && canMove(2, ourCenterPoint, -1, 0))
				movePoint(-1, 0);
			else if ((!upPressed && !downPressed) || (Tools.random(null, 0, 100, "Moving Correction") > 95
					&& !cellContainsBomb(bombList, goingToCell))) {
				for (int i = 0; i < correctAmt; i++)
					if (canMove(0, ourCenterPoint, -1, -i)) {
						movePoint(0, -1);
						break;
					} else if (canMove(0, ourCenterPoint, -1, i)) {
						movePoint(0, 1);
						break;
					}
			}
		}
		if (rightPressed)
			if (canMove(3, ourCenterPoint, move, 0))
				movePoint(move, 0);
			else if (move > 1 && canMove(3, ourCenterPoint, 1, 0))
				movePoint(1, 0);
			else if ((!upPressed && !downPressed) || (Tools.random(null, 0, 100, "Moving Correction") > 95
					&& !cellContainsBomb(bombList, goingToCell))) {
				for (int i = 0; i < correctAmt; i++)
					if (canMove(0, ourCenterPoint, 1, -i)) {
						movePoint(0, -1);
						break;
					} else if (canMove(0, ourCenterPoint, 1, i)) {
						movePoint(0, 1);
						break;
					}
			}
		if (!upPressed && !downPressed && !leftPressed && !rightPressed)
			movePoint(0, 0);
		if (aPressed) {
			if (powerGlove) {
				if (pickedUpBomb == null && onTopBombs != null) {
					for (int i = 0; i < onTopBombs.size(); i++) {
						Bomb bomb = onTopBombs.get(i);
						if (bomb.type != BombType.LAND_MINE && bomb.playerOrigin.equals(this)) {
							long time = game.currentTimeMillis();
							pickedUpBomb = new Bomb(bomb, time);
							game.network.talking.bombPickUp(pickedUpBomb, this, time);
							bomb.remove();
							onTopBombs.remove(i);
							if (onTopBombs.size() == 0)
								onTopBombs = null;
							break;
						}
					}
				} else if (pickedUpBomb != null) {
					game.network.talking.bombThrow(pickedUpBomb, this);
					if (!game.network.isPlayingOnline()) {
						game.sound.play("Bomb GO");
						pickedUpBomb.pickedUpCount = 3 + (character.facing * 5);
						pickedUpBomb.cellPoint = getCellPoint();
						character.animation = Animation.HOLDING_BOMB_THROWING;
						character.animationTick = 0;
						DoublePoint p = new DoublePoint(Stage.getExactTileMidPoint(game, getCellPoint()));
						pickedUpBomb.exactPoint = new DoublePoint(p.x + pickedUpBomb.adjustExactPoint.x,
								p.y + pickedUpBomb.adjustExactPoint.y);
						game.objects.add(pickedUpBomb);
						pickedUpBomb = null;
					}
				}
			}
			aPressed = false;
		}
		if (bPressed) {
			bPressed = false;
		}
		if (cPressed) {
			if (skullTimer > game.currentTimeMillis() && skullType == 5)
				return; // Can't place bombs
			if (pickedUpBomb == null && getBombsLaidAmt() < bombAmt) {
				if (lineBomb && game.currentTimeMillis() - lastCPress <= 200) {
					if (!isCpu) {
						game.logger.log(LogLevel.DEBUG,
								"linebomb[" + getIndex() + "]: called by player #" + getIndex() + "!");
						Point newCell = getCellPoint();
						int bombsLaid = getBombsLaidAmt();
						Bomb firstBomb = null;
						for (int i = 0; firstBomb == null && i < onTopBombs.size(); i++)
							if (onTopBombs.get(i).playerOrigin.equals(this))
								firstBomb = onTopBombs.get(i);
						boolean laidABomb = false;
						for (int i = bombsLaid; i <= bombAmt; i++) {
							if (character.facing == 0)
								newCell = new Point(newCell.x, newCell.y + 1);
							else if (character.facing == 1)
								newCell = new Point(newCell.x, newCell.y - 1);
							else if (character.facing == 2)
								newCell = new Point(newCell.x - 1, newCell.y);
							else if (character.facing == 3)
								newCell = new Point(newCell.x + 1, newCell.y);
							int tileType = game.stage.get()[newCell.y][newCell.x];
							if (tileType != -1) {
								game.logger.log(LogLevel.DEBUG,
										"linebomb[" + getIndex() + "]: reached tile that's not empty");
								return;
							} else if (newCell.x >= 0 && newCell.y >= 0 && newCell.x < game.stage.getHeight()
									&& newCell.y < game.stage.getWidth()) {
								game.logger.log(LogLevel.DEBUG, "linebomb[" + getIndex() + "]: placing bomb #" + i
										+ " of a possible total of " + bombAmt);
								long time = game.currentTimeMillis();
								Bomb bomb = layBomb(newCell, true, time, -1, getBombTypeToLay());
								game.network.talking.layBomb(this, getCellPoint(), true, time, bomb.identifier,
										bomb.type);
								laidABomb = true;
							} else {
								game.logger.log(LogLevel.DEBUG, "linebomb[" + getIndex() + "]: reached end of map");
								break;
							}
						}
						if (laidABomb)
							game.sound.play("Bomb Placed");
						// onTopBombs.add(firstBomb); // Let us walk on first
						// bomb again, to escape
					}
				} else {
					long time = game.currentTimeMillis();
					Bomb bomb = layBomb(getCellPoint(), false, time, -1, getBombTypeToLay());
					if (bomb == null) {
						// Didn't place bomb
					} else
						game.network.talking.layBomb(this, getCellPoint(), false, time, bomb.identifier, bomb.type);
				}
				cPressed = false;
				lastCPress = game.currentTimeMillis();
			}
		}
		if (skullTimer > game.currentTimeMillis() && skullType == 6 && getBombsLaidAmt() < bombAmt) {
			// TODO: allow client side
			// Can't stop laying bombs
			if (!game.network.isPlayingOnline() || game.network.weAreHost()) {
				long time = game.currentTimeMillis();
				Bomb bomb = layBomb(getCellPoint(), false, time, -1, getBombTypeToLay());
				if (bomb == null) {
					// Didn't place bomb
				} else
					game.network.talking.layBomb(this, getCellPoint(), false, time, bomb.identifier, bomb.type);
			}
		}
		if (lPressed || rPressed) {
			if (lastKickedBomb != null && !lastKickedBomb.finished && !lastKickedBomb.exploding
					&& lastKickedBomb.moving != -1) {
				lastKickedBomb.moving = -1;
				lastKickedBomb.exactPoint = new DoublePoint(
						Stage.getExactTileMidPoint(game, lastKickedBomb.getCellPoint()));
				lastKickedBomb.exactPoint = new DoublePoint(
						lastKickedBomb.exactPoint.x + lastKickedBomb.adjustExactPoint.x,
						lastKickedBomb.exactPoint.y + lastKickedBomb.adjustExactPoint.y);
				game.network.talking.bombMovingStop(lastKickedBomb, lastKickedBomb.playerOrigin, this);
				lPressed = false;
			}
		}
		if (pickedUpBomb != null)
			pickedUpBomb.tick(game);
		if (pickedUpBomb != null) {
			pickedUpBomb.exactPoint = new DoublePoint(exactPoint.x - 8, exactPoint.y - 4);
			if (character.animation == Animation.HOLDING_BOMB_PICKING_UP) {
				if (character.animationTick < 1)
					pickedUpBomb.exactPoint = new DoublePoint(pickedUpBomb.exactPoint.x,
							pickedUpBomb.exactPoint.y + 30);
				else if (character.animationTick == 1)
					pickedUpBomb.exactPoint = new DoublePoint(pickedUpBomb.exactPoint.x,
							pickedUpBomb.exactPoint.y + 14);
			}
		}
		cellPoint = getCellPoint();
	}

	public boolean cellContainsBomb(ArrayList<com.github.vegeto079.saturnbomberman.objects.Bomb> bombList, Point cell) {
		for (com.github.vegeto079.saturnbomberman.objects.Bomb bomb : bombList)
			if (bomb.getCellPoint().distance(cell) == 0)
				return true;
		return false;
	}

	public boolean canMove(int direction, DoublePoint ourCenterPoint, double movingX, double movingY) {
		return Stage.canMove(direction, ourCenterPoint, movingX, movingY, game, getCellPoint(), this);
	}

	public BombType getBombTypeToLay() {
		if (getBombsLaidAmt() == 0 || bombType == BombType.GUN || bombType == BombType.SLOW)
			return bombType;
		else
			return BombType.NORMAL;
	}

	public Bomb layBomb(Point cell, boolean fromLinebomb, long time, long identifier, BombType type) {
		boolean bombExistsOnSpot = false;
		if (identifier == -1)
			identifier = (long) (time - Math.random() * 50000);
		for (com.github.vegeto079.saturnbomberman.objects.Object obj : game.objects)
			if (obj instanceof com.github.vegeto079.saturnbomberman.objects.Bomb)
				if (obj.cellPoint.distance(cell) < 1 && !obj.finished)
					bombExistsOnSpot = true;
		if (!bombExistsOnSpot) {
			if (!fromLinebomb)
				game.sound.play("Bomb Placed");
			Bomb newBomb = null;
			long explodeTime = time + Bomb.LIFE_TIME;
			int theFirePower = firePower;
			if (skullTimer > time)
				if (skullType == 2)
					explodeTime = time + Bomb.LIFE_TIME / 2;
				else if (skullType == 3)
					explodeTime = time + Bomb.LIFE_TIME * 2;
				else if (skullType == 4)
					theFirePower = 2;
			// A bomb is laid down, so place a normal one
			newBomb = new Bomb(cell, type, this, game, theFirePower, explodeTime, identifier);
			if (onTopBombs == null)
				onTopBombs = new ArrayList<Bomb>();
			onTopBombs.add(newBomb);
			for (int i = 0; i < game.players.size(); i++)
				if (game.players.get(i).getCellPoint().distance(cellPoint) == 0 && !game.players.get(i).equals(this)) {
					if (game.players.get(i).onTopBombs == null)
						game.players.get(i).onTopBombs = new ArrayList<Bomb>();
					game.players.get(i).onTopBombs.add(newBomb);
				}
			game.objects.add(newBomb);
			return newBomb;
		}
		return null;
	}

	public boolean placeRandomItemToBoard() {
		int item = Tools.random(game, 0, 16, "Placing random item on board (which powerup)");
		int toRemove = -1;
		int tryCount = 0;
		while (toRemove == -1 && tryCount < 25) {
			// game.logger.log(LogLevel.DEBUG, "tryCount(" + tryCount +
			// ") item(" + item
			// + ") finding which item to take from us");
			tryCount++;
			if (item == 0 && firePower > 2)
				toRemove = item;
			else if (item == 1 && bombAmt > 1)
				toRemove = item;
			else if (item == 2 && speed > 1)
				toRemove = item;
			else if (item == 3 && kickBomb)
				toRemove = item;
			else if (item == 4 && powerGlove)
				toRemove = item;
			else if (item == 6 && lineBomb)
				toRemove = item;
			else if (item == 7 && bombType == BombType.BEAD)
				toRemove = item;
			else if (item == 8 && bombType == BombType.SPIKED)
				toRemove = item;
			else if (item == 9 && bombType == BombType.JELLY)
				toRemove = item;
			else if (item == 10 && bombType == BombType.POWER)
				toRemove = item;
			else if (item == 11 && bombType == BombType.LAND_MINE)
				toRemove = item;
			else
				item++;
			if (item > 11)
				item = 0;
		}
		// game.logger.log(LogLevel.DEBUG, "tryCount(" + tryCount + ") item(" +
		// item
		// + ") got item to remove: " + toRemove);
		if (toRemove == -1)
			return false; // We had no items to remove
		tryCount = 0;
		Point randomEmptyBoardPoint = null;
		int boardPointX = Tools.random(game, 0, game.stage.getWidth() - 1,
				"Placing random item on board (board point X)");
		int boardPointY = Tools.random(game, 0, game.stage.getHeight() - 1,
				"Placing random item on board (board point Y)");
		while (randomEmptyBoardPoint == null && tryCount < 1000) {
			// game.logger.log(LogLevel.DEBUG, "tryCount(" + tryCount
			// + ") finding empty place to put item (" + boardPointX + ","
			// + boardPointY + ")");
			tryCount++;
			boolean somethingOnSquare = false;
			if (game.stage.getIndex(boardPointX, boardPointY) == -1) {
				ArrayList<Player> players = game.players;
				for (Player p : players)
					if (!somethingOnSquare)
						if (p.getCellPoint().distance(new Point(boardPointY, boardPointX)) == 0)
							somethingOnSquare = true;
				ArrayList<com.github.vegeto079.saturnbomberman.objects.Object> objects = game.objects;
				for (com.github.vegeto079.saturnbomberman.objects.Object obj : objects)
					if (!somethingOnSquare)
						if (obj.cellPoint.distance(new Point(boardPointY, boardPointX)) == 0)
							somethingOnSquare = true;
				if (!somethingOnSquare) {
					// No player or block exists on square, place the item there
					randomEmptyBoardPoint = new Point(boardPointY, boardPointX);
					Item itemToPlace = new Item(randomEmptyBoardPoint, game, item);
					itemToPlace.visible = true;
					game.objects.add(itemToPlace);

				}
			} else
				somethingOnSquare = true;
			if (somethingOnSquare) {
				boardPointX++;
				if (boardPointX >= game.stage.getWidth()) {
					boardPointX = 0;
					boardPointY++;
					if (boardPointY >= game.stage.getHeight())
						boardPointY = 0;
				}
			}
		}
		if (randomEmptyBoardPoint != null) {
			game.logger.log(LogLevel.DEBUG, "Success! Removing powerup from us (" + item + "). Item placed on square ("
					+ boardPointX + "," + boardPointY + ").");
			if (item == 0)
				firePower--;
			else if (item == 1)
				bombAmt--;
			else if (item == 2)
				speed--;
			else if (item == 3)
				kickBomb = false;
			else if (item == 4)
				powerGlove = false;
			else if (item == 6)
				lineBomb = false;
			else if (item >= 7 && item <= 11)
				bombType = BombType.NORMAL;
			return true;
		} else
			return false;
	}

	public boolean equals(Player otherPlayer) {
		return character != null && otherPlayer.character != null && character.equals(otherPlayer.character);
	}

	public Point getMiddleOfBodyPoint() {
		Point middleOfBodyOffset = new Point(14, 45);
		Point ourPoint = new Point((int) getExactPoint().x + middleOfBodyOffset.x,
				(int) getExactPoint().y + middleOfBodyOffset.y);
		return ourPoint;
	}

	public static Point[] getCollisionPoints(Point centerPoint) {
		Point topRightPoint = new Point(centerPoint.x + 14, centerPoint.y - 8);
		Point bottomLeftPoint = new Point(centerPoint.x - 9, centerPoint.y + 14);
		Point bottomRightPoint = new Point(centerPoint.x + 14, centerPoint.y + 14);
		Point topLeftPoint = new Point(centerPoint.x - 9, centerPoint.y - 8);
		return new Point[] { topRightPoint, bottomLeftPoint, bottomRightPoint, topLeftPoint };
	}

	public Point getCellPoint() {
		if (game.stage == null)
			return new Point(-1, -1);
		Point stageOffset = game.stage.getOffset();
		Point middleOfBodyOffset = new Point(14, 45);
		Point ourPoint = new Point(
				(int) Math.floor(((double) getExactPoint().x - (double) stageOffset.x + (double) middleOfBodyOffset.x)
						/ (double) Stage.SQUARE_WIDTH),
				(int) Math.floor(((double) getExactPoint().y - (double) stageOffset.y + (double) middleOfBodyOffset.y)
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

	private void movePoint(double x, double y) {
		DoublePoint newPoint = new DoublePoint(exactPoint.x + x, exactPoint.y + y);
		newPoint.round(3);
		if (exactPoint.distance(newPoint) > 0) {
			if (!game.network.isPlayingOnline() || game.network.talking.moveLocation(this, newPoint.x, newPoint.y,
					character.facing, upPressed, downPressed, leftPressed, rightPressed)) {
				exactPoint = newPoint;
				moving = true;
			} else if (moving) {
				exactPoint = newPoint;
			}
		} else if (moving && (!game.network.isPlayingOnline()
				|| game.network.talking.stopMoving(this, newPoint.x, newPoint.y, character.facing))) {
			moving = false;
		}
	}

	public DoublePoint getExactPoint() {
		return exactPoint;
	}

	public void setExactPoint(DoublePoint exactPoint) {
		this.exactPoint = exactPoint;
	}

	public void keyUp(int keyID) {
		if (keyID == 0)
			if (skullTimer > game.currentTimeMillis() && skullType == 7)
				downPressed = false; // reverse
			else if (skullTimer > game.currentTimeMillis() && skullType == 9 && !downPressed && !rightPressed
					&& !leftPressed)
				upPressed = true; // keep moving
			else
				upPressed = false;
		else if (keyID == 1)
			if (skullTimer > game.currentTimeMillis() && skullType == 7)
				upPressed = false; // reverse
			else if (skullTimer > game.currentTimeMillis() && skullType == 9 && !upPressed && !rightPressed
					&& !leftPressed)
				downPressed = true; // keep moving
			else
				downPressed = false;
		else if (keyID == 2)
			if (skullTimer > game.currentTimeMillis() && skullType == 7)
				rightPressed = false; // reverse
			else if (skullTimer > game.currentTimeMillis() && skullType == 9 && !upPressed && !downPressed
					&& !rightPressed)
				leftPressed = true; // keep moving
			else
				leftPressed = false;
		else if (keyID == 3)
			if (skullTimer > game.currentTimeMillis() && skullType == 7)
				leftPressed = false; // reverse
			else if (skullTimer > game.currentTimeMillis() && skullType == 9 && !upPressed && !downPressed
					&& !leftPressed)
				rightPressed = true; // keep moving
			else
				rightPressed = false;
		else if (keyID == 4)
			aPressed = false;
		else if (keyID == 5)
			bPressed = false;
		else if (keyID == 6)
			cPressed = false;
		else if (keyID == 7)
			lPressed = false;
		else if (keyID == 8)
			rPressed = false;
	}

	public void keyDown(int keyID) {
		if (keyID == 0)
			if (skullTimer > game.currentTimeMillis() && skullType == 7)
				downPressed = true;// reverse
			else if (skullTimer > game.currentTimeMillis() && skullType == 9) {
				upPressed = true;
				downPressed = false;
				leftPressed = false;
				rightPressed = false;
			} else
				upPressed = true;
		else if (keyID == 1)
			if (skullTimer > game.currentTimeMillis() && skullType == 7)
				upPressed = true;// reverse
			else if (skullTimer > game.currentTimeMillis() && skullType == 9) {
				upPressed = false;
				downPressed = true;
				leftPressed = false;
				rightPressed = false;
			} else
				downPressed = true;
		else if (keyID == 2)
			if (skullTimer > game.currentTimeMillis() && skullType == 7)
				rightPressed = true;// reverse
			else if (skullTimer > game.currentTimeMillis() && skullType == 9) {
				upPressed = false;
				downPressed = false;
				leftPressed = true;
				rightPressed = false;
			} else
				leftPressed = true;
		else if (keyID == 3)
			if (skullTimer > game.currentTimeMillis() && skullType == 7)
				leftPressed = true;// reverse
			else if (skullTimer > game.currentTimeMillis() && skullType == 9) {
				upPressed = false;
				downPressed = false;
				leftPressed = false;
				rightPressed = true;
			} else
				rightPressed = true;
		else if (keyID == 4)
			aPressed = true;
		else if (keyID == 5)
			bPressed = true;
		else if (keyID == 6)
			cPressed = true;
		else if (keyID == 7)
			lPressed = true;
		else if (keyID == 8)
			rPressed = true;
	}

	public int getBombsLaidAmt() {
		int bombsLaid = 0;
		for (com.github.vegeto079.saturnbomberman.objects.Object obj : game.objects)
			if (obj instanceof com.github.vegeto079.saturnbomberman.objects.Bomb)
				if (((Bomb) obj).playerOrigin.equals(this) && !obj.finished)
					bombsLaid++;
		return bombsLaid;
	}

	public ArrayList<Bomb> getBombsLaid() {
		ArrayList<Bomb> bombs = new ArrayList<Bomb>();
		for (com.github.vegeto079.saturnbomberman.objects.Object obj : game.objects)
			if (obj instanceof com.github.vegeto079.saturnbomberman.objects.Bomb)
				if (((Bomb) obj).playerOrigin.equals(this) && !obj.finished)
					bombs.add((Bomb) obj);
		return bombs;
	}

	/**
	 * Sets index if it isn't already set, and retrieves it either way.
	 */
	public int getIndex() {
		for (int i = 0; index == -1 && i < game.players.size(); i++)
			if (game.players.get(i).equals(this))
				setIndex(i);
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public class Stats {
		private ArrayList<Integer> thisStageKilledPlayers = new ArrayList<Integer>();
		private int thisStageKilledByPlayer = -1;
		private ArrayList<Integer> killedPlayers = new ArrayList<Integer>();
		private ArrayList<Integer> killedByPlayers = new ArrayList<Integer>();
		private int totalBattles = 0;
		private int wins = 0;
		private int losses = 0;
		private int draws = 0;
		private int cacheRank = 0;

		public ArrayList<Integer> getThisStageKilledPlayers() {
			return thisStageKilledPlayers;
		}

		public int getThisStageKilledByPlayer() {
			return thisStageKilledByPlayer;
		}

		public int getWinPercent() {
			return (int) ((float) wins / (float) totalBattles * 100f);
		}

		public int getWins() {
			return wins;
		}

		public void increaseWins() {
			wins++;
			totalBattles++;
		}

		public int getLosses() {
			return losses;
		}

		public void increaseLosses() {
			losses++;
			totalBattles++;
		}

		public int getDraws() {
			return draws;
		}

		public void increaseDraws() {
			draws++;
			totalBattles++;
		}

		public int getTotalBattles() {
			return totalBattles;
		}

		public ArrayList<Integer> getKilledPlayers() {
			return killedPlayers;
		}

		public int getTotalKilledPlayersAmt() {
			int kills = 0;
			for (int player : killedPlayers)
				if (player != getIndex())
					kills++;
			return kills + getThisStageKilledPlayersAmt();
		}

		public int getTotalKilledPlayerAmt(int killedPlayer) {
			int kills = 0;
			for (int player : killedPlayers)
				if (player == killedPlayer)
					kills++;
			return kills + getThisStageKilledPlayerAmt(killedPlayer);
		}

		public int getThisStageKilledPlayerAmt(int killedPlayer) {
			int kills = 0;
			for (int i = 0; i < thisStageKilledPlayers.size(); i++) {
				if (thisStageKilledPlayers.get(i) == killedPlayer)
					kills++;
			}
			return kills;
		}

		public int getThisStageKilledPlayersAmt() {
			boolean killedSelf = false;
			for (int i = 0; i < thisStageKilledPlayers.size(); i++) {
				// System.out.println("we killed(" +
				// thisStageKilledPlayers.get(i)
				// + "). We are (" + getIndex() + ")");
				if (thisStageKilledPlayers.get(i) == getIndex())
					killedSelf = true;
			}
			if (killedSelf)
				return thisStageKilledPlayers.size() - 1;
			else
				return thisStageKilledPlayers.size();
		}

		public int getSuicideAmt() {
			int suicides = 0;
			ArrayList<Integer> killedPlayers = getKilledPlayers();
			for (int i = 0; i < killedPlayers.size(); i++)
				if (killedPlayers.get(i) == getIndex())
					suicides++;
			killedPlayers = getThisStageKilledPlayers();
			for (int i = 0; i < killedPlayers.size(); i++)
				if (killedPlayers.get(i) == getIndex())
					suicides++;
			return suicides;
		}

		public int getEnvironmentalDeaths() {
			int envrionmentalDeaths = 0;
			ArrayList<Integer> killedPlayers = getKilledByPlayers();
			for (int i = 0; i < killedPlayers.size(); i++)
				if (killedPlayers.get(i) == Integer.MAX_VALUE)
					envrionmentalDeaths++;
			if (thisStageKilledByPlayer == Integer.MAX_VALUE)
				envrionmentalDeaths++;
			return envrionmentalDeaths;
		}

		public ArrayList<Integer> getKilledByPlayers() {
			return killedByPlayers;
		}

		public int getCacheRank() {
			return cacheRank;
		}

		public int getRank(ArrayList<Player> players) {
			ArrayList<Integer> wins = new ArrayList<Integer>();
			ArrayList<Integer> score = new ArrayList<Integer>();
			for (Player p : players) {
				wins.add(p.stats.getWins());
				score.add(p.stats.getTotalKilledPlayersAmt());
			}
			int ourWins = getWins();
			int ourScore = getTotalKilledPlayersAmt();
			int rank = 1;
			for (int i = 0; i < wins.size(); i++) {
				if (ourWins < wins.get(i))
					rank++;
				else if (ourWins == wins.get(i))
					if (ourScore < score.get(i))
						rank++;
			}
			cacheRank = rank;
			return rank;
		}

		public void killedPlayer(int playerIndex) {
			thisStageKilledPlayers.add(playerIndex);
		}

		public void killedByPlayer(int playerIndex) {
			thisStageKilledByPlayer = playerIndex;
		}

		public void newStage() {
			if (thisStageKilledByPlayer != -1)
				killedByPlayers.add(thisStageKilledByPlayer);
			thisStageKilledByPlayer = -1;
			for (int i = 0; i < thisStageKilledPlayers.size(); i++)
				killedPlayers.add(thisStageKilledPlayers.get(i));
			thisStageKilledPlayers = new ArrayList<Integer>();
		}

		public void reset() {
			thisStageKilledPlayers = new ArrayList<Integer>();
			thisStageKilledByPlayer = -1;
			killedPlayers = new ArrayList<Integer>();
			killedByPlayers = new ArrayList<Integer>();
		}

		public Stats copy() {
			Stats s = new Stats();
			s.thisStageKilledPlayers = new ArrayList<Integer>(thisStageKilledPlayers);
			s.thisStageKilledByPlayer = thisStageKilledByPlayer;
			s.killedPlayers = new ArrayList<Integer>(killedPlayers);
			s.killedByPlayers = new ArrayList<Integer>(killedByPlayers);
			s.totalBattles = totalBattles;
			s.wins = wins;
			s.losses = losses;
			s.draws = draws;
			s.cacheRank = cacheRank;
			return s;
		}
	}

	public void draw(Graphics2D g, MainBomberman game) {
		if (character != null)
			character.draw(g, this, game);
	}

	public Player copy() {
		Player p = new Player(game);
		p.index = index;
		p.stats = stats.copy();
		p.character = character.copy();
		return p;
	}

	public static ArrayList<Player> copyList(ArrayList<Player> list) {
		ArrayList<Player> newList = new ArrayList<Player>(list.size());
		for (Player p : list)
			newList.add(p.copy());
		return newList;
	}

	public static enum PlayerComparator implements Comparator<Player> {
		RANK_SORT {
			public int compare(Player o1, Player o2) {
				return o1.stats.getCacheRank() - o2.stats.getCacheRank();
			}
		},
		INDEX_SORT {
			public int compare(Player o1, Player o2) {
				return o1.index - o2.index;
			}
		};

		public static Comparator<Player> decending(final Comparator<Player> other) {
			return new Comparator<Player>() {
				public int compare(Player o1, Player o2) {
					return other.compare(o1, o2);
				}
			};
		}

		public static Comparator<Player> getComparator(final PlayerComparator... multipleOptions) {
			return new Comparator<Player>() {
				public int compare(Player o1, Player o2) {
					for (PlayerComparator option : multipleOptions) {
						int result = option.compare(o1, o2);
						if (result != 0)
							return result;
					}
					return 0;
				}
			};
		}
	}

	/**
	 * Sets direction of our {@link #character} using
	 * {@link character#setDirection(int)}.
	 * 
	 * @param dir
	 *            0: down<br>
	 *            1: up<br>
	 *            2: left<br>
	 *            3: right
	 */
	public void setDirection(int dir) {
		character.setDirection(dir);
	}

	/**
	 * Sets the direction buttons.
	 * 
	 * @param buttons
	 *            Integer array, required size 4.<br/>
	 *            Expected values:<br/>
	 *            [0]: upPressed<br/>
	 *            [1]: downPressed<br/>
	 *            [2]: leftPressed<br/>
	 *            [3]: rightPressed
	 */
	public void setButtonsPushed(boolean[] buttons) {
		if (buttons.length != 4)
			try {
				throw new Exception("Array size not expected (" + buttons.length + ") instead of 4).");
			} catch (Exception e) {
				e.printStackTrace();
				return;
			}
		upPressed = buttons[0];
		downPressed = buttons[1];
		leftPressed = buttons[2];
		rightPressed = buttons[3];
	}

}