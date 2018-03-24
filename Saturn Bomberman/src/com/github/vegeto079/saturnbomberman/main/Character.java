package com.github.vegeto079.saturnbomberman.main;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import com.github.vegeto079.saturnbomberman.misc.Constants;
import com.github.vegeto079.saturnbomberman.misc.Pictures;
import com.github.vegeto079.saturnbomberman.objects.Player;

import com.github.vegeto079.ngcommontools.main.*;

/**
 * 
 * @author Nathan
 * @version 1.0: Started tracking version.
 * @version 1.01: Drawing of Animation.WALKING_UP, WALKING_DOWN, WALKING_LEFT,
 *          and WALKING_RIGHT consolidated into one method. Drawing of
 *          HOLDING_BOMB_WALKING_UP, DOWN, LEFT, and RIGHT also consolidated.
 *          Skull flashing added.
 * @version 1.02: {@link #copy()} added.
 * @version 1.03: Failsafe added to not draw anything when dead.
 * @version 1.04: Added {@link #setDirection(int)} for multiplayer use.
 * @version 1.05: HOLDING_BOMB_WALKING drawing of bomb now properly draws it on
 *          top of the character if they're not facing north.
 */
public class Character {
	public Animation animation = Animation.IDLE;
	public int characterIndex = -1;
	private Pictures pictures = null;
	private int gameTick = 0;
	public int animationTick = 0;
	/**
	 * Which direction we are currently facing.<br>
	 * 0: down<br>
	 * 1: up<br>
	 * 2: left<br>
	 * 3: right
	 */
	public int facing = 0;

	public Character(Pictures pictures, int characterIndex) {
		this.pictures = pictures;
		this.characterIndex = characterIndex;
	}

	public boolean equals(Character character) {
		return equals(character.characterIndex);
	}

	public boolean equals(int characterIndex) {
		return this.characterIndex == characterIndex;
	}

	public Character copy() {
		Character c = new Character(pictures, characterIndex);
		c.animation = animation;
		c.gameTick = gameTick;
		c.animationTick = animationTick;
		c.facing = facing;
		return c;
	}

	public enum Animation {
		IDLE, IDLE_ANIMATION, HOLDING_BOMB_IDLE, WALKING_UP, WALKING_DOWN, WALKING_LEFT, WALKING_RIGHT, WALKING_IN_PLACE, BOMBER_SELECT_UP, BOMBER_SELECT_DOWN, BOMBER_SELECT_IDLE, BOMBER_SELECT_WALKING, HOLDING_BOMB_PICKING_UP, HOLDING_BOMB_THROWING, HOLDING_BOMB_WALKING_UP, HOLDING_BOMB_WALKING_DOWN, HOLDING_BOMB_WALKING_LEFT, HOLDING_BOMB_WALKING_RIGHT, STUNNED_SPINNING, DYING, WINNING
	}

