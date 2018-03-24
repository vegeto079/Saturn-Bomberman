package com.github.vegeto079.saturnbomberman.misc;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Transparency;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.PixelGrabber;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

import com.github.vegeto079.ngcommontools.main.Game;
import com.github.vegeto079.ngcommontools.main.Logger.LogLevel;
import com.github.vegeto079.ngcommontools.main.Resizer;
import com.github.vegeto079.ngcommontools.main.ResourceHandler;
import com.github.vegeto079.ngcommontools.main.Tools;

/**
 * 
 * @author Nathan
 * @version 1.0: Started tracking version.
 * @version 1.01: Changed darken(BufferedImage, float) to
 *          {@link #changeBrightness(BufferedImage, float)}, as it can both
 *          brighten and darken. Mins and maxes added for to be failsafe.
 *          {@link #darken(BufferedImage)} still remains as a shortcut, and
 *          {@link #brighten(BufferedImage)} has been added for the same reason.
 * @version 1.02: Made file-getting linux-safe.
 * @version 1.03: Added {@link #availableStageAmt} and
 *          {@link #getAvailableStageAmt()} to be ran in {@link #init()}.
 * @version 1.04: If we are running from a jar and extracted images, we now
 *          delete the majority of them after they are in memory. Added
 *          {@link #deleteEmptyResFolders(File...)}, to be ran at the end of
 *          {@link #init()} to delete empty leftover folders of images.
 */
public class Pictures {
	public Game game = null;
	public boolean initialized = false;
	public boolean startedInit = false;
	public ArrayList<ArrayList<ArrayList<BufferedImage>>> characters = new ArrayList<ArrayList<ArrayList<BufferedImage>>>();
	public ArrayList<BufferedImage> mainMenu = new ArrayList<BufferedImage>();
	public ArrayList<ArrayList<BufferedImage>> dinoSelector = new ArrayList<ArrayList<BufferedImage>>();
	public BufferedImage optionsMenu = null;
	public BufferedImage selectionBackground = null;
	public BufferedImage selectionBackground2 = null;
	public BufferedImage selectionBombBackground = null;
	public BufferedImage selectionBombBackground2 = null;
	public ArrayList<ArrayList<BufferedImage>> selectionButtons = null;
	public ArrayList<ArrayList<BufferedImage>> selectionSelector = null;
	public ArrayList<ArrayList<BufferedImage>> selectionButtonSelector = null;
	public BufferedImage selectionBomberBackground = null;
	public BufferedImage selectionBomberBackground2 = null;
	public ArrayList<ArrayList<BufferedImage>> selectionBomberTiles = null;
	public BufferedImage selectionCircusBackground = null;
	public BufferedImage selectionDomeBackground = null;
	public ArrayList<BufferedImage> stagesSelections = new ArrayList<BufferedImage>();
	public ArrayList<BufferedImage> stagesDescription = new ArrayList<BufferedImage>();
	public BufferedImage stageBackground = null;
	public BufferedImage stageBackgroundOverlap = null;
	public BufferedImage stageTimerOverlap = null;
	public ArrayList<ArrayList<BufferedImage>> stageBlocks = new ArrayList<ArrayList<BufferedImage>>();
	public ArrayList<ArrayList<BufferedImage>> bombs = new ArrayList<ArrayList<BufferedImage>>();
	public ArrayList<ArrayList<BufferedImage>> bombFire = new ArrayList<ArrayList<BufferedImage>>();
	public ArrayList<ArrayList<BufferedImage>> items = new ArrayList<ArrayList<BufferedImage>>();
	public ArrayList<ArrayList<BufferedImage>> itemExplosion = new ArrayList<ArrayList<BufferedImage>>();
	public BufferedImage shadow = null;
	public BufferedImage weight = null;
	public ArrayList<ArrayList<BufferedImage>> textNumbers = new ArrayList<ArrayList<BufferedImage>>();
	public ArrayList<ArrayList<BufferedImage>> textHurry = new ArrayList<ArrayList<BufferedImage>>();
	public ArrayList<ArrayList<BufferedImage>> stageReadyGo = new ArrayList<ArrayList<BufferedImage>>();
	public BufferedImage xbox360controller = null;
	public ArrayList<ArrayList<BufferedImage>> endStatsButtons = new ArrayList<ArrayList<BufferedImage>>();
	public BufferedImage endStatsBg = null;
	public BufferedImage endStatsOverall = null;
	public BufferedImage endStatsSuicide = null;
	public ArrayList<ArrayList<BufferedImage>> networkingCursor = new ArrayList<ArrayList<BufferedImage>>();
	public ArrayList<ArrayList<BufferedImage>> networkingBackground = new ArrayList<ArrayList<BufferedImage>>();
	public ArrayList<ArrayList<BufferedImage>> networkingPopup = new ArrayList<ArrayList<BufferedImage>>();

