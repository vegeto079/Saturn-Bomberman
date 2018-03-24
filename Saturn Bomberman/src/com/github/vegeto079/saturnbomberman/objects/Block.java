package com.github.vegeto079.saturnbomberman.objects;

import java.awt.Graphics2D;
import java.awt.Point;

import com.github.vegeto079.ngcommontools.main.DoublePoint;
import com.github.vegeto079.saturnbomberman.main.MainBomberman;
import com.github.vegeto079.saturnbomberman.main.Stage;


/**
 * 
 * @author Nathan
 * @version 1.0: Started tracking version.
 *
 */
public class Block extends com.github.vegeto079.saturnbomberman.objects.Object {
	final long DEFAULT_EXPLODE_TIME = 500;
	final long ITEM_GRACE_PERIOD = 250;

	private MainBomberman game = null;

	long explodeTime = -1;
	boolean exploding = false;
	boolean waitForNetwork = false;
	boolean solid = true;
	boolean itemCreated = false;

	public Block(Point cellPoint) {
		this.cellPoint = cellPoint;
	}

	public Block(int cellX, int cellY) {
		cellPoint = new Point(cellX, cellY);
	}

	@Override
	public void tick(MainBomberman game) {
		if (this.game == null)
			this.game = game;
		if (exploding && waitForNetwork
				&& game.currentTimeMillis() - 2000 >= explodeTime) {
			exploding = false;
			waitForNetwork = false;
			explodeTime = -1;
		}
		if (exploding && !waitForNetwork)
			if (game.currentTimeMillis() >= explodeTime)
				if (!itemCreated) {
					for (int z = 0; z < game.objects.size(); z++)
						if (game.objects.get(z).cellPoint
								.distance(new Point(cellPoint.y, cellPoint.x)) == 0)
							if (game.objects.get(z) instanceof Item) {
								Item item = (Item) game.objects.get(z);
								item.visible = true;
								break;
							}
					itemCreated = true;
				} else if (game.currentTimeMillis() >= explodeTime + ITEM_GRACE_PERIOD) {
					solid = false;
					setStagePoint(-1);
				}
	}

	@Override
	public void draw(Graphics2D g, MainBomberman game) {
		if (!solid)
			return;
		Point exactPoint = getExactPoint(game).toPoint();
		try {
			int idx = -1;
			if (!exploding)
				idx = 1;
			else if (game.currentTimeMillis() > explodeTime)
				return;
			else {
				idx = (int) ((1d
						- ((double) explodeTime - (double) game.currentTimeMillis())
								/ (double) (DEFAULT_EXPLODE_TIME))
						* 3d + 2d);
				// System.out.println("idx: " + idx + " (" + ((1d
				// - ((double) explodeTime - (double) game.currentTimeMillis())
				// / (double) (DEFAULT_EXPLODE_TIME))
				// * 3d) + ")");
			}
			g.drawImage(game.pictures.stageBlocks.get(0).get(idx), exactPoint.x,
					exactPoint.y, game);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public DoublePoint getExactPoint(MainBomberman game) {
		Point stageOffset = game.stage.getOffset();
		DoublePoint exactPoint = new DoublePoint(
				stageOffset.x + cellPoint.y * Stage.SQUARE_WIDTH,
				stageOffset.y + cellPoint.x * Stage.SQUARE_HEIGHT - 2);
		this.exactPoint = exactPoint;
		return exactPoint;
	}

	public void destroyAt(long time) {
		// From network
		explodeTime = time;
		exploding = true;
		waitForNetwork = false;
	}

	public void explode() {
		if (!exploding) {
			exploding = true;
			explodeTime = game.currentTimeMillis() + DEFAULT_EXPLODE_TIME;
			if (game.network.isPlayingOnline() && game.network.weAreHost())
				game.network.talking.blockDestroy(cellPoint, explodeTime);
			else if (game.network.isPlayingOnline() && !game.network.weAreHost())
				waitForNetwork = true;
		}
	}

	public void setStagePoint(int set) {
		game.stage.get()[cellPoint.x][cellPoint.y] = set;
	}

	public static Block get(int cellX, int cellY, MainBomberman game) {
		return get(new Point(cellX, cellY), game);
	}

	public static Block get(Point cellPoint, MainBomberman game) {
		int tryCount = 10;
		do
			try {
				tryCount--;
				for (int i = 0; i < game.objects.size(); i++) {
					com.github.vegeto079.saturnbomberman.objects.Object obj = game.objects.get(i);
					if (obj instanceof Block && obj.cellPoint.distance(cellPoint) == 0)
						return (Block) obj;
				}
			} catch (Exception e) {
			}
		while (tryCount > 0);
		return null;
	}
}