	public void stepAnimation(Player ourPlayer) {
		gameTick++;
		if (animation.equals(Animation.IDLE_ANIMATION)
				|| animation.equals(Animation.BOMBER_SELECT_IDLE)) {
			if (characterIndex == Constants.CHARACTERS.WHITE_BOMBERMAN
					|| characterIndex == Constants.CHARACTERS.BLACK_BOMBERMAN) {
				if (gameTick > 5) {
					gameTick = 0;
					animationTick++;
				}
			} else {
				if (gameTick > 10) {
					gameTick = 0;
					animationTick++;
				}
			}
		} else if (animation.equals(Animation.WALKING_UP)
				|| animation.equals(Animation.WALKING_DOWN)
				|| animation.equals(Animation.WALKING_LEFT)
				|| animation.equals(Animation.WALKING_RIGHT)
				|| animation.equals(Animation.WALKING_IN_PLACE)
				|| animation.equals(Animation.HOLDING_BOMB_WALKING_UP)
				|| animation.equals(Animation.HOLDING_BOMB_WALKING_DOWN)
				|| animation.equals(Animation.HOLDING_BOMB_WALKING_LEFT)
				|| animation.equals(Animation.HOLDING_BOMB_WALKING_RIGHT)) {
			if (animation.equals(Animation.WALKING_UP)
					|| animation.equals(Animation.HOLDING_BOMB_WALKING_UP))
				facing = 1;
			else if (animation.equals(Animation.WALKING_DOWN)
					|| animation.equals(Animation.HOLDING_BOMB_WALKING_DOWN))
				facing = 0;
			else if (animation.equals(Animation.WALKING_LEFT)
					|| animation.equals(Animation.HOLDING_BOMB_WALKING_LEFT))
				facing = 2;
			else if (animation.equals(Animation.WALKING_RIGHT)
					|| animation.equals(Animation.HOLDING_BOMB_WALKING_RIGHT))
				facing = 3;
			int num = 11 - ourPlayer.speed;
			if (num < 4)
				num = 4;
			if (gameTick % num == 0)
				animationTick++;
			if (gameTick > num) {
				gameTick = 0;
			}
		} else if (animation.equals(Animation.BOMBER_SELECT_UP)) {
			if (gameTick > 60) {
				gameTick = 0;
				animation = Animation.BOMBER_SELECT_IDLE;
			}
		} else if (animation.equals(Animation.BOMBER_SELECT_DOWN)) {
			if (gameTick > 60) {
				gameTick = 0;
				animation = Animation.IDLE;
				characterIndex = -1;
			}
		} else if (animation.equals(Animation.BOMBER_SELECT_WALKING)) {
			if (gameTick % 5 == 0)
				animationTick++;
			if (gameTick > 70) {
				gameTick = 0;
				animation = Animation.IDLE;
			}
		} else if (animation.equals(Animation.HOLDING_BOMB_PICKING_UP)
				|| animation.equals(Animation.HOLDING_BOMB_THROWING)) {
			if (gameTick % 7 == 0)
				animationTick++;
			if (gameTick > 70) {
				gameTick = 0;
			}
		} else if (animation.equals(Animation.STUNNED_SPINNING)) {
			if (gameTick % 7 == 0)
				animationTick++;
			if (gameTick > 70) {
				gameTick = 0;
			}
		} else if (animation.equals(Animation.DYING)) {
			if (gameTick % 5 == 0)
				animationTick++;
			if (gameTick > 70) {
				gameTick = 0;
			}
		} else if (animation.equals(Animation.WINNING)) {
			if (gameTick % 7 == 0 && animationTick != -1)
				animationTick++;
			if (gameTick > 70) {
				gameTick = 0;
			}
		} else if (animation.equals(Animation.IDLE)) {
			gameTick = 0;
			animationTick = 0;
		} else if (gameTick > 10) {
			gameTick = 0;
			animationTick++;
		}
	}

	public void draw(Graphics2D g, Player ourPlayer, MainBomberman game) {
		draw(g, (int) ourPlayer.getExactPoint().x, (int) ourPlayer.getExactPoint().y,
				ourPlayer, game);
	}

	public void draw(Graphics2D g, int x, int y, MainBomberman game) {
		draw(g, x, y, null, game);
	}