	public int availableStageAmt = 0;

	private boolean debug = false;

	public class PicturesThread extends Thread {
		public void run() {
			init();
		}
	}

	public Pictures(Game game) {
		this.game = game;
		mainMenu.add(f("mainMenu/", "Main Menu Background"));
		mainMenu.add(normalImage("mainMenu/", "Main Menu Dancing 1"));
		mainMenu.add(normalImage("mainMenu/", "Main Menu Dancing 2"));
		mainMenu.add(normalImage("mainMenu/", "Main Menu Dancing 3"));
		mainMenu.add(normalImage("mainMenu/", "Main Menu Dancing 4"));
		mainMenu.add(normalImage("mainMenu/", "Main Menu Dancing 5"));
		mainMenu.add(normalImage("mainMenu/", "Main Menu Logo"));
		System.gc();
		game.logger.log(LogLevel.DEBUG, "Pictures initial loading complete.");
	}

	public void getAvailableStageAmt() {
		String mainPath = ResourceHandler.get(game.logger, "pictures", Tools.getJarName());
		if (System.getProperty("os.name").contains("Linux")) {
			if (debug)
				game.logger.log(LogLevel.DEBUG, "On linux, adjusting path accordingly.");
			mainPath = "/" + mainPath;
		}
		if (debug)
			game.logger.log(LogLevel.DEBUG, "Got main path: " + mainPath);
		while (true) {
			File foundFile = findFileOrFolder_OLD(new File(mainPath),
					"battle stage background " + (availableStageAmt + 1));
			if (foundFile == null)
				break;
			else {
				availableStageAmt++;
				foundFile = null;
			}
		}
		if (debug)
			game.logger.log(LogLevel.DEBUG, "Found available stages: " + availableStageAmt);
	}

