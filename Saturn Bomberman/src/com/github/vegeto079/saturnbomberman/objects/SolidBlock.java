package com.github.vegeto079.saturnbomberman.objects;

import java.awt.Graphics2D;
import java.awt.Point;

import com.github.vegeto079.saturnbomberman.main.MainBomberman;
import com.github.vegeto079.saturnbomberman.main.Stage;

/**
 * 
 * @author Nathan
 * @version 1.0: Started tracking version.
 *
 */
public class SolidBlock extends com.github.vegeto079.saturnbomberman.objects.Object {

	public SolidBlock(Point cellPoint) {
		this.cellPoint = cellPoint;
	}

	public SolidBlock(int cellX, int cellY) {
		cellPoint = new Point(cellX, cellY);
	}

	@Override
	public void tick(MainBomberman game) {
		// Do nothing, this is just a solid block
	}

	@Override
	public void draw(Graphics2D g, MainBomberman game) {
		Point stageOffset = game.stage.getOffset();
		g.drawImage(game.pictures.stageBlocks.get(0).get(0),
				stageOffset.x + cellPoint.y * Stage.SQUARE_WIDTH,
				stageOffset.y + cellPoint.x * Stage.SQUARE_HEIGHT - 2, game);

	}
}
