package com.github.vegeto079.saturnbomberman.misc;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.TexturePaint;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;

import com.github.vegeto079.ngcommontools.main.Game;
import com.github.vegeto079.ngcommontools.main.Tools;
import com.github.vegeto079.saturnbomberman.main.MainBomberman;


/**
 * 
 * @author Nathan
 * @version 1.0: Started tracking version.
 * @version 1.01: Removed getTextWidth() and getTextHeight(), moving them to
 *          {@link Tools}.
 * @version 1.02: Added
 *          {@link #textWithOutline(Graphics2D, String, int, int, Color, Color)}
 *          .
 */
public class Draw {
	public Game game = null;
	public Pictures pictures = null;

	public Draw(MainBomberman game) {
		this.game = game;
		game.pictures = pictures;
	}

	public void setPictures(Pictures pictures) {
		this.pictures = pictures;
	}

	public static void text(Graphics2D g, String text, int x, int y,
			BufferedImage imageToOverlay) {
		Color colorBefore = g.getColor();
		text = text.replace("(", "").replace(")", "");
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		Rectangle r = new Rectangle(0, 0, imageToOverlay.getWidth(),
				imageToOverlay.getHeight());
		TexturePaint tp = new TexturePaint(imageToOverlay, r);
		g.setPaint(tp);
		g.drawString(text, x, y);
		g.setColor(colorBefore);
	}

	public static void text(Graphics2D g, String text, int x, int y,
			BufferedImage imageToOverlay, Color outlineColor, float outlineSize) {
		Paint paintBefore = g.getPaint();
		Color colorBefore = g.getColor();
		text = text.replace("(", "").replace(")", "");
		Rectangle r = new Rectangle(0, 0, imageToOverlay.getWidth(),
				imageToOverlay.getHeight());
		TexturePaint tp = new TexturePaint(imageToOverlay, r);
		g.setPaint(tp);
		g.drawString(text, x, y);
		g.setColor(outlineColor);
		GlyphVector gv = g.getFont().createGlyphVector(g.getFontRenderContext(), text);
		Shape shape = gv.getOutline();
		g.setStroke(new BasicStroke(outlineSize));
		g.translate(x, y);
		g.draw(shape);
		g.setColor(colorBefore);
		g.setPaint(paintBefore);
		g.translate(-x, -y);
	}

	public static void text(Graphics g, String text, int x, int y, Color color) {
		Color colorBefore = g.getColor();
		text = text.replace("(", "").replace(")", "");
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(color);
		g.drawString(text, x, y);
		g.setColor(colorBefore);
	}

	public void textCentered(Graphics2D g, String text, int y,
			BufferedImage imageToOverlay) {
		text = text.replace("(", "").replace(")", "");
		text(g, text, game.getWidth() / 2 - Tools.getTextWidth(g, text) / 2, y,
				imageToOverlay);
	}

	public void textCentered(Graphics2D g, String text, int y,
			BufferedImage imageToOverlay, Color outlineColor, float outlineSize) {
		text = text.replace("(", "").replace(")", "");
		text(g, text, game.getWidth() / 2 - Tools.getTextWidth(g, text) / 2, y,
				imageToOverlay, outlineColor, outlineSize);
	}

	public void textCentered(Graphics g, String text, int y, Color color) {
		text = text.replace("(", "").replace(")", "");
		text(g, text, game.getWidth() / 2 - Tools.getTextWidth(g, text) / 2, y, color);
	}

	public void debug(Graphics g, Font font, String... strings) {
		Font fontBefore = g.getFont();
		int x = game.getWidth() - 8;
		int y = 8;
		boolean moveOver = false;
		Font thisFont = new Font(font.getName(), font.getStyle(), font.getSize());
		g.setFont(thisFont);
		for (int i = 0; i < strings.length; i++) {
			if (y + 21 / 2 + 4 >= game.getHeight() && !moveOver) {
				// y = 8;
				// x = 8;
				// moveOver = true;
			}
			text(g, strings[i], x - (moveOver ? 0 : Tools.getTextWidth(g, strings[i])),
					y += 21 / 2 + 4, Color.GREEN);
		}
		g.setFont(fontBefore);
	}

	public void rectangle(Graphics g, int x1, int y1, int x2, int y2, Color color) {
		Color tempColor = g.getColor();
		g.setColor(color);
		g.fillRect(x1, y1, x2, y2);
		g.setColor(tempColor);
	}

	public void whiteRectangle(Graphics g, int x1, int y1, int x2, int y2) {
		rectangle(g, x1, y1, x2, y2, Color.WHITE);
	}

	public void whiteRectangle(Graphics g, int x1, int y1, int x2, int y2, float alpha) {
		rectangle(g, x1, y1, x2, y2, new Color(1f, 1f, 1f, alpha));
	}