	public void init() {
		// dinoSelector = spriteSheet(f("dino selector"));
		// System.exit(1);
		startedInit = true;
		getAvailableStageAmt();
		characters.add(doubleResize(spriteSheet(f("characters/", "Character White Bomberman"))));
		characters.add(doubleResize(spriteSheet(f("characters/", "Character Black Bomberman"))));
		characters.add(doubleResize(spriteSheet(f("characters/", "Character Honey"))));
		characters.add(doubleResize(spriteSheet(f("characters/", "Character Kotetsu"))));
		characters.add(doubleResize(spriteSheet(f("characters/", "Character Milon"))));
		characters.add(doubleResize(spriteSheet(f("characters/", "Character Master Higgins"))));
		characters.add(doubleResize(spriteSheet(f("characters/", "Character Bonks"))));
		characters.add(doubleResize(spriteSheet(f("characters/", "Character Manjimaru"))));
		characters.add(doubleResize(spriteSheet(f("characters/", "Character Kabuki"))));
		characters.add(doubleResize(spriteSheet(f("characters/", "Character Kinu"))));
		characters.add(doubleResize(spriteSheet(f("characters/", "Character Manto"))));
		characters.add(doubleResize(spriteSheet(f("characters/", "Character Yuna"))));

		dinoSelector = spriteSheet(f("", "dino selector"));
		xbox360controller = normalImage("", "xbox 360 controller");
		optionsMenu = f("", "Options Background");
		game.logger.log(LogLevel.DEBUG, "Set up optionsMenu RGB array.");

		selectionBackground = normalImage("matchSelection/", "match selection background");
		selectionBackground2 = normalImage("matchSelection/", "match selection background 2");
		selectionCircusBackground = normalImage("matchSelection/", "match selection circus background");
		selectionDomeBackground = normalImage("matchSelection/", "match selection dome background");
		selectionDomeBackground = makeColorTransparent(selectionDomeBackground, selectionDomeBackground
				.getRGB(selectionDomeBackground.getWidth() - 3, selectionDomeBackground.getHeight() - 3));
		BufferedImage bi = null;
		for (int i = 1; i < 9; i++) {
			bi = normalImage("matchSelection/stages/", "stage selection " + i);
			bi = makeColorTransparent(bi, bi.getRGB(bi.getWidth() - 5, bi.getHeight() - 5));
			stagesSelections.add(bi);
			bi.flush();
			bi = null;
		}
		for (int i = 1; i < 9; i++) {
			bi = normalImage("matchSelection/stages/", "stage selection desc " + i);
			bi = makeColorTransparent(bi, bi.getRGB(bi.getWidth() - 5, bi.getHeight() - 5));
			stagesDescription.add(bi);
			bi.flush();
			bi = null;
		}
		selectionBombBackground = normalImage("matchSelection/", "match selection bomb background");
		selectionBombBackground2 = normalImage("matchSelection/", "match selection bomb background 2");
		selectionButtons = spriteSheet(f("matchSelection/", "match selection buttons"));
		selectionSelector = spriteSheet(f("matchSelection/", "match selection selector"));
		selectionButtonSelector = spriteSheet(f("matchSelection/", "match selection button selector"));
		selectionBomberBackground = f("matchSelection/", "match selection bomber background");
		selectionBomberBackground2 = normalImage("matchSelection/", "match selection bomber background 2");
		selectionBomberTiles = spriteSheet(f("matchSelection/", "match selection bomber tiles"));

		bombs = doubleResize(spriteSheet(f("objects/", "objects bombs")));
		bombFire = doubleResize(spriteSheet(f("objects/", "objects bomb fire")));
		items = doubleResize(spriteSheet(f("objects/", "objects items")));
		shadow = resize(normalImage("objects/", "objects shadow"), 2, 2);
		weight = normalImage("objects/", "objects weight");
		itemExplosion = doubleResize(spriteSheet(f("objects/", "item explosion")));

		textNumbers = spriteSheet(f("text/", "text numbers"));
		textHurry = spriteSheet(f("text/", "text hurry"));

		stageReadyGo = spriteSheet(f("battleStages/", "battle stage ready go"));
		stageTimerOverlap = f("battleStages/", "battle stage timer overlap");

		endStatsButtons = spriteSheet(f("endStats/", "end stats buttons"));
		endStatsBg = normalImage("endStats/", "end stats background");
		endStatsOverall = normalImage("endStats/", "end stats overall");
		endStatsSuicide = resize(f("endStats/", "end stats suicide"), 2, 2);

		networkingCursor = spriteSheet(f("networking/", "networking cursor"));
		networkingBackground = spriteSheet(f("networking/", "networking background"));
		networkingPopup = spriteSheet(f("networking/", "networking popup"));
		if (ResourceHandler.runningFromJar() && !ResourceHandler.resAlreadyExists())
			ResourceHandler.deleteEmptyResFolders(game.logger);
		System.gc();
		initialized = true;
		game.logger.log(LogLevel.DEBUG, "Pictures class fully initalized.");
	}

	public void loadStageToMemory(int whichStage) {
		stageBackground = normalImage("battleStages/", "battle stage background " + (whichStage + 1));
		stageBackground = makeColorTransparent(stageBackground,
				stageBackground.getRGB(stageBackground.getWidth() - 10, stageBackground.getHeight() - 10));

		stageBackgroundOverlap = normalImage("battleStages/", "battle stage background overlap " + (whichStage + 1));
		stageBackgroundOverlap = makeColorTransparent(stageBackgroundOverlap, stageBackgroundOverlap
				.getRGB(stageBackgroundOverlap.getWidth() - 250, stageBackgroundOverlap.getHeight() - 250));
		stageBackgroundOverlap = cropTransparentPixels(stageBackgroundOverlap, stageBackgroundOverlap
				.getRGB(stageBackgroundOverlap.getWidth() - 250, stageBackgroundOverlap.getHeight() - 250));

		stageBlocks = spriteSheet(f("battleStages/", "battle stage blocks " + (whichStage + 1)));
		game.logger.log(LogLevel.DEBUG, "Loaded stage into pictures memory: " + whichStage);
	}

	public boolean stageIsInMemory() {
		return stageBackground != null;
	}