	public void draw(Graphics2D g, int x, int y, Player ourPlayer, MainBomberman game) {
		BufferedImage imageToDraw = null;
		try {
			if (ourPlayer.getExactPoint().x == -10
					|| ourPlayer.getExactPoint().y == -10) {
				// Player is dead and discarded, don't draw
				return;
			}
		} catch (Exception e) {
			// can't get exact point, not in game
		}
		boolean skull = false;
		if (ourPlayer != null && ourPlayer.skullTimer > System.currentTimeMillis()
				&& ourPlayer.deadTimer == -1) {
			double count = game.tickcounter;
			if (ourPlayer.skullTimer - System.currentTimeMillis() > 3000) {
				if ((count > 10 && count <= 20) || (count > 30 && count <= 40)
						|| (count > 50 && count <= 60) || (count > 70 && count <= 80)
						|| (count > 90 && count <= 100))
					skull = true; // More than 3 sec left in skull
			} else if (ourPlayer.skullTimer - System.currentTimeMillis() <= 3000)
				if ((count > 25 && count <= 50) || (count > 75 && count <= 100))
					skull = true; // Skull almost done
		}
		if (animation.equals(Animation.WALKING_IN_PLACE)) {
			if (facing == 0)
				animation = Animation.WALKING_DOWN;
			else if (facing == 1)
				animation = Animation.WALKING_UP;
			else if (facing == 2)
				animation = Animation.WALKING_LEFT;
			else if (facing == 3)
				animation = Animation.WALKING_RIGHT;
		}
		if (animation.equals(Animation.IDLE)) {
			if (characterIndex == Constants.CHARACTERS.MANTO)
				x -= 8;
			int[] facingPics = { 8, 2, 5, 11 };
			if (facing > facingPics.length - 1)
				facing = 0;
			imageToDraw = pictures.characters.get(characterIndex).get(1)
					.get(facingPics[facing]);
			if (skull)
				imageToDraw = Pictures.darken(imageToDraw);
			g.drawImage(imageToDraw, x, y, null);
		} else if (animation.equals(Animation.HOLDING_BOMB_IDLE)) {
			if (characterIndex == Constants.CHARACTERS.MANTO)
				x -= 8;
			int[] facingPics = { 6, 0, 3, 9 };
			if (facing > facingPics.length - 1)
				facing = 0;
			if (facing == 1)
				ourPlayer.pickedUpBomb.draw(g, game);
			imageToDraw = pictures.characters.get(characterIndex).get(4)
					.get(facingPics[facing]);
			if (skull)
				imageToDraw = Pictures.darken(imageToDraw);
			g.drawImage(imageToDraw, x, y, null);
			if (facing != 1)
				ourPlayer.pickedUpBomb.draw(g, game);
		} else if (animation.equals(Animation.IDLE_ANIMATION)) {
			if (animationTick >= pictures.characters.get(characterIndex).get(11).size()
					|| animationTick < 0)
				animationTick = 0;
			if (characterIndex == Constants.CHARACTERS.MANTO)
				x -= 8;
			try {
				imageToDraw = pictures.characters.get(characterIndex).get(11)
						.get(animationTick);
				if (skull)
					imageToDraw = Pictures.darken(imageToDraw);
				g.drawImage(imageToDraw, x, y, null);
			} catch (Exception ignored) {
				// Sometimes animationTick won't go to zero for some reason?
				imageToDraw = pictures.characters.get(characterIndex).get(11).get(0);
				if (skull)
					imageToDraw = Pictures.darken(imageToDraw);
				g.drawImage(imageToDraw, x, y, null);
			}
		} else if (animation.equals(Animation.BOMBER_SELECT_UP)) {
			g.drawImage(pictures.selectionBomberTiles.get(0).get(0), x - 9,
					y - gameTick * 2 + 43, null);
			if (characterIndex == Constants.CHARACTERS.MANTO)
				x -= 8;
			imageToDraw = pictures.characters.get(characterIndex).get(1).get(8);
			g.drawImage(imageToDraw, x, y - gameTick * 2, null);
		} else if (animation.equals(Animation.BOMBER_SELECT_DOWN)) {
			g.drawImage(pictures.selectionBomberTiles.get(0).get(0), x - 9,
					y + gameTick * 2 + 43 - 120, null);
			if (characterIndex == Constants.CHARACTERS.MANTO)
				x -= 8;
			imageToDraw = pictures.characters.get(characterIndex).get(1).get(8);
			g.drawImage(imageToDraw, x, y + gameTick * 2 - 120, null);
		} else if (animation.equals(Animation.BOMBER_SELECT_IDLE)) {
			try {
				if (animationTick >= pictures.characters.get(characterIndex).get(11)
						.size() - 1)
					animationTick = 0;
				g.drawImage(pictures.selectionBomberTiles.get(0).get(0), x - 9,
						y - 120 + 43, null);
				if (characterIndex == Constants.CHARACTERS.MANTO)
					x -= 8;
				g.drawImage(pictures.characters.get(characterIndex).get(11)
						.get(animationTick), x, y - 120, null);
			} catch (Exception e) {
				// Sometimes messes up? dunno why
			}
		} else if (animation.equals(Animation.BOMBER_SELECT_WALKING)) {
			if (characterIndex == Constants.CHARACTERS.MANTO)
				x -= 8;
			int[] tickProgression = { 2, 0, 2, 1 };
			if (animationTick > tickProgression.length - 1)
				animationTick = 0;
			int thisTick = tickProgression[animationTick];
			g.drawImage(pictures.selectionBomberTiles.get(0).get(0), x - 9, y - 120 + 43,
					null);

			BufferedImage orig = pictures.characters.get(characterIndex).get(1)
					.get(thisTick);
			BufferedImage resized = Resizer.resize(orig,
					(int) (orig.getWidth() * (1d - ((double) gameTick / 80d))),
					(int) (orig.getHeight() * (1d - ((double) gameTick / 80d))));
			y -= 120;
			g.drawImage(resized, (int) (x + ((380 - x) * ((double) gameTick / 70d))),
					(int) (y + ((120 - y) * ((double) gameTick / 70d))), null);
		} else if (animation.equals(Animation.WALKING_UP)
				|| animation.equals(Animation.WALKING_DOWN)
				|| animation.equals(Animation.WALKING_LEFT)
				|| animation.equals(Animation.WALKING_RIGHT)) {
			if (characterIndex == Constants.CHARACTERS.MANTO)
				x -= 8;
			int[] tickProgression = null;
			if (animation.equals(Animation.WALKING_UP))
				tickProgression = new int[] { 0, 2, 1, 2 };
			else if (animation.equals(Animation.WALKING_DOWN))
				tickProgression = new int[] { 6, 8, 7, 8 };
			else if (animation.equals(Animation.WALKING_LEFT))
				tickProgression = new int[] { 3, 5, 4, 5 };
			else if (animation.equals(Animation.WALKING_RIGHT))
				tickProgression = new int[] { 9, 11, 10, 11 };
			if (animationTick > tickProgression.length - 1)
				animationTick = 0;
			int thisTick = 0;
			try {
				// this errors out when you kick bomb near the end of a match
				thisTick = tickProgression[animationTick];
			} catch (Exception e) {
				// hopefully doesn't occur otherwise.. just draw idle
				animation = Animation.IDLE;
				draw(g, x, y, ourPlayer, game);
				return;
			}
			imageToDraw = pictures.characters.get(characterIndex).get(1).get(thisTick);
			if (skull)
				imageToDraw = Pictures.darken(imageToDraw);
			g.drawImage(imageToDraw, x, y, null);
		} else if (animation.equals(Animation.HOLDING_BOMB_PICKING_UP)) {
			if (characterIndex == Constants.CHARACTERS.MANTO)
				x -= 8;
			int[] tickProgression = { -1, -1, -1 };
			if (facing == 0)
				tickProgression = new int[] { 8, 7, 6 };
			else if (facing == 1)
				tickProgression = new int[] { 2, 1, 0 };
			else if (facing == 2)
				tickProgression = new int[] { 5, 4, 3 };
			else if (facing == 3)
				tickProgression = new int[] { 11, 10, 9 };
			if (animationTick > tickProgression.length - 1)
				animationTick = tickProgression.length - 1;
			int thisTick = tickProgression[animationTick];
			imageToDraw = pictures.characters.get(characterIndex).get(4).get(thisTick);
			if (skull)
				imageToDraw = Pictures.darken(imageToDraw);
			if (facing != 1)
				g.drawImage(imageToDraw, x, y, null);
			if (ourPlayer.pickedUpBomb != null)
				ourPlayer.pickedUpBomb.draw(g, game);
			if (facing == 1)
				g.drawImage(imageToDraw, x, y, null);
		} else if (animation.equals(Animation.HOLDING_BOMB_THROWING)) {
			if (characterIndex == Constants.CHARACTERS.MANTO)
				x -= 8;
			int[] tickProgression = { -1, -1, -1 };
			if (facing == 0)
				tickProgression = new int[] { 6, 7, 8 };
			else if (facing == 1)
				tickProgression = new int[] { 0, 1, 2 };
			else if (facing == 2)
				tickProgression = new int[] { 3, 4, 5 };
			else if (facing == 3)
				tickProgression = new int[] { 9, 10, 11 };
			if (animationTick > tickProgression.length - 1)
				animationTick = tickProgression.length - 1;
			int thisTick = tickProgression[animationTick];
			imageToDraw = pictures.characters.get(characterIndex).get(4).get(thisTick);
			if (skull)
				imageToDraw = Pictures.darken(imageToDraw);
			if (facing != 1)
				g.drawImage(imageToDraw, x, y, null);
			if (ourPlayer.pickedUpBomb != null)
				ourPlayer.pickedUpBomb.draw(g, game);
			if (facing == 1)
				g.drawImage(imageToDraw, x, y, null);
		} else if (animation.equals(Animation.HOLDING_BOMB_WALKING_UP)
				|| animation.equals(Animation.HOLDING_BOMB_WALKING_DOWN)
				|| animation.equals(Animation.HOLDING_BOMB_WALKING_LEFT)
				|| animation.equals(Animation.HOLDING_BOMB_WALKING_RIGHT)) {
			if (characterIndex == Constants.CHARACTERS.MANTO)
				x -= 8;
			int[] tickProgression = null;
			if (animation.equals(Animation.HOLDING_BOMB_WALKING_UP))
				tickProgression = new int[] { 0, 0, 1, 0 };
			else if (animation.equals(Animation.HOLDING_BOMB_WALKING_DOWN))
				tickProgression = new int[] { 4, 6, 5, 6 };
			else if (animation.equals(Animation.HOLDING_BOMB_WALKING_LEFT))
				tickProgression = new int[] { 2, 3, 3, 3 };
			else if (animation.equals(Animation.HOLDING_BOMB_WALKING_RIGHT))
				tickProgression = new int[] { 6, 9, 7, 9 };
			if (animationTick > tickProgression.length - 1)
				animationTick = 0;
			int thisTick = tickProgression[animationTick];
			if (facing == 1 && ourPlayer.pickedUpBomb != null)
				ourPlayer.pickedUpBomb.draw(g, game);
			imageToDraw = pictures.characters.get(characterIndex).get(4).get(thisTick);
			if (animationTick == 1 || animationTick == 3)
				imageToDraw = pictures.characters.get(characterIndex).get(4)
						.get(thisTick);
			else
				imageToDraw = pictures.characters.get(characterIndex).get(3)
						.get(thisTick);
			if (skull)
				imageToDraw = Pictures.darken(imageToDraw);
			g.drawImage(imageToDraw, x, y, null);
			if (facing != 1 && ourPlayer.pickedUpBomb != null)
				ourPlayer.pickedUpBomb.draw(g, game);
		} else if (animation.equals(Animation.STUNNED_SPINNING)) {
			if (characterIndex == Constants.CHARACTERS.MANTO)
				x -= 8;
			int[] tickProgression = { 3, 0, 1, 2 };
			int thisAnimationTick = animationTick;
			if (animationTick > tickProgression.length - 1)
				if (animationTick - tickProgression.length + 1 > tickProgression.length
						- 1) {
					thisAnimationTick = 0;
				} else
					thisAnimationTick = animationTick - tickProgression.length + 1;
			int thisTick = tickProgression[thisAnimationTick];
			imageToDraw = pictures.characters.get(characterIndex).get(7).get(thisTick);
			if (skull)
				imageToDraw = Pictures.darken(imageToDraw);
			g.drawImage(imageToDraw, x, y, null);
		} else if (animation.equals(Animation.DYING)) {
			if (characterIndex == Constants.CHARACTERS.MANTO)
				x -= 8;
			if (characterIndex == Constants.CHARACTERS.BLACK_BOMBERMAN
					|| characterIndex == Constants.CHARACTERS.WHITE_BOMBERMAN)
				x -= 8; // Bigger death explosion animation than others
			if (animationTick > pictures.characters.get(characterIndex).get(10).size()
					- 1)
				animationTick = -1;
			if (animationTick == -1)
				return;
			imageToDraw = pictures.characters.get(characterIndex).get(10)
					.get(pictures.characters.get(characterIndex).get(10).size() - 1
							- animationTick);
			g.drawImage(imageToDraw, x, y, null);
		} else if (animation.equals(Animation.WINNING)) {
			if (characterIndex == Constants.CHARACTERS.MANTO)
				x -= 8;
			if (characterIndex == Constants.CHARACTERS.BLACK_BOMBERMAN
					|| characterIndex == Constants.CHARACTERS.WHITE_BOMBERMAN)
				x -= 2; // Bigger winning explosion animation than others
			int thisAnimationTick = animationTick;
			if (animationTick > pictures.characters.get(characterIndex).get(12).size()
					- 1)
				animationTick = -1;
			if (animationTick == -1)
				thisAnimationTick = pictures.characters.get(characterIndex).get(12).size()
						- 1;
			imageToDraw = pictures.characters.get(characterIndex).get(12)
					.get(thisAnimationTick);
			if (skull)
				imageToDraw = Pictures.darken(imageToDraw);
			g.drawImage(imageToDraw, x, y, null);
		}
		if (imageToDraw != null)
			imageToDraw.flush();
		imageToDraw = null;
	}

	/**
	 * Sets {@link #facing}.
	 * 
	 * @param dir
	 *            0: down<br>
	 *            1: up<br>
	 *            2: left<br>
	 *            3: right
	 */
	public void setDirection(int dir) {
		facing = dir;
	}
}