	public void blackRectangle(Graphics g, int x1, int y1, int x2, int y2) {
		rectangle(g, x1, y1, x2, y2, Color.BLACK);
	}

	public void blackRectangle(Graphics g, int x1, int y1, int x2, int y2, float alpha) {
		rectangle(g, x1, y1, x2, y2, new Color(0f, 0f, 0f, alpha));
	}

	public void blackRectangle(Graphics g) {
		blackRectangle(g, -20, -20, 840, 840);
	}

	public void blackRectangle(Graphics g, float alpha) {
		blackRectangle(g, -20, -20, 840, 840, alpha);
	}

	public void textCenteredWithTexturedImage(Graphics2D g, Color whichTexturedImage,
			boolean blink, int y, String text) {
		textCentered(g, text, y, getTextTextureImage(whichTexturedImage, blink),
				blink ? Color.WHITE : new Color(48, 8, 8), 1.25f);
	}

	public void textWithTexturedImage(Graphics2D g, Color whichTexturedImage,
			boolean blink, int x, int y, String text) {
		text(g, text, x, y, getTextTextureImage(whichTexturedImage, blink),
				blink ? Color.WHITE : new Color(48, 8, 8), 1.25f);
	}

	protected static BufferedImage getTextTextureImage(Color color, boolean blink) {
		BufferedImage bi = new BufferedImage(1, 25, BufferedImage.TYPE_INT_ARGB);
		if (color.getRGB() == Color.RED.getRGB()) {
			writeTextTextureImage(bi, new Color(248, 104, 88), new Color(248, 152, 144),
					new Color(248, 200, 192), new Color(248, 248, 248),
					new Color(248, 200, 192), new Color(248, 152, 144),
					new Color(248, 104, 88), new Color(200, 64, 56), blink);
		} else if (color.getRGB() == Color.YELLOW.getRGB()) {
			writeTextTextureImage(bi, new Color(236, 168, 0), new Color(236, 213, 0),
					new Color(236, 236, 46), new Color(236, 236, 236),
					new Color(236, 236, 46), new Color(236, 213, 0),
					new Color(236, 168, 0), new Color(190, 122, 0), blink);
		} else if (color.getRGB() == Color.BLACK.getRGB()) {
			writeTextTextureImage(bi, Color.BLACK, Color.BLACK, Color.BLACK, Color.BLACK,
					Color.BLACK, Color.BLACK, Color.BLACK, Color.BLACK, blink);
		}
		return bi;
	}

	protected static void writeTextTextureImage(BufferedImage bi, Color one, Color two,
			Color three, Color four, Color five, Color six, Color seven, Color eight,
			boolean blink) {
		if (blink) {
			one = Color.WHITE;
			two = Color.WHITE;
			three = Color.WHITE;
			four = Color.WHITE;
			five = Color.WHITE;
			six = Color.WHITE;
			seven = Color.WHITE;
			eight = Color.WHITE;
		}
		Graphics2D g2 = bi.createGraphics();
		Paint paint = g2.getPaint();
		g2.setPaint(one);
		g2.fillRect(0, 0, bi.getWidth(), 2);
		g2.setPaint(two);
		g2.fillRect(0, 2, bi.getWidth(), 5);
		g2.setPaint(three);
		g2.fillRect(0, 5, bi.getWidth(), 7);
		g2.setPaint(four);
		g2.fillRect(0, 7, bi.getWidth(), 12);
		g2.setPaint(five);
		g2.fillRect(0, 12, bi.getWidth(), 15);
		g2.setPaint(six);
		g2.fillRect(0, 15, bi.getWidth(), 17);
		g2.setPaint(seven);
		g2.fillRect(0, 17, bi.getWidth(), 22);
		g2.setPaint(eight);
		g2.fillRect(0, 22, bi.getWidth(), 25);
		g2.setPaint(paint);
		g2.dispose();
	}

	public void dinoSelector(Graphics2D g, Pictures pictures, int selectorAnimationIndex,
			int width, int height) {
		g.drawImage(pictures.dinoSelector.get(0).get(selectorAnimationIndex), width,
				height, game);
	}

	public void textWithOutline(Graphics2D g, String text, int x, int y,
			Color insideColor, Color outlineColor) {
		GlyphVector gv = g.getFont().createGlyphVector(g.getFontRenderContext(), text);
		Shape shape = gv.getOutline();
		g.setStroke(new BasicStroke(1.0f));
		g.setColor(insideColor);
		g.drawString(text, x, y); // draw regular
		g.translate(x, y); // move everything over
		g.setColor(outlineColor);
		g.draw(shape); // draw outline
		g.translate(-x, -y); // move everything back
	}
}