	public void unloadStageFromMemory() {
		stageBackground.flush();
		stageBackground = null;
		stageBackgroundOverlap.flush();
		stageBackgroundOverlap = null;
		for (int i = 0; i < stageBlocks.size(); i++) {
			for (int j = 0; j < stageBlocks.get(i).size(); j++) {
				if (stageBlocks.get(i).get(j) != null)
					stageBlocks.get(i).get(j).flush();
				stageBlocks.get(i).set(j, null);
			}
			stageBlocks.set(i, null);
		}
		stageBlocks = null;
		game.logger.log(LogLevel.DEBUG, "Unloaded stage from memory.");
	}

	public static BufferedImage darken(BufferedImage image) {
		return changeBrightness(image, .5f);
	}

	public static BufferedImage brighten(BufferedImage image) {
		return changeBrightness(image, 1.5f);
	}

	/**
	 * Changes the RGB (but not alpha) values of a {@link BufferedImage} by
	 * <b>opacity</b>. Image will appear darker if <b>opacity</b> is below 1, and
	 * brighter if above 1.
	 * 
	 * @param image
	 *            The original image to change brightness of.
	 * @param opacity
	 *            Level to change brightness by. <i>1.5</i> represents a <i>50%</i>
	 *            increase in colors (and therefore brightness).
	 * @return The updated {@link BufferedImage}.
	 */
	public static BufferedImage changeBrightness(BufferedImage image, float opacity) {
		BufferedImage newImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		int width = newImage.getWidth(), height = newImage.getHeight();
		WritableRaster raster = image.getRaster();
		int[] pixels = null;
		raster.getPixels(0, 0, width, height, pixels);
		for (int i = 0; i < newImage.getWidth(); i++)
			for (int j = 0; j < newImage.getHeight(); j++) {
				pixels = raster.getPixel((int) i, j, pixels);
				Color color = new Color(pixels[0], pixels[1], pixels[2], pixels[3]);
				// Color color = new Color(image.getRGB(i, j));
				if (color.getRed() < 5 && color.getGreen() < 5 && color.getBlue() < 5) {
					newImage.setRGB(i, j, new Color(0, 0, 0, 0).getRGB());
				} else {
					Color newColor = new Color((int) (Math.min(255, Math.max(0, color.getRed() * opacity))),
							(int) (Math.min(255, Math.max(0, color.getGreen() * opacity))),
							(int) (Math.min(255, Math.max(0, color.getBlue() * opacity))), color.getAlpha());
					newImage.setRGB(i, j, newColor.getRGB());
				}
			}
		return newImage;
	}

