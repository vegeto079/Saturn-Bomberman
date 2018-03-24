package com.github.vegeto079.saturnbomberman.objects;

import java.awt.Graphics2D;
import java.awt.Point;

import com.github.vegeto079.ngcommontools.main.DoublePoint;
import com.github.vegeto079.saturnbomberman.main.MainBomberman;

public abstract class Object {
	public boolean finished = false;
	public Point cellPoint = new Point(-1, -1);
	public DoublePoint exactPoint = new DoublePoint(-1, -1);

	public abstract void tick(MainBomberman game);

	public abstract void draw(Graphics2D g, MainBomberman game);
}