	public static BufferedImage changeOpacity(BufferedImage image, float opacityChangePercent) {
		BufferedImage newImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int i = 0; i < newImage.getWidth(); i++)
			for (int j = 0; j < newImage.getHeight(); j++) {
				Color color = new Color(image.getRGB(i, j));
				if (color.getRed() < 5 && color.getGreen() < 5 && color.getBlue() < 5) {
					newImage.setRGB(i, j, new Color(0, 0, 0, 0).getRGB());
				} else {
					Color newColor = new Color(color.getRed(), color.getGreen(), color.getBlue(),
							(int) (Math.max(0, Math.min(255, color.getAlpha() * opacityChangePercent))));
					newImage.setRGB(i, j, newColor.getRGB());
				}
			}
		return newImage;
	}

	public static BufferedImage removeRectangle(BufferedImage image, Rectangle rect) {
		BufferedImage newImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int i = 0; i < newImage.getWidth(); i++)
			for (int j = 0; j < newImage.getHeight(); j++)
				if (i < rect.x || i >= rect.x + rect.width || j < rect.y || j >= rect.y + rect.height)
					newImage.setRGB(i, j, image.getRGB(i, j));
		return newImage;
	}

	protected BufferedImage normalImage(String directory, String fileName) {
		BufferedImage image = f(directory, fileName);
		image = makeColorTransparent(image, image.getRGB(image.getWidth() - 1, 0));
		return image;
	}

	protected ArrayList<ArrayList<BufferedImage>> doubleResize(ArrayList<ArrayList<BufferedImage>> listOfList) {
		return resize(listOfList, 2, 2);
	}

	protected ArrayList<ArrayList<BufferedImage>> resize(ArrayList<ArrayList<BufferedImage>> listOfList,
			double scaleWidth, double scaleHeight) {
		ArrayList<ArrayList<BufferedImage>> newListOfList = new ArrayList<ArrayList<BufferedImage>>();
		for (ArrayList<BufferedImage> imageList : listOfList) {
			ArrayList<BufferedImage> newList = new ArrayList<BufferedImage>();
			for (int i = 0; i < imageList.size(); i++) {
				BufferedImage image = imageList.get(i);
				image = resize(image, scaleWidth, scaleHeight);
				newList.add(image);
				image.flush();
				image = null;
				imageList.get(i).flush();
				imageList.set(i, null);
			}
			newListOfList.add(newList);
		}
		listOfList.clear();
		listOfList = null;
		return newListOfList;
	}

	public static BufferedImage resize(BufferedImage image, double scaleWidth, double scaleHeight) {
		return Resizer.resize(image, (int) (image.getWidth() * scaleWidth), (int) (image.getHeight() * scaleHeight));
	}

	protected BufferedImage cropTransparentPixels(BufferedImage image, int transparent) {
		BufferedImage newImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		int frontRowWithPixels = -1, frontColumnWithPixels = -1, backRowWithPixels = -1, backColumnWithPixels = -1;
		for (int i = 0; i < image.getHeight() && frontRowWithPixels == -1; i++)
			if (!arrayListIntegerAllEquals(getPixelRow(image, i), transparent))
				frontRowWithPixels = i;
		for (int i = 0; i < image.getWidth() && frontColumnWithPixels == -1; i++)
			if (!arrayListIntegerAllEquals(getPixelColumn(image, i), transparent))
				frontColumnWithPixels = i;
		for (int i = image.getHeight() - 1; i >= 0 && backRowWithPixels == -1; i--)
			if (!arrayListIntegerAllEquals(getPixelRow(image, i), transparent))
				backRowWithPixels = i;
		for (int i = image.getWidth() - 1; i >= 0 && backColumnWithPixels == -1; i--)
			if (!arrayListIntegerAllEquals(getPixelColumn(image, i), transparent))
				backColumnWithPixels = i;
		if (frontRowWithPixels == -1)
			game.logger.log(LogLevel.WARNING, "frontRowWithPixels==-1!");
		else if (frontColumnWithPixels == -1)
			game.logger.log(LogLevel.WARNING, "frontColumnWithPixels==-1!");
		else if (backRowWithPixels == -1)
			game.logger.log(LogLevel.WARNING, "backRowWithPixels==-1!");
		else if (backColumnWithPixels == -1)
			game.logger.log(LogLevel.WARNING, "backColumnWithPixels==-1!");
		else {
			try {
				newImage = new BufferedImage(backColumnWithPixels - frontColumnWithPixels + 1,
						backRowWithPixels - frontRowWithPixels + 1, BufferedImage.TYPE_INT_ARGB);
			} catch (Exception e) {
				return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
			}
			for (int i = 0; i < newImage.getWidth(); i++)
				for (int j = 0; j < newImage.getHeight(); j++)
					newImage.setRGB(i, j, image.getRGB(i + frontColumnWithPixels, j + frontRowWithPixels));
		}
		return newImage;
	}

	private boolean arrayListIntegerAllEquals(ArrayList<Integer> list, int integer) {
		for (int intToTest : list)
			if (intToTest != integer)
				return false;
		return true;
	}

	private ArrayList<Integer> getPixelRow(BufferedImage image, int row) {
		// System.out.println("Getting row " + row);
		ArrayList<Integer> list = new ArrayList<Integer>();
		try {
			for (int i = 0; i < image.getWidth(); i++)
				list.add(image.getRGB(i, row));
		} catch (Exception e) {
			// e.printStackTrace();
		}
		return list;
	}

	private ArrayList<Integer> getPixelColumn(BufferedImage image, int column) {
		ArrayList<Integer> list = new ArrayList<Integer>();
		for (int i = 0; i < image.getHeight(); i++)
			list.add(image.getRGB(column, i));
		return list;
	}

	public static BufferedImage makeColorTransparent(BufferedImage image, int colorToChangeRGB) {
		return changeColor(image, colorToChangeRGB, Color.OPAQUE);
	}

	public static BufferedImage changeColor(BufferedImage image, int colorToChangeRGB, int changeColorToRGB) {
		BufferedImage newImage = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int i = 0; i < newImage.getWidth(); i++)
			for (int j = 0; j < newImage.getHeight(); j++)
				if (image.getRGB(i, j) == colorToChangeRGB)
					newImage.setRGB(i, j, changeColorToRGB);
				else
					newImage.setRGB(i, j, image.getRGB(i, j));
		return newImage;
	}

	protected ArrayList<ArrayList<BufferedImage>> spriteSheet(BufferedImage spriteSheet) {
		int maxY = -1;
		ArrayList<ArrayList<BufferedImage>> newImages = new ArrayList<ArrayList<BufferedImage>>();
		ArrayList<BufferedImage> tempImages = new ArrayList<BufferedImage>();
		int outlineColor = spriteSheet.getRGB(0, 0);
		int ignoreColor = Integer.MIN_VALUE;
		for (int j = 0; j < spriteSheet.getHeight(game); j++) {
			// if (debug)game.logger.log(LogLevel.DEBUG, "outside j set to " +
			// j);
			for (int i = 0; i < spriteSheet.getWidth(game); i++) {
				// if (debug)game.logger.log(LogLevel.DEBUG, "outside i set to "
				// + i);
				int thisI = 0, thisJ = 0, thisIstart = 0;
				if (ignoreColor != Integer.MIN_VALUE && ignoreColor == spriteSheet.getRGB(i, j)) {
					// System.out.println("breaking");
					break;
				} else if (outlineColor != spriteSheet.getRGB(i, j)) {
					thisI = i;
					// if (debug)
					// game.logger.log(LogLevel.DEBUG, "starting thisI set to "
					// + thisI);
					thisJ = j;
					// if (debug)
					// game.logger.log(LogLevel.DEBUG, "starting thisJ set to "
					// + thisJ);
					thisIstart = thisI;
					// if (debug)
					// game.logger.log(LogLevel.DEBUG,
					// "starting thisIstart set to "
					// + thisIstart);
					try {
						while (spriteSheet.getRGB(thisI, thisJ) != outlineColor) {
							// if (debug)game.logger.log(LogLevel.DEBUG,
							// "thisI increased to " + thisI);
							thisI++;
						}
						// if (debug)
						// game.logger.log(LogLevel.DEBUG,
						// "Found a color that isn't the background color with
						// thisI at ("
						// + thisI + "," + thisJ + ")");
					} catch (ArrayIndexOutOfBoundsException oub) {
						break;
					}
					// if (debug)
					// game.logger.log(LogLevel.DEBUG,
					// "Done adding to thisI. Went down one to " + thisI);
					try {
						while (spriteSheet.getRGB(thisIstart, thisJ) != outlineColor) {
							// if (debug)game.logger.log(LogLevel.DEBUG,
							// "thisJ increased to " + thisJ);
							thisJ++;
						}
						// if (debug)
						// game.logger.log(LogLevel.DEBUG,
						// "Found a color that isn't the background color with
						// thisJ at ("
						// + thisI + "," + thisJ + ")");
					} catch (ArrayIndexOutOfBoundsException oub) {
						break;
					}
					// if (debug)
					// game.logger.log(LogLevel.DEBUG,
					// "Done adding to thisJ. Went down one to " + thisJ);
					try {
						BufferedImage aNewImage = spriteSheet.getSubimage(i, j, (thisI - i), (thisJ - j));
						boolean dontAdd = false;
						if (aNewImage.getWidth(game) < 5 || aNewImage.getHeight(game) < 5)
							dontAdd = true;
						if (!dontAdd) {
							aNewImage = makeColorTransparent(aNewImage, outlineColor);
							aNewImage = makeColorTransparent(aNewImage, aNewImage.getRGB(aNewImage.getWidth() - 1, 0));
							if (aNewImage.getWidth() < 5 || aNewImage.getHeight() < 5)
								dontAdd = true;
							if (!dontAdd) {
								// if (debug)
								// game.logger.log(LogLevel.DEBUG,
								// "dontAdd is false");
								int thisY = j + 1 + (thisJ - j - 1) + 1;
								if (thisY > maxY && maxY != -1) {
									// if (debug)
									// game.logger.log(LogLevel.DEBUG,
									// "thisY is bigger than maxY");
									// if (debug)
									// game.logger
									// .log(LogLevel.DEBUG,
									// "This isn't the first image, so lets
									// officially add it!");
									ArrayList<BufferedImage> tempList = new ArrayList<BufferedImage>();
									for (int z = 0; z < tempImages.size(); z++)
										tempList.add(tempImages.get(z));
									newImages.add(tempList);
									tempImages = new ArrayList<BufferedImage>();
									maxY = thisY;
									// if (debug)
									// game.logger.log(LogLevel.DEBUG,
									// "maxY updated to " + maxY);
								} else {
									if (thisY > maxY)
										maxY = thisY;
								}
							}
							if (ignoreColor == Integer.MIN_VALUE) {
								// System.out
								// .println("Checking to see if we can get
								// ignore color");
								boolean foundDiffColor = false;
								int color = aNewImage.getRGB(0, 0);
								for (int x = 0; x < aNewImage.getWidth() && !foundDiffColor; x++)
									for (int y = 0; y < aNewImage.getHeight() && !foundDiffColor; y++)
										if (aNewImage.getRGB(x, y) != color) {
											// System.out.println(aNewImage.getRGB(x,
											// y)
											// + " != " + color);
											foundDiffColor = true;
										}
								if (!foundDiffColor) {
									ignoreColor = aNewImage.getRGB(0, 0);
									// System.out.println("ignoreColor set to "
									// + ignoreColor);
									aNewImage.flush();
									aNewImage = null;
									continue;
								} else {
									// System.out
									// .println("We found a diff color, so this
									// isn't the one to ignore");
									// File outputfile = new File("image "
									// + (tempImages.size() + 1) + ".png");
									// ImageIO.write(aNewImage, "png",
									// outputfile);
								}
							}
							tempImages.add(aNewImage);
						}
						BufferedImage temp = spriteSheet;
						for (int x = i; x < thisI; x++)
							for (int y = j; y < thisJ; y++)
								temp.setRGB(x, y, outlineColor);
						spriteSheet = temp;
					} catch (Exception e) {
						e.printStackTrace();
						game.logger.log(LogLevel.WARNING, "Got Exception, returning. (" + newImages.size() + ")");
						return newImages;
					}
					if (debug)
						game.logger.log(LogLevel.DEBUG,
								"Done with image. (" + tempImages.size() + "," + newImages.size() + ")");
				} else {
					continue;
				}
			}
		}
		ArrayList<BufferedImage> tempList = new ArrayList<BufferedImage>();
		for (int z = 0; z < tempImages.size(); z++) {
			tempList.add(tempImages.get(z));
		}
		newImages.add(tempList);
		for (int z = 0; z < tempList.size(); z++) {
			tempImages.get(z).flush();
			tempImages.set(z, null);
		}
		game.logger.log(LogLevel.DEBUG, "Got spritesheet");
		return newImages;
	}

	public BufferedImage f(String directory, String fileName) {
		return findAndLoadImage(directory, fileName);
	}

	private BufferedImage findAndLoadImage(String directory, String fileName) {
		return findAndLoadImage(directory, fileName, debug);
	}

	private BufferedImage findAndLoadImage(String directory, String fileName, boolean displayUnfoundFiles) {
		String finalDirectory = "/res/pictures/" + directory + fileName + ".png";
		if (debug)
			game.logger.log(LogLevel.DEBUG, "Loading image: " + finalDirectory);
		BufferedImage image = null;
		try {
			image = ImageIO.read(Pictures.class.getResourceAsStream(finalDirectory));
		} catch (Exception e) {
			// e.printStackTrace();
			if (displayUnfoundFiles)
				game.logger.err(LogLevel.ERROR, "We couldn't find the image, returning null: " + fileName);
		}
		return image;
	}

	@SuppressWarnings("unused")
	private BufferedImage findAndLoadImage_OLD(String fileName, boolean displayUnfoundFiles) {
		System.gc();
		try {
			String mainPath = ResourceHandler.get(game.logger, "pictures", Tools.getJarName());
			if (System.getProperty("os.name").contains("Linux")) {
				if (debug)
					game.logger.log(LogLevel.DEBUG, "On linux, adjusting path accordingly.");
				mainPath = "/" + mainPath;
			}
			if (debug)
				game.logger.log(LogLevel.DEBUG, "Got main path: " + mainPath);
			File foundFile = findFileOrFolder_OLD(new File(mainPath), fileName);
			if (foundFile == null) {
				if (displayUnfoundFiles)
					game.logger.err(LogLevel.DEBUG, "We couldn't find the image: " + fileName);
			} else {
				if (debug)
					game.logger.log(LogLevel.DEBUG, "Found the file: " + foundFile.getAbsolutePath());
				BufferedImage image = getImage(foundFile.getAbsolutePath());
				game.logger.log(LogLevel.DEBUG, "Extracted " + fileName + " image");
				return image;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (displayUnfoundFiles)
			game.logger.err(LogLevel.DEBUG, "We couldn't find the image, returning null: " + fileName);
		return null;
	}

	private File findFileOrFolder_OLD(File folder, String fileToFind) {
		game.repaint();
		if (debug)
			game.logger.log(LogLevel.DEBUG, "findFilesAndFolders(" + folder.getName() + "," + fileToFind + ") called.");
		if (folder.exists()) {
			if (debug)
				game.logger.log(LogLevel.DEBUG, "Folder exists! Getting list of files.");
		} else if (debug)
			game.logger.log(LogLevel.DEBUG, "Folder doesn't exist..");
		File[] list = folder.listFiles();
		if (debug)
			game.logger.log(LogLevel.DEBUG, "Folder size: " + list.length);
		File foundFile = null;
		for (int i = 0; i < list.length; i++) {
			if (list[i].isDirectory()) {
				if (debug)
					game.logger.log(LogLevel.DEBUG, "Looking into directory: " + list[i].getAbsolutePath());
				foundFile = findFileOrFolder_OLD(list[i], fileToFind);
				if (foundFile != null)
					return foundFile;
			} else {
				String fileName = list[i].getName().split("\\.")[0].replace("-", " ");
				if (debug)
					game.logger.log(LogLevel.DEBUG, "Comparing [" + fileToFind + "] to [" + fileName + "]");
				if (fileName.equalsIgnoreCase(fileToFind.replace("-", " ")))
					return list[i];
			}
		}
		if (debug)
			game.logger.log(LogLevel.DEBUG, "Couldn't find it :( returning null");
		return null;
	}

	private BufferedImage getImage(String imageName) throws IOException {
		File file = new File(imageName);
		BufferedImage image = ImageIO.read(file);
		if (file.exists() && ResourceHandler.runningFromJar() && !ResourceHandler.resAlreadyExists())
			file.delete();
		return image;
	}

	public static Image toImage(BufferedImage bufferedImage) {
		return Toolkit.getDefaultToolkit().createImage(bufferedImage.getSource());
	}

	// This method returns a buffered image with the contents of an image
	public static BufferedImage toBufferedImage(Image image) {
		if (image instanceof BufferedImage) {
			return (BufferedImage) image;
		}

		// This code ensures that all the pixels in the image are loaded
		image = new ImageIcon(image).getImage();

		// Determine if the image has transparent pixels; for this method's
		// implementation, see Determining If an Image Has Transparent Pixels
		boolean hasAlpha = hasAlpha(image);

		// Create a buffered image with a format that's compatible with the
		// screen
		BufferedImage bimage = null;
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		try {
			// Determine the type of transparency of the new buffered image
			int transparency = Transparency.OPAQUE;
			if (hasAlpha) {
				transparency = Transparency.BITMASK;
			}

			// Create the buffered image
			GraphicsDevice gs = ge.getDefaultScreenDevice();
			GraphicsConfiguration gc = gs.getDefaultConfiguration();
			bimage = gc.createCompatibleImage(image.getWidth(null), image.getHeight(null), transparency);
		} catch (HeadlessException e) {
			// The system does not have a screen
		}

		if (bimage == null) {
			// Create a buffered image using the default color model
			int type = BufferedImage.TYPE_INT_RGB;
			if (hasAlpha) {
				type = BufferedImage.TYPE_INT_ARGB;
			}
			bimage = new BufferedImage(image.getWidth(null), image.getHeight(null), type);
		}

		// Copy image to buffered image
		Graphics g = bimage.createGraphics();

		// Paint the image onto the buffered image
		g.drawImage(image, 0, 0, null);
		g.dispose();
		image.flush();
		image = null;

		return bimage;
	}

	// This method returns true if the specified image has transparent pixels
	public static boolean hasAlpha(Image image) {
		// If buffered image, the color model is readily available
		if (image instanceof BufferedImage) {
			BufferedImage bimage = (BufferedImage) image;
			return bimage.getColorModel().hasAlpha();
		}

		// Use a pixel grabber to retrieve the image's color model;
		// grabbing a single pixel is usually sufficient
		PixelGrabber pg = new PixelGrabber(image, 0, 0, 1, 1, false);
		try {
			pg.grabPixels();
		} catch (InterruptedException e) {
		}

		// Get the image's color model
		ColorModel cm = pg.getColorModel();
		return cm.hasAlpha();
	}
}
