package imageEncoder;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.io.*;
import java.security.DigestException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Random;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.*;

import org.imgscalr.Scalr;

/*
 * 	TODOs: 
 *  1. Change the sign bit from last to the first
 *  2. Change the size of the embeded QR code
 *  3. Debug the mapping mystery
 * */


public class ScaleImage {
	
	private final int DEFAULT_BLANK_COLOR = 0;
	private final int RGB_VALUE_MASK = (1<<24) - 1;
	private final int QR_IMG_BLOCKS = 113; // QR Version 24
	private final int BASE_IMG_QR_BLOCKS = 33; // QR Version 4
	private final int LINE_RATIO_OF_BIG_OVER_SMALL_QR = 3;
	private final int BASE_PER_ENTRY_SIZE = 4;
	private final int SINGLE_CHANNEL_MASK = 0xff;
	private final int SPECIAL_MARK_BIT = 0xfffefffe;
	private final int RGB_MIN_INCREMENT = 0x2;
	private final int FIRST_LAYER_PARTITION_NUMBER = 10;
	private final int SECOND_LAYER_PARTITION_NUMBER = 96;
	private final int SECOND_LAYER_EXCEPTION_NUMBER = 8;
	private final int SECOND_LAYER_CHOOSE_NUMBER = 2;
	
	private int numlocs = BASE_IMG_QR_BLOCKS;
	private int numcells = numlocs*numlocs;
    private int[] cells;
    private int[] avgRgbs;
    
    private String privateKey = "This is a private key";
    
    public ScaleImage () {
    	
    	cells = new int[numcells];
    	avgRgbs = new int[numcells];
    	
    	for(int i = 0; i < numcells; i++) {
    		cells[i] = i;
    		avgRgbs[i] = 0;
    	}
    	
    }

	public void scale(String srcFile, int destWidth, int destHeight,
			String destFile) throws IOException {
		
			BufferedImage src = ImageIO.read(new File(srcFile));
			BufferedImage dest = new BufferedImage(destWidth,destHeight,
													BufferedImage.TYPE_INT_RGB);
			Graphics2D g = dest.createGraphics();
			
			AffineTransform at = AffineTransform.getScaleInstance(
												(double)destWidth/src.getWidth(),
												(double)destHeight/src.getHeight());
			
			g.drawRenderedImage(src,at);
			ImageIO.write(dest,"JPG",new File(destFile));
	}

	public void scaleWithProportion(String srcFile, int destWidth, int destHeight,
			String destFile) throws IOException {
		
			BufferedImage src = ImageIO.read(new File(srcFile));
			BufferedImage dest = Scalr.resize(src, destWidth, destHeight);
			ImageIO.write(dest,"JPG",new File(destFile));
	}

	public void scaleWithBlank(String srcFile, int destWidth, int destHeight,
			String destFile) throws IOException {
		
			BufferedImage src = ImageIO.read(new File(srcFile));
			int srcWidth, srcHeight;
			BufferedImage dest = null;
			
			srcWidth = src.getWidth();
			srcHeight = src.getHeight();
			
			dest = new BufferedImage(srcWidth, srcHeight,
													BufferedImage.TYPE_INT_RGB);
			
			HashMap<Integer, Integer> rgbToFreqMap = new HashMap<Integer, Integer> ();
			
			
	    	for (int x = 0; x < srcWidth; x++) {
	    		
	    		for (int y = 0; y < srcHeight; y++) {
	    			
	    			int rgb = src.getRGB(x, y) ;
	    			int alpha = 0;
	    			 
	    			if ((rgb & RGB_VALUE_MASK) != 0 && (rgb & RGB_VALUE_MASK) != RGB_VALUE_MASK) {
	    				int r, g, b;
	    				
	    				alpha = rgb - (rgb & RGB_VALUE_MASK);
	    				
	    				r = (rgb >> 16) & 0xff;
	    				g = (rgb >> 8) & 0xff;
	    				b = rgb & 0xff;
	    				
	    				if (r < 128 || g < 128 || b < 128) {

		    				rgb = alpha;
	    				} else {
	    					
	    					rgb = alpha | RGB_VALUE_MASK;
	    					
	    				}
	    				
	    			} 
	    			
	    			dest.setRGB(x, y, rgb );
	    			
	    			if(rgbToFreqMap.containsKey(rgb)) {
	    				
	    				int lFreq = rgbToFreqMap.get(rgb);
	    				
	    				lFreq++;
	    				
	    				rgbToFreqMap.put(rgb, lFreq);
	    				
	    			} else {
	    				
	    				rgbToFreqMap.put(rgb, 1);
	    				
	    			}
	    			
	    			
	    		}
	    		
	    	}
	    	
	    	Iterator<Integer> myIter = rgbToFreqMap.keySet().iterator();
	    	
	    	while (myIter.hasNext()) {
	    		
	    		int rgb = myIter.next();
	    		
	    		int freq = rgbToFreqMap.get(rgb);
	    		
	    		System.out.println("r " + ((rgb >> 16) & 255) + 
						   " g " + ((rgb >> 8) & 255)+ 
						   " b " + (rgb & 255) + 
						   " freq " + freq);
	    		
	    	}
	    	
	    	
	    	/* if (destWidth > srcWidth) {

		    	for (int x = srcWidth; x < destWidth; x++) {
		    		
		    		for (int y = 0; y < destHeight; y++) {
		    				
		    			dest.setRGB(x, y, DEFAULT_BLANK_COLOR );
		    		}
		    		
		    	}	    		
	    		
	    	} else {
	    	
		    	for (int x = 0; x < srcWidth; x++) {
		    		
		    		for (int y = srcHeight; y < destHeight; y++) {
		    				
		    			dest.setRGB(x, y, DEFAULT_BLANK_COLOR );
		    		}
		    		
		    	}
	    	
	    	} */
			
			ImageIO.write(dest,"JPG",new File(destFile));
	}
	
    boolean zeroDistance(int src, int dst) {
    	int dx, dy;
    	char rDelta = 0, gDelta = 0, bDelta = 0;
    	char rShift = 0, gShift = 0, bShift = 0;
    	
    	dx = ((dst - src)%numlocs + numlocs) % numlocs;
    	dy = ((dst - src)/numlocs + numlocs) % numlocs;

		rShift = (char) ((dx  + dy) % 8);
		gShift = (char) ((dx * dx + dy * dy) % 8);
		bShift = (char) ((dx * dx * dx + dy * dy * dy) % 8);
    	
		rDelta = (char) ((dx  + dy) % 256);
		gDelta = (char) ((dx * dx + dy * dy) % 256);
		bDelta = (char) ((dx * dx * dx + dy * dy * dy) % 256);

		return (((rDelta == 0) && (gDelta == 0) && (bDelta == 0)) 
				|| ((rShift == 0) && (gShift == 0) && (bShift == 0)));
    }
	
	
	// It is important to encode the 33x33 mappings using the 0-9 numerical values
	// So minimal mapping hack is needed, how to do it? 
	// 0-9 is the source index, 
	// so the basic question is how to derive 33 from 0-9 
	// First, 4x4 mapping instead of 33x33 mapping
	// For each mapping entry, 1 number + another number for one dimension,
	// So 4 numbers for one mapping entry, in total 4*33*33= 4356 numbers
	// Use version 24, 113x113, at level M, map to 2188 numbers
	// At least 113 pixels are needed to draw the QR code rectangle
	// A need to make the whole graph rectangle
	
	public class QRPosition {
		int x;
		int y;
		int size;
		
	};
	
	public class WBRatio {
		int white;
		int black;
	};
	
	public QRPosition computeQRCodePosition(int srcWidth, int srcHeight) {
		
		QRPosition ret = new QRPosition();
		int maxSize = srcWidth;
		int minSize = srcWidth;
		
		if (maxSize < srcHeight) {
			maxSize = srcHeight;
		}
		
		if (minSize > srcHeight) {
			minSize = srcHeight;
		}
		
		
		ret.y = 0;
		ret.size = maxSize / LINE_RATIO_OF_BIG_OVER_SMALL_QR;
		ret.x = maxSize - ret.size;
		
		
		return ret;
	}
	
	public int computeOverlapArea (int srcWidth, int srcHeight, QRPosition pos, int maxWidth) {
		
		int overlapArea = 0;
		
		if (srcWidth > srcHeight) {
			
			int tapInWidth = pos.size - (maxWidth - srcHeight);
			
			assert(srcWidth == maxWidth);
			
			overlapArea = tapInWidth * pos.size;
			
			
		} else if (srcWidth < srcHeight) {
			
			int tapInWidth = pos.size - (maxWidth - srcWidth);
			
			assert(srcWidth == maxWidth);
			
			overlapArea = tapInWidth * pos.size;
			
			
		} else {
			
			overlapArea = pos.size * pos.size;
			
		}
		
		return overlapArea;
	}
	
	public int computeFreeExtArea (int srcWidth, int srcHeight, QRPosition pos, int maxWidth) {
		
		int freeExtArea = 0;
		
		if (srcWidth > srcHeight) {
			
			int tapInWidth = pos.size - (maxWidth - srcHeight);
			
			assert(srcWidth == maxWidth);
			
			freeExtArea = maxWidth * (maxWidth - srcHeight) - tapInWidth * pos.size;
			
			
		} else if (srcWidth < srcHeight) {
			
			int tapInWidth = pos.size - (maxWidth - srcWidth);
			
			assert(srcWidth == maxWidth);
			
			freeExtArea = maxWidth * (maxWidth - srcWidth) - tapInWidth * pos.size;
			
			
		} else {
			
			freeExtArea = 0;
			
		}
		
		return freeExtArea;
	}
	
	public int computeLostWidth (QRPosition pos, int overflowArea) {
		
		int lostWidth = 0;
		int overflowLines = (overflowArea + pos.size/2) / pos.size;
		
		lostWidth = ( overflowLines + LINE_RATIO_OF_BIG_OVER_SMALL_QR * 2 - 1) / (LINE_RATIO_OF_BIG_OVER_SMALL_QR * 4 - 2); 
		
		return lostWidth;
	}
	

	public int moveToFreeExtArea (BufferedImage src, BufferedImage dest, 
								  int srcWidth, int srcHeight, int maxWidth, 
								  QRPosition pos, int lostWidth, int overlapArea, int overflowArea) {
		
		int remainingLines = 0;
		int movedLines = 0;
		int trueOverlapArea = overlapArea;
		int trueOverflowArea = overflowArea; 
		int rgb = 0;
		int x, y;
		
		if (srcWidth == maxWidth) {
			trueOverlapArea -= lostWidth * (pos.size - (maxWidth - srcHeight));
			trueOverflowArea -= lostWidth * (maxWidth - srcHeight);
			trueOverflowArea -= lostWidth * (srcWidth - lostWidth - pos.size);
		
			movedLines = trueOverflowArea / (pos.size - lostWidth);
			
			int destStartP;
			
			destStartP = 0;
			
			for (x = srcWidth - pos.size; x < srcWidth - lostWidth; x++) {
				for (y = maxWidth - srcHeight + lostWidth; y < maxWidth - srcHeight + movedLines + 1; y++) {

					rgb = src.getRGB(x, y - maxWidth + srcHeight);
					
					dest.setRGB(lostWidth + destStartP % (srcWidth - lostWidth - pos.size), 
							lostWidth + destStartP / (srcWidth - lostWidth - pos.size), rgb);
					
					assert(rgb % 2 == 0);
					
					destStartP++;
				}
			}
			
		} else if (srcHeight == maxWidth){
			trueOverlapArea -= lostWidth * (pos.size - (maxWidth - srcWidth));
			trueOverflowArea -= lostWidth * (maxWidth - srcWidth);
			trueOverflowArea -= lostWidth * (srcHeight - lostWidth - pos.size);
			
			movedLines = trueOverflowArea / (pos.size - lostWidth);
			
			int destStartP;
			
			destStartP = 0;
			
			for (x = maxWidth - pos.size; x < maxWidth - pos.size + movedLines + 1; x++) {
				for (y = lostWidth; y < pos.size; y++) {

					rgb = src.getRGB(x, y);
					
					dest.setRGB(srcWidth + destStartP / (srcHeight - lostWidth - pos.size),
							pos.size + destStartP % (srcHeight - lostWidth -pos.size), rgb);
					
					assert(rgb % 2 == 0);
					
					destStartP++;
				}
			}
			
		} else {
			
			System.err.println("Hmm, error in the size");
			assert(false);
		}

		remainingLines = ( trueOverlapArea - trueOverflowArea + (pos.size - lostWidth)/2 ) / (pos.size - lostWidth); 
		
		assert((remainingLines + movedLines) == overlapArea/pos.size);
		
		return remainingLines;
	}
	
	public class XYPos {
		public XYPos(int x, int y) {
			// TODO Auto-generated constructor stub
			xPos = x;
			yPos = y;
			
		}
		public XYPos() {
			// TODO Auto-generated constructor stub
			xPos = 0;
			yPos = 0;
		}
		int xPos;
		int yPos;
	};
	
	public XYPos getXYPos(int destStartP, int srcWidth, int srcHeight, int maxWidth, QRPosition pos) {
		
		XYPos retPos = new XYPos();
		int xOffset = destStartP % (4 * maxWidth);
		
		if (xOffset < maxWidth) {
			
			retPos.xPos = xOffset;
			retPos.yPos = destStartP / (4 * maxWidth);
			
		} else if (xOffset < 2 * maxWidth) {
			
			retPos.xPos = maxWidth - 1 - destStartP / (4 * maxWidth);
			retPos.yPos = xOffset - maxWidth;
			
			
		} else if (xOffset < 3* maxWidth) {
			
			retPos.xPos = maxWidth - 1 - (xOffset % maxWidth);
			retPos.yPos = maxWidth - 1 - destStartP / (4 * maxWidth);
			
		} else {
			
			retPos.xPos = destStartP / (4 * maxWidth);
			retPos.yPos = maxWidth - 1 - (xOffset % maxWidth);
			
		}
		
		
		return retPos;
	}
	
	
	public void distributeLinesFromArea(BufferedImage src, BufferedImage dest, 
										int srcWidth, int srcHeight, int maxWidth, 
										QRPosition pos, int lostWidth, int remainingLines) {

		int rgb = 0;
		int x, y;
		
		if (srcWidth == maxWidth) {
			
			int destStartP;
			
			destStartP = 0;
			
			assert(maxWidth - srcHeight + lostWidth <= maxWidth - pos.size - remainingLines);
			
			for (x = srcWidth - pos.size; x < srcWidth - lostWidth; x++) {
				for (y = maxWidth - pos.size - remainingLines; y < maxWidth - pos.size; y++) {

					rgb = src.getRGB(x, y - maxWidth + srcHeight);
					
					XYPos myPos = getXYPos(destStartP, srcWidth, srcHeight, maxWidth, pos);
					
					assert(rgb % 2 == 0);
					
					dest.setRGB(myPos.xPos, myPos.yPos, rgb);
					
					destStartP++;
				}
			}
			
		} else if (srcHeight == maxWidth){
			int destStartP;
			
			destStartP = 0;
			
			assert(maxWidth - srcWidth + lostWidth <= maxWidth - pos.size - remainingLines);
			
			
			for (x = maxWidth - pos.size; x < maxWidth - pos.size + remainingLines; x++) {
				for (y = lostWidth; y < pos.size; y++) {

					rgb = src.getRGB(x, y);

					XYPos myPos = getXYPos(destStartP, srcWidth, srcHeight, maxWidth, pos);

					assert(rgb % 2 == 0);
					
					dest.setRGB(myPos.xPos, myPos.yPos, rgb);
					
					destStartP++;
				}
			}
			
		} else {
			
			System.err.println("Hmm, error in the size");
			assert(false);
		}
		
		
	}
	
	public void fillRemainingPart(BufferedImage dest, 
			int srcWidth, int srcHeight, int maxWidth, 
			QRPosition pos, 
			int toFillArea)
	{
		int movedLines = 0;
		int rgb = 0;
		int x, y;
		
		movedLines = toFillArea / (maxWidth - pos.size);
		
		if (srcWidth == maxWidth) {
			
			int destStartP;
			
			destStartP = 0;
			
			for (x = 0; x < srcWidth - pos.size; x++) {
				for (y = maxWidth - srcHeight - movedLines; y < maxWidth - srcHeight; y++) {

					dest.setRGB(destStartP % (srcWidth - pos.size), 
							y + destStartP / (srcWidth - pos.size), rgb);
					
					destStartP++;
				}
			}
			
		} else if (srcHeight == maxWidth){
			
			int destStartP;
			
			destStartP = 0;
			
			assert(srcWidth <= maxWidth - movedLines + 1);
			
			for (x = srcWidth; x < maxWidth - movedLines + 1; x++) {
				for (y = pos.size; y < srcHeight; y++) {

					dest.setRGB(x + destStartP / (srcHeight - pos.size),
							pos.size + destStartP % (srcHeight - pos.size), rgb);
					
					destStartP++;
				}
			}
			
		} else {
			
			System.err.println("Hmm, error in the size");
			assert(false);
		}
		
	}
	
	public String getNumbersInString(int num) 
	{
		String ret = null;
		int i = 0;
		
		for (i = 0; i < num; i++) {
			int number = (int) (Math.random() * 9);
			
			//ret.concat(Integer.toHexString(number));
			
			ret = ret.format("%x", number);
			
		}
		
		
		return ret;
	}
	
	public BufferedImage generateQRImg(String contentCode, int maxWidth)
	{
		BufferedImage bufImg = null; 
		
		BarcodeFormat barcodeFormat = BarcodeFormat.valueOf("QR_CODE");
	    int width = maxWidth;
	    int height = maxWidth;
		    
	    if (contentCode == null) {
	      System.err.println("Cannot get the QRImg");
	      return bufImg;
	    }
	    
	    @SuppressWarnings("unused")
		MultiFormatWriter barcodeWriter = new MultiFormatWriter();
	    BitMatrix matrix;
		try {
			matrix = barcodeWriter.encode(contentCode, barcodeFormat, width, height);
			bufImg = MatrixToImageWriter.toBufferedImage(matrix);
		} catch (WriterException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return bufImg;
		
	}
	
	public WBRatio computeRatio(BufferedImage src) {
		
		WBRatio ret = new WBRatio();

		int srcWidth = src.getWidth();
		int srcHeight = src.getHeight();
		
		ret.black = 0;
		ret.white = 0;

    	for (int x = 0; x < srcWidth; x++) {
    		
    		for (int y = 0; y < srcHeight; y++) {
    			
    			int rgb = src.getRGB(x, y) ;
    			int alpha = 0;
    			
    			if ((rgb & RGB_VALUE_MASK) != 0 && (rgb & RGB_VALUE_MASK) != RGB_VALUE_MASK) {
    				int r, g, b;
    				
    				alpha = rgb - (rgb & RGB_VALUE_MASK);
    				
    				r = (rgb >> 16) & 0xff;
    				g = (rgb >> 8) & 0xff;
    				b = rgb & 0xff;
    				
    				if (r < 128 || g < 128 || b < 128) {

	    				rgb = alpha;
    				} else {
    					
    					rgb = alpha | RGB_VALUE_MASK;
    					
    				}
    				
    			} 
    	
    			if ((rgb & RGB_VALUE_MASK) == 0) {
    				
    				ret.black++;
    			} else {
    				
    				ret.white++;
    			}
    		}
    	}
    	
		return ret;
	}
	
	public class ThresholdPair {
		
		int threshold;
		int excessiveEqualNumbers;
		
		int blockMap[];
		
		byte mutationMap[];
		
		int ones;
		int zeros;
		
	};
	
	public ThresholdPair computeThresholdRGB (WBRatio contentRatio, int xPartitions, int yPartitions, BufferedImage dest) {
		
		ThresholdPair tp = new ThresholdPair();
		int rgb, x, y, perPartitionPixels, subRgb, x1, y1;
		int splitIndex = 0, arraySize = 0;
		ArrayList<Integer> rgbMatrix = new ArrayList<Integer>();
		
		tp.blockMap = new int[xPartitions * yPartitions];
		
		perPartitionPixels = dest.getHeight() / yPartitions;
		
		for (x = 0; x < xPartitions; x++) {
			for (y = 0; y < yPartitions; y++) {
				
				tp.blockMap[x * xPartitions + y] = 0;
				
				if (!(x >= xPartitions - xPartitions/LINE_RATIO_OF_BIG_OVER_SMALL_QR && y <= yPartitions/LINE_RATIO_OF_BIG_OVER_SMALL_QR)) {
					subRgb = 0;
					
					for (x1 = 0; x1 < perPartitionPixels; x1++) {
						
						for (y1 = 0; y1 < perPartitionPixels; y1++) {
	
							rgb = dest.getRGB(x * perPartitionPixels + x1, y * perPartitionPixels + y1);
							
							assert(rgb % 2 == 0);
							
							subRgb += (rgb & RGB_VALUE_MASK);
									
						}
					}
					
					rgbMatrix.add(subRgb / (perPartitionPixels * perPartitionPixels));
					
					tp.blockMap[x * xPartitions + y] = subRgb / (perPartitionPixels * perPartitionPixels);
				
					avgRgbs[x * xPartitions + y] = subRgb / (perPartitionPixels * perPartitionPixels);
				}
			}
			
		}
		
		Collections.sort(rgbMatrix);
		
		//TODO: what should we do for the image with equal RGB values? 
		// Get the number of items above the splitIndex but with the same 
		// RGB value
		splitIndex = rgbMatrix.size() * contentRatio.black / (contentRatio.black + contentRatio.white);
		
		tp.threshold = rgbMatrix.get(splitIndex);
		
		arraySize = rgbMatrix.size();
		tp.excessiveEqualNumbers = 0;
		while (splitIndex < arraySize && rgbMatrix.get(splitIndex) == tp.threshold) {
			
			splitIndex++;
			tp.excessiveEqualNumbers++;
			
		}
		
		for (x = 0; x < xPartitions; x++) {
			for (y = 0; y < yPartitions; y++) {
		
				if (tp.blockMap[x * xPartitions + y] > tp.threshold) {
					tp.blockMap[x * xPartitions + y] = 1;
				} else {
					tp.blockMap[x * xPartitions + y] = 0;
				}
			
			}
			
		}
		
		tp.zeros = 0;
		tp.ones = 0;
		
		for(int i = 0; i < tp.blockMap.length; i++) {
			if (tp.blockMap[i] == 0) {
				tp.zeros++;
				
			} else {
				tp.ones++;
				
			}
		}
		
		
		return tp;
	}
	
	public char getR (int rgb) {
		
		char r = (char) ((rgb & 0xff0000) >>> 16);
		
		return r;
	}
	

	public char getB (int rgb) {
		
		char r = (char) ((rgb & 0xff));
		
		return r;
	}
	
	public void adjustRGBForThreshold(BufferedImage dest, int xPartitions, int yPartitions, ThresholdPair tp)
	{
		int rgb, x, y, perPartitionPixels, subRgb, x1, y1, newB;
		ArrayList<XYPos> rgbArray = new ArrayList<XYPos>();
		
		perPartitionPixels = dest.getHeight() / yPartitions;
		
		for (x = 0; x < xPartitions; x++) {
			for (y = 0; y < yPartitions; y++) {
				
				if (!(x >= xPartitions - xPartitions/LINE_RATIO_OF_BIG_OVER_SMALL_QR && y <= yPartitions/LINE_RATIO_OF_BIG_OVER_SMALL_QR)) {
					subRgb = 0;
					
					for (x1 = 0; x1 < perPartitionPixels; x1++) {
						
						for (y1 = 0; y1 < perPartitionPixels; y1++) {
	
							rgb = dest.getRGB(x * perPartitionPixels + x1, y * perPartitionPixels + y1);
							
							assert(getR(rgb) % 2 == 0);
							
							assert(rgb % 2 == 0);
							
							subRgb += (rgb & RGB_VALUE_MASK);
									
						}
					}
					
					if(subRgb / (perPartitionPixels * perPartitionPixels) == tp.threshold){
						
						rgbArray.add(new XYPos(x, y));
					}
				}
			}
			
		}
		
		assert(rgbArray.size() >= tp.excessiveEqualNumbers);
		
		Collections.shuffle(rgbArray);
		
		for(x = 0; x < tp.excessiveEqualNumbers; x++) {
			
			for (x1 = rgbArray.get(x).xPos * perPartitionPixels; x1 < rgbArray.get(x).xPos * perPartitionPixels + perPartitionPixels; x1++) {
				
				for (y1 = rgbArray.get(x).yPos * perPartitionPixels; y1 < rgbArray.get(x).yPos * perPartitionPixels + perPartitionPixels; y1++) {

					rgb = dest.getRGB(x1, y1);

					newB = getB(rgb);
					
					assert(getR(rgb) % 2 == 0);
					
					assert(rgb % 2 == 0);
					
					if (newB < 0xff - RGB_MIN_INCREMENT) {
					
						dest.setRGB(x1, y1, rgb + RGB_MIN_INCREMENT);
						assert(getR(rgb + RGB_MIN_INCREMENT) % 2 == 0);
					} else {
						dest.setRGB(x1, y1, rgb);
						assert(getR(rgb) % 2 == 0);
						
						assert((rgb & 0xffffff) >= tp.threshold);
						
					}
							
				}
			}
			
			tp.blockMap[rgbArray.get(x).xPos * xPartitions + rgbArray.get(x).yPos] = 1;
		}
		
		tp.excessiveEqualNumbers = 0;
	}
	
	/*	Format a string value in the range of [0, 255]
	 * */
	public String formatString(int v) {
	
		String subStr = new String();
		
		subStr = String.format("%03d", v);
		
		return subStr;
		
	}
	
	public String getNineRightDigit(int thresholdRGB)
	{
		String ret = new String();
		int r, g, b;
		
		r = (thresholdRGB >> 16) & 0xff;
		g = (thresholdRGB >> 8) & 0xff;
		b = (thresholdRGB) & 0xff;
		
		ret.concat(formatString(r));
		ret.concat(formatString(g));
		ret.concat(formatString(b));
		
		return ret;
		
	}
	

	
	public void jumblePrimaryImage(BufferedImage dest, ThresholdPair tp)
	{
		int allOnes = 0;
		int allZeros = 0;
		int srcWidth, srcHeight;
		
		srcWidth = BASE_IMG_QR_BLOCKS;
		srcHeight = BASE_IMG_QR_BLOCKS;
		
		int valueOneMap [] = new int[tp.ones];
		int valueZeroMap [] = new int[tp.zeros];
		
		allOnes = 0;
		allZeros = 0;
		
		for(int i = 0; i < tp.blockMap.length; i++) {
			int xOffset = i % srcWidth;
			int yOffset = i / srcWidth;
			
			if (! (xOffset >= srcWidth - srcWidth/LINE_RATIO_OF_BIG_OVER_SMALL_QR && yOffset < srcHeight/LINE_RATIO_OF_BIG_OVER_SMALL_QR)) {
				
				if (tp.blockMap[i] == 0) {
					valueZeroMap[allZeros++] = i;
					
				} else {
					valueOneMap[allOnes++] = i;
					
				}
			
			}
		}
		
		//TODO: mutation with the fundamental change
		// What should happen to the internal mapping? 
		// Internal mapping
		// Bi-partite mapping: the only and unique mapping: easy, fill in the slot as the order of missed slots
		// Random mapping among 1's
		
		int valueOneMutation[] = new int[valueOneMap.length];
		int valueZeroMutation[] = new int[valueZeroMap.length];
		
		boolean valueOneFlags[] = new boolean[valueOneMap.length];
		boolean valueZeroFlags[] = new boolean[valueZeroMap.length];
		
		Arrays.fill(valueOneFlags, false);
		Arrays.fill(valueZeroFlags, false);
		
		getValueOneMutation(valueOneMutation, tp, valueOneFlags);

		getValueOneMutation(valueZeroMutation, tp, valueZeroFlags);
		
		//assert(checkCells(valueZeroMutation, valueZeroMutation.length));
		
		//TODO: Deduce the @cells mapping from the fundamental mutation
        Random rand = new Random();
        int ri;
        for (int i=0; i < valueOneMap.length; i++) {

        	ri = valueOneMutation[i];
        	
        	if (valueOneFlags[ri] == false) {
	        	
	            int tmp = cells[valueOneMap[i]];
	            cells[valueOneMap[i]] = cells[valueOneMap[ri]];
	            cells[valueOneMap[ri]] = tmp;
            
        	}
        }
        


		// Random mapping among 0's
		for (int i=0; i < valueZeroMap.length; i++) {
            
			ri = valueZeroMutation[i];

        	if (valueZeroFlags[ri] == false) {
			
	            int tmp = cells[valueZeroMap[i]];
	            cells[valueZeroMap[i]] = cells[valueZeroMap[ri]];
	            cells[valueZeroMap[ri]] = tmp;
            
        	}
        }
		
		//assert(checkCells(cells, cells.length));
		
		int cellBitMap [] = new int[numcells];
		int checkedCells;
		
		checkedCells = 0;
		ri = 0;
		while (checkedCells < numcells) {
	        
			while (cellBitMap[ri] != 0) {
				ri++;
			}
			
	        ArrayList<Integer> retCells = changeContentPreservedThreshold(dest, ri, tp.threshold, cells);
	        
	        Iterator<Integer> iter = retCells.iterator();
	        while (iter.hasNext()) {
	        	cellBitMap[iter.next()] = 1;
	        }
	        
	        checkedCells += retCells.size();
	        
	    }
		
		assert(checkedCells == numcells);
		
	}
	
	
	private boolean checkCells(int[] cells2, int len) {
		// TODO Auto-generated method stub
		
		for (int i = 0; i < len; i++) {
			if (i == cells2[i]) {
				assert(false); 
				return false;
			}
		}
		
		return true;
	}
	
	private boolean checkCells(byte[] cells2,
			int len) {
		for (int i = 0; i < len; i++) {
			if (i == cells2[i]) {
				assert(false); 
				return false;
			}
		}
		
		return true;
	}

	public class OffsetAndCircleStart {
		
		public OffsetAndCircleStart(int offset2, boolean b) {
			// TODO Auto-generated constructor stub
			offset = offset2;
			isCircleStart = b;
		}
		int offset;
		boolean isCircleStart;
		
	};
	
	/*
	 * @valueMutation[O]: the output structure
	 * @tp[I]: the source of the mutation
	 * */
	private void getValueOneMutation(int[] valueMutation, ThresholdPair tp, boolean[] valueFlags) {
		int i, j;
		byte srcMap[] = tp.mutationMap;
		int modifiedLayerOneLength[] = new int[FIRST_LAYER_PARTITION_NUMBER]; 
		int totalLen = valueMutation.length;
		int baseLen = totalLen / FIRST_LAYER_PARTITION_NUMBER;
		int remainder = FIRST_LAYER_PARTITION_NUMBER - totalLen % FIRST_LAYER_PARTITION_NUMBER;
		OffsetAndCircleStart startOffsets[] = new OffsetAndCircleStart[FIRST_LAYER_PARTITION_NUMBER];
		int offset = 0;
		boolean firstLayerFlags[] = new boolean[FIRST_LAYER_PARTITION_NUMBER];
		boolean secondLayerFlags[] = new boolean[baseLen + 1];
		
		assert(SECOND_LAYER_PARTITION_NUMBER >= baseLen + 1);
		
		for (i = 0; i < valueMutation.length; i++) {
			valueMutation[i] = i;
		}
		
		for (i = 0; i < FIRST_LAYER_PARTITION_NUMBER; i++) {
			modifiedLayerOneLength[i] = baseLen + 1;
		}
		
		int bitMap[] = new int[FIRST_LAYER_PARTITION_NUMBER];
		
		for(i = 0; i < FIRST_LAYER_PARTITION_NUMBER; i++) {
			bitMap[i] = 0;
		}
		
		int changeLenSet[] = findAndModifyCircle(srcMap, FIRST_LAYER_PARTITION_NUMBER, remainder, firstLayerFlags);
		
		
		assert (changeLenSet.length == remainder);
		
		i = 0;
		for (i = 0; i < changeLenSet.length; i++) {
			modifiedLayerOneLength[changeLenSet[i]] -= 1;
		}
		
		
		offset = 0;
		for (i = 0; i < FIRST_LAYER_PARTITION_NUMBER; i++) {
		
			startOffsets[i] = new OffsetAndCircleStart(offset, firstLayerFlags[i]);
			
			offset += modifiedLayerOneLength[i];
			
		}
		
		for (i = 0; i < FIRST_LAYER_PARTITION_NUMBER; i++) {

			int internalMapping[] = new int[modifiedLayerOneLength[i]];
			int bitmap[] = new int[modifiedLayerOneLength[i]];
			
			updateSecondLayerMapping(tp, i, internalMapping, secondLayerFlags);
			
			Arrays.fill(bitmap, 0);
			
			// Second-layer exchange
			j = 0; 
			offset = 0;
			while (offset < modifiedLayerOneLength[i]) {
				
				if (secondLayerFlags[internalMapping[j]] == false) {
					
					int tmp = valueMutation[startOffsets[i].offset + j];
					
					valueMutation[startOffsets[i].offset + j] = valueMutation[startOffsets[i].offset + internalMapping[j]];
					valueMutation[startOffsets[i].offset + internalMapping[j]] = tmp;
				
				}
				
				offset++;
				bitmap[j] = 1;
				
				if (offset < modifiedLayerOneLength[i]) {
				
					if (secondLayerFlags[internalMapping[j]] == false) {
						j = internalMapping[j];
					} else {
						
						j = 0;
						while (bitmap[j] == 1) {
							j++;
						}
					}
				
				}
			}
			
		} 

		int oldMutation[] = new int[valueMutation.length];
		
		for (i = 0; i < valueMutation.length; i++) {
			oldMutation[i] = valueMutation[i];
		}
		
		offset = 0;
		i = 0;
		while (offset < FIRST_LAYER_PARTITION_NUMBER) {

			// First-layer exchange
			if (startOffsets[srcMap[i]].isCircleStart == false){
				int targetLen = modifiedLayerOneLength[srcMap[i]];
				int minLen = targetLen;
				int tmp = 0;
				
				if (targetLen > modifiedLayerOneLength[i]) {
					minLen = modifiedLayerOneLength[i];
					
				}
				
				if (valueMutation[startOffsets[i].offset + modifiedLayerOneLength[i] - 1] == startOffsets[i].offset + modifiedLayerOneLength[i] - 1) {
					
					tmp = valueMutation[startOffsets[i].offset];
					valueMutation[startOffsets[i].offset] = valueMutation[startOffsets[i].offset + modifiedLayerOneLength[i] - 1];
					valueMutation[startOffsets[i].offset + modifiedLayerOneLength[i] - 1] = tmp;
					
					assert(tmp != startOffsets[i].offset + modifiedLayerOneLength[i] - 1);
					
				}
				
				if (valueMutation[startOffsets[srcMap[i]].offset + modifiedLayerOneLength[srcMap[i]] - 1] 
						== startOffsets[srcMap[i]].offset + modifiedLayerOneLength[srcMap[i]] - 1) {
					
					tmp = valueMutation[startOffsets[srcMap[i]].offset];
					valueMutation[startOffsets[srcMap[i]].offset] = valueMutation[startOffsets[srcMap[i]].offset + modifiedLayerOneLength[srcMap[i]] - 1];
					valueMutation[startOffsets[srcMap[i]].offset + modifiedLayerOneLength[srcMap[i]] - 1] = tmp;
					
					assert(tmp != startOffsets[srcMap[i]].offset + modifiedLayerOneLength[srcMap[i]] - 1);
					
				}
				
				
				for (j = 0; j < minLen; j++) {
					
					tmp = valueMutation[startOffsets[i].offset + j];
					
					valueMutation[startOffsets[i].offset + j] = valueMutation[startOffsets[srcMap[i]].offset + j];
					valueMutation[startOffsets[srcMap[i]].offset + j] = tmp;
					
				}
			
			}
			
			offset++;
			bitMap[i] = 1;
			
			if (offset < FIRST_LAYER_PARTITION_NUMBER) {
			
				if (startOffsets[srcMap[i]].isCircleStart == false) {
					i = srcMap[i];
				} else {
					
					i = 0;
					while (bitMap[i] == 1) {
						i++;
					}
				}
			
			}
			
		}
		

		assert(checkCells(valueMutation, valueMutation.length));
		
		// Update the flag array
		{
			
			HashMap<Integer, Integer> circleMap = new HashMap<Integer, Integer> ();
			int bitmap[] = new int[valueMutation.length];
			
			Arrays.fill(bitmap, 0);
			Arrays.fill(secondLayerFlags, false);
			
			i = 0; 
			totalLen = 0;
			while (totalLen < valueMutation.length) {
				i = 0;
				
				while(bitmap[i] != 0) {
					i++;
				};
				
				while (circleMap.containsKey(i) == false) {
					
					circleMap.put(i, i);
					
					bitmap[i] = 1;
					
					i = valueMutation[i];
				}
				
				valueFlags[i] = true;
				
				totalLen += circleMap.size();
				
				circleMap.clear();
			}
			
		}
		
	}

	// Return the startPos whose current and previous sets contain @len elements in total
	private int[] findAndModifyCircle(byte[] srcMap, int firstLayerMapLen, int remainder, boolean[] firstLayerFlags) {
		// Find a circle with length > len and break at the index of @len
		int ret = -1;
		int retArray[] = new int[remainder];
		int retLen = 0;
		HashMap<Integer, Integer> circleMap = new HashMap<Integer, Integer> ();
		int i = 0;
		int pos = 0, totalLen = 0, curLen = 0;
		ArrayList<Integer> startPos = new ArrayList<Integer> ();
		int bitmap[] = new int[firstLayerMapLen];
		
		Arrays.fill(bitmap, 0);
		
		assert(firstLayerMapLen == firstLayerFlags.length);
		
		Arrays.fill(firstLayerFlags, false);
		
		totalLen = 0;
		pos = 0;
		retLen = 0;
		while (totalLen < firstLayerMapLen) {
			
			while(bitmap[pos] != 0) {
				pos++;
			};
			
			while (circleMap.containsKey(pos) == false) {
				
				if (retLen < retArray.length) {
					retArray[retLen++] = pos;
				}
				
				circleMap.put(pos, pos);
				
				bitmap[pos] = 1;
				
				pos = srcMap[pos];
			}
			
			firstLayerFlags[pos] = true;
			
			startPos.add(pos);
			startPos.add(circleMap.size());
			
			totalLen += circleMap.size();
			
			circleMap.clear();
			
		}
		
		return retArray;
		
	}

	private void updateSecondLayerMapping(ThresholdPair tp, int index,
			int[] internalMapping, boolean[] secondLayerFlags) {

		int srcMap[] = new int[SECOND_LAYER_PARTITION_NUMBER];
		int destMap[] = new int[SECOND_LAYER_PARTITION_NUMBER];
		int i = 0;
		int curDestPtr = internalMapping.length - 1;
		
		for (i = 0; i < srcMap.length; i++) {
			int storedValue = tp.mutationMap[FIRST_LAYER_PARTITION_NUMBER + 2 * index * SECOND_LAYER_PARTITION_NUMBER + 2 * i];
			
			storedValue *= 10;
			storedValue += tp.mutationMap[FIRST_LAYER_PARTITION_NUMBER + 2 * index * SECOND_LAYER_PARTITION_NUMBER + 2 * i + 1];
			
			srcMap[i] = storedValue;
			
			if(i < internalMapping.length && storedValue < internalMapping.length) {
				destMap[storedValue] = 1;
			}
		}
		
		curDestPtr = internalMapping.length - 1;
		for(i = 0; i < internalMapping.length; i++) {
			
			if(srcMap[i] < internalMapping.length) {
				
				internalMapping[i] = srcMap[i];
				
			} else {
				
				while(destMap[curDestPtr] != 0){
					curDestPtr--;
					
				}
				
				assert(curDestPtr >= 0);
				internalMapping[i] = curDestPtr;
			}
		}
		
		HashMap<Integer, Integer> circleMap = new HashMap<Integer, Integer> ();
		int bitmap[] = new int[internalMapping.length];
		int totalLen = 0;
		
		Arrays.fill(bitmap, 0);
		
		Arrays.fill(secondLayerFlags, false);
		
		
		i = 0; 
		totalLen = 0;
		while (totalLen < internalMapping.length) {
			i = 0;
			while(bitmap[i] != 0) {
				i++;
			};
			
			while (circleMap.containsKey(i) == false) {
				
				circleMap.put(i, i);
				
				bitmap[i] = 1;
				
				i = internalMapping[i];
			}
			
			secondLayerFlags[i] = true;
			
			totalLen += circleMap.size();
			
			circleMap.clear();
		}
		
	}

	public int getCeilingBit (int threshold) {
		int ret = 0;
		int value = 1;
		
		while (value < threshold) {
			
			value <<= 1;
			ret++;
			
		}
		
		return ret;
	}

	/*	Go through the chain of swapped changes and return the set of indexes
	 * */
	public ArrayList<Integer> changeContentPreservedThreshold(BufferedImage dest, int beginningSrc, int threshold, int[] cells2) {
    	
		ArrayList<Integer> retArray = new ArrayList<Integer>();
		int src = beginningSrc;
		int dst = cells2[beginningSrc];
		
		int oldSubImg [] = null; 
		
		while (true) {
	    	int dx, dy, width, height, x, y, x1, y1, x2, y2;
	    	char rDelta = 0, gDelta = 0, bDelta = 0;    	
	    	int rShift = 0, gShift = 0, bShift = 0;
	    	int w, h;
	    	int thresholdCeilingBit;
	    	
	    	char r, g, b; 
	
	    	boolean aboveThreshold = avgRgbs[src] > threshold;
			
	    	thresholdCeilingBit = getCeilingBit(threshold);
	    	
	    	w = dest.getWidth();
	    	h = dest.getHeight();
	    	
	    	dx = ((dst - src)%numlocs + numlocs) % numlocs;
	    	dy = ((dst - src)/numlocs + numlocs) % numlocs;
	
			rShift = ((dx  + dy) % 8);
			gShift = ((dx * dx + dy * dy) % 8);
			bShift = ((dx * dx * dx + dy * dy * dy) % 8);
	
			rDelta = (char) ((dx  + dy) % 256);
			gDelta = (char) ((dx * dx + dy * dy) % 256);
			bDelta = (char) ((dx * dx * dx + dy * dy * dy) % 256);
	    	
			if(rDelta % 2 == 1) {
				rDelta += 1;
			}
	
			if(bDelta % 2 == 1) {
				bDelta += 1;
			}
			
			
	    	width = w/numlocs;
	    	height = h/numlocs;
	    	
	    	x1 = (src%numlocs) * width;
	    	y1 = (src/numlocs) * height;
	    	x2 = (dst%numlocs) * width;
	    	y2 = (dst/numlocs) * height;
	    	
	    	BufferedImage subImg = dest.getSubimage(x1, y1, width, height);
	    	BufferedImage dstImg = dest.getSubimage(x2, y2, width, height);
	    	
	    	oldSubImg = new int[width * height];
	    	
	    	if(src == beginningSrc) {
	    		
	    		for(x = 0; x < width; x++) {
	    			for (y = 0; y < height; y++) {
	    				oldSubImg [y * width + x] = subImg.getRGB(x, y);
	    			}
	    		}
	    		
	    	}
	    	
	    	
	    	for (x = 0; x < width; x++) {
	    		
	    		for (y = 0; y < height; y++) {
	    			
	    			int rgb = 0;
	    			if (dst != beginningSrc) {
	
	    				rgb = dstImg.getRGB(x, y);
	    	    		
	    	    	} else {
	    	    		rgb = oldSubImg [y * width + x];
	    	    	}
	    			
	    			assert(rgb % 2 == 0);
	    			
	    			int alpha = rgb - (rgb & RGB_VALUE_MASK);
	    			
	    			r = (char) ((rgb & RGB_VALUE_MASK) >> 16);
	    			g = (char) ((rgb & 0xff00) >> 8);
	    			b = (char) (rgb & 0xff);
	
	    			assert(r % 2 == 0);
	    			
	    			r = added_color_delta(r, rDelta);
	    			g = added_color_delta(g, gDelta);
	    			b = added_color_delta(b, bDelta);
	    			
	    			int newRGB = r;
	    			
	    			assert(r % 2 == 0);
	    			
	    			newRGB <<= 8;
	    			newRGB += g;
	    			newRGB <<= 8;
	    			newRGB += b;
	    			
	    			assert(newRGB % 2 == 0);
	    			
	    			int trgt_rgb = shift_with_threshold(newRGB, rShift, gShift, bShift, 
	    												thresholdCeilingBit, threshold, aboveThreshold);
	    			/*g = modify_with_threshold(g, gShift, gDelta, thresholdCeilingBit > 8 ? (thresholdCeilingBit - 8) : 0, aboveThreshold);
	    			b = modify_with_threshold(b, bShift, bDelta, thresholdCeilingBit, aboveThreshold);
	    			
	    			 r = (char) ((r + rDelta) & SINGLE_CHANNEL_MASK);
	    			g = (char) ((g + gDelta) & SINGLE_CHANNEL_MASK);
	    			b = (char) ((b + bDelta) & SINGLE_CHANNEL_MASK); 
	
	    			r = addition_with_threshold(r, rDelta, thresholdCeilingBit > 16 ? (thresholdCeilingBit - 16) : 0, aboveThreshold);
	    			g = addition_with_threshold(g, gDelta, thresholdCeilingBit > 8 ? (thresholdCeilingBit - 8) : 0, aboveThreshold);
	    			b = addition_with_threshold(b, bDelta, thresholdCeilingBit, aboveThreshold); 
	    			
	    			int trgt_rgb = alpha;
	    			
	    			trgt_rgb <<= 8;
	    			trgt_rgb |= r;
	    			trgt_rgb <<= 8;
	    			trgt_rgb |= g;
	    			trgt_rgb <<= 8;
	    			trgt_rgb |= b;*/
	    				
	    			subImg.setRGB(x, y, trgt_rgb );
	    		}
	    		
	    	}
	    	
	    	retArray.add(src);
	
	    	if (dst == beginningSrc) {
	    		
	    		break;
	    	}
	    	
	    	src = dst;
	    	dst = cells2[src];
    	
		}
    	
    	return retArray;
    	
    }
	
    public char added_color_delta (char color, char delta) {
    	return (char) ((color + delta) & SINGLE_CHANNEL_MASK);
    }
    

    public char circular_shift_certain_bits(char v, int limits, int shift) {
    	
    	char result = v;
    	
    	char lowerPart = (char) (v & ((1 << (8 - limits)) - 1 ));
    	char upperPart = (char) (v - lowerPart);
    	char LOCAL_UPPER_MASK = (char) (0xff - ((1 << (8 - limits)) - 1));
    	
    	assert(shift < limits);
    	
    	result = (char) ((((upperPart>>>shift) & LOCAL_UPPER_MASK) | (upperPart<<(limits - shift)) ) & LOCAL_UPPER_MASK);
    	
    	result = (char)(result + lowerPart);
    	
    	return result;
    	
    }


    public char circular_shift_certain_bits_lower(char v, int limits, int shift) {
    	
    	char result = v;
    	
    	char lowerPart = (char) (v & ((1 << (limits)) - 1 ));
    	char upperPart = (char) (v - lowerPart);
    	char LOCAL_LOWER_MASK = (char) ((1 << limits) - 1);
    	
    	
    	assert(shift < limits);
    	
    	result = (char) (((lowerPart>>>shift) | (lowerPart<<(limits - shift)) ) & LOCAL_LOWER_MASK);
    	
    	result = (char)(result + upperPart);
    	
    	return result;
    	 	
    }

    /*	Limit + one bit, the last one bit is reserved for sign or other purpose
     * */

    public char circular_shift_certain_bits_lower_version2(char v, int limits, int shift) {
    	char result = v;
    	int delta = 1;
    	
    	char lowerPart = (char) (v & ((1 << (limits + delta)) - 1 ));
    	char upperPart = (char) (v - lowerPart);
    	char LOCAL_LOWER_MASK = (char) ((1 << limits + delta) - 2);
    	
    	assert(upperPart % 2 == 0);
    	
    	assert(shift < limits);
    	
    	result = (char) ((((lowerPart>>>shift) & LOCAL_LOWER_MASK) | (lowerPart<<(limits - shift)) ) & LOCAL_LOWER_MASK);
    	
    	result = (char)(result + upperPart);
    	
    	assert(result % 2 == 0);
    	
    	return result;
    	
    }
    
    public int shift_with_threshold(int rgb, int rShift, 
    								int gShift,
    								int bShift,
    								int thresholdCeilingBit, int threshold,
    								boolean groupAboveThreshold) {
    	
    	int shiftBits = 0;
    	int thresholdValue = 0;
    	int shiftedColor = 0;
    	char r = (char) ((rgb >>> 16) & SINGLE_CHANNEL_MASK);
		char g = (char) ((rgb >>> 8) & SINGLE_CHANNEL_MASK);
		char b = (char) (rgb & SINGLE_CHANNEL_MASK);
		

    	thresholdValue = (1 << thresholdCeilingBit);
    	
    	// How to make everybody above the threshold? Even for those that are not above the threshold ?
    	// Above the threshold, do the circulation as usual
    	// Below the threshold, one can use the alpha value to indicate whether the pattern above the threshold is good. 
    	// Set the maximal to 254 instead of 255. Actually reserve that bit to figure out if original the pixel is above
    	// the threshold
    	// Lower part does not need to shift, only the upper part needs to be shifted
    	
    	
    	if (groupAboveThreshold) {
    	
    		// One consecutive option is to increase the value above the threshold
    		// Assert the rgb value can be divided by 2 with no remainder
    		
    		assert(rgb % 2 == 0);
    		
    		if (thresholdValue >= (1 << 16)) {
    			
    			shiftBits = rShift % (24 - thresholdCeilingBit);
    			
    			if (thresholdValue == (1 << 16)) {
    			
	    			if (rgb >= thresholdValue) {
	    				
	    				char upperRPart = (char) (r & 0xfe);
	    				char lowerRPart = (char) (r - upperRPart);
	    				
	    				assert(lowerRPart == 0);
	    				
	    				shiftBits = 7;
	    				
	    				upperRPart = circular_shift_certain_bits(upperRPart, 7, shiftBits);
	    				
	    				g = circular_shift_certain_bits(g, 8, gShift);
	    				
	    				assert(b % 2 == 0);
	    				
	    				shiftBits = bShift % 7;
	    				
	    				b = circular_shift_certain_bits(b, 7, shiftBits);
	    				
	    				shiftedColor = upperRPart;
	    				
	    				shiftedColor <<= 8;
	    				shiftedColor += g;
	    				shiftedColor <<= 8;
	    				shiftedColor += b;
	    				
	    			} else {
	    				
	    				r = 0xff;
	    				

	    				g = circular_shift_certain_bits(g, 8, gShift);
	    				
	    				assert(b % 2 == 0);
	    				
	    				shiftBits = bShift % 7;
	    				
	    				b = circular_shift_certain_bits(b, 7, shiftBits);
	    				
	    				
	    				shiftedColor = r;
	    				shiftedColor <<= 8;
	    				shiftedColor += g;
	    				shiftedColor <<=8;
	    				shiftedColor += b;
	    				
	    			}
    			
    			} else {

    				if (rgb >= thresholdValue) {
	    				
	    				char upperRPart = (char) (r & (0xff - (1 << (thresholdCeilingBit - 16)) + 1));
	    				char lowerRPart = (char) (r - upperRPart);
	    				
	    				
	    				upperRPart = circular_shift_certain_bits(upperRPart, 24 - thresholdCeilingBit, shiftBits);
	    				
	    				g = circular_shift_certain_bits(g, 8, gShift);
	    				
	    				assert(b % 2 == 0);
	    				
	    				shiftBits = bShift % 7;
	    				
	    				b = circular_shift_certain_bits(b, 7, shiftBits);
	    				
	    				shiftedColor = upperRPart + lowerRPart;
	    				
	    				shiftedColor <<= 8;
	    				shiftedColor += g;
	    				shiftedColor <<= 8;
	    				shiftedColor += b;
	    				
	    			} else {
	    				
	    				char lowerRPart = (char) (r & ((1 << (thresholdCeilingBit - 16)) - 1 ));
	    				char upperRPart = 0xff;
	    				
	    				upperRPart >>>= (thresholdCeilingBit - 16);
	    				upperRPart <<= (thresholdCeilingBit - 16);
	    				
	    				assert(lowerRPart % 2 == 0);
	    				
	    				r = (char) (upperRPart | lowerRPart | 0x1);
	    				
	    				g = circular_shift_certain_bits(g, 8, gShift);
	    				
	    				assert(b % 2 == 0);
	    				
	    				shiftBits = bShift % 7;
	    				
	    				b = circular_shift_certain_bits(b, 7, shiftBits);
	    				
	    				shiftedColor = r;
	    				shiftedColor <<= 8;
	    				shiftedColor += g;
	    				shiftedColor <<= 8;
	    				shiftedColor += b;
	    				
	    			}
    				
    			}
    			
    		} else if (thresholdValue >= (1 << 8)) {
    			
    			if (r > 0) {
    				
    				assert(r % 2 == 0);
    				
    				shiftBits = rShift % 7;
    				
    				g = circular_shift_certain_bits(g, 8, gShift);
    				
    				assert(b % 2 == 0);
    				
    				shiftBits = bShift % 7;
    				
    				b = circular_shift_certain_bits(b, 7, shiftBits);
    				
    				shiftedColor = r;
    				shiftedColor <<= 8;
    				shiftedColor += g;
    				shiftedColor <<= 8;
    				shiftedColor += b;
    				
    			} else {
    				
    				if (rgb >= thresholdValue) {
    					char upperRPart = (char) (g & (0xff - (1 << (thresholdCeilingBit - 8)) + 1));
	    				char lowerRPart = (char) (g - upperRPart);
	    				
	        			shiftBits = gShift % (16 - thresholdCeilingBit);
	    				
	    				upperRPart = circular_shift_certain_bits(upperRPart, 16 - thresholdCeilingBit, shiftBits);

	    				assert(b % 2 == 0);
	    				
	    				shiftBits = bShift % 7;
	    				
	    				b = circular_shift_certain_bits(b, 7, shiftBits);
	    				
	    				shiftedColor = upperRPart + lowerRPart;
	    				shiftedColor <<= 8;
	    				shiftedColor += b;
    					
    					
    				} else {

	    				char lowerRPart = (char) (g & ((1 << (thresholdCeilingBit - 8)) - 1 ));
	    				char upperRPart = 0xff;
	    				
	    				upperRPart >>>= (thresholdCeilingBit - 8);
	    				upperRPart <<= (thresholdCeilingBit - 8);
	    				
	    				
	    				r = 0x1;
	    				
	    				shiftBits = gShift % (thresholdCeilingBit - 8);

	    				lowerRPart = circular_shift_certain_bits_lower(lowerRPart, thresholdCeilingBit - 8, shiftBits);
	    				
	    				g = (char) (upperRPart | lowerRPart);
	    				
	    				assert(b % 2 == 0);
	    				
	    				shiftBits = bShift % 7;
	    				
	    				b = circular_shift_certain_bits(b, 7, shiftBits);
	    				
	    				shiftedColor = r;
	    				shiftedColor <<= 8;
	    				shiftedColor += g;
	    				shiftedColor <<= 8;
	    				shiftedColor += b;
    					
    				}
    				
    			}
    			
    		} else {
    			
    			if (r > 0) {

    				assert(r % 2 == 0);
    				
    				shiftBits = rShift % 7;
    				
    				r = circular_shift_certain_bits(r, 7, shiftBits);
    				
    				g = circular_shift_certain_bits(g, 8, gShift);
    				
    				assert(b % 2 == 0);
    				
    				shiftBits = bShift % 7;
    				
    				b = circular_shift_certain_bits(b, 7, shiftBits);
    				
    				shiftedColor = r;
    				shiftedColor <<= 8;
    				shiftedColor += g;
    				shiftedColor <<= 8;
    				shiftedColor += b;
    				
    				
    			} else if (g > 0) {
    				
    				g = circular_shift_certain_bits(g, 8, gShift);
    				
    				assert(b % 2 == 0);
    				
    				shiftBits = bShift % 7;
    				
    				b = circular_shift_certain_bits(b, 7, shiftBits);
    				
    				shiftedColor = r;
    				shiftedColor <<= 8;
    				shiftedColor += g;
    				shiftedColor <<= 8;
    				shiftedColor += b;
    				
    				
    			} else {
    				
    				if (rgb >= thresholdValue) {
    					
        				char lowerRPart = (char) (b & ((1 << (thresholdCeilingBit)) - 1 ));
	    				char upperRPart = 0xff;
	    				
	    				upperRPart >>>= (thresholdCeilingBit);
	    				upperRPart <<= (thresholdCeilingBit);
	    				
	    				r = 0x0;
	    				
    					shiftBits = bShift % (8 - thresholdCeilingBit);

    					lowerRPart = circular_shift_certain_bits(lowerRPart, 8 - thresholdCeilingBit, shiftBits);
	    				
	    				b = (char) (upperRPart | lowerRPart);
	    				
	    				assert(b % 2 == 0);
	    				
	    				shiftedColor = r;
	    				shiftedColor <<= 8;
	    				shiftedColor <<= 8;
	    				shiftedColor += b;
    					
    					
    				} else {
    					// Make sure the last one digit is zero
    					char lowerRPart = (char) (b & ((1 << (thresholdCeilingBit)) - 1 ));
	    				char upperRPart = 0xff;
	    				
	    				upperRPart >>>= (thresholdCeilingBit);
	    				upperRPart <<= (thresholdCeilingBit);
	    				
	    				r = 0x1;
	    				
	    				if (thresholdCeilingBit > 1) {
	    				
	    					shiftBits = bShift % (thresholdCeilingBit - 1);

	    					// Take care of the last one digit
	    					lowerRPart = circular_shift_certain_bits_lower_version2(lowerRPart, thresholdCeilingBit - 1, shiftBits);
	    				
	    				}
	    				b = (char) (upperRPart | lowerRPart);
	    				
	    				assert(b % 2 == 0);
	    				
	    				shiftedColor = r;
	    				shiftedColor <<= 8;
	    				shiftedColor <<= 8;
	    				shiftedColor += b;
    					
    				}
    			}
    			
    		}
    		
    		
    		
    	} else {
    		
    		// This is the one that needs more attention
    		// Use the least significant one bit as the sign bit
    		// 2*T - X
    		
    		int value = 2 * threshold - rgb;
    		
    		if (value < 0) {
    			value |= 0x1;
    		}
    		
    		shiftedColor = value & 0xffffff;
    	}
    	
    	return shiftedColor; 
    }
    

	private byte getValueXD0(int src) {
		byte ret = 0;
		
		ret = (byte) (src % 10);
		
		if (ret < 0) {
			ret = (byte) (10 + ret);
		}
		
		return ret;
	}

	private byte getValueXD1(int src) {
		int ret = 0;
		
		ret = src / 10;
		
		if (ret < 0) {
			ret = 10 + ret;
		}
		
		return (byte) ret;
	}
    
	public byte getXD0 (int src, int dest) {
		
		byte ret = 0;
		
		ret = (byte) (((dest - src) % BASE_IMG_QR_BLOCKS) % 10);
		
		if (ret < 0) {
			ret = (byte) (10 + ret);
		}
		
		return ret;
	}

	public byte getXD1 (int src, int dest) {
		int ret = 0;
		
		ret = ((dest - src) % BASE_IMG_QR_BLOCKS) / 10;
		
		if (ret < 0) {
			ret = 10 + ret;
		}
		
		return (byte) ret;
	}

	public byte getYD0 (int src, int dest) {
		int ret = 0;
		
		ret = ((dest - src) / BASE_IMG_QR_BLOCKS) % 10;
		
		if (ret < 0) {
			ret = -ret;
		}
		
		return (byte) ret;
	}

	public byte getYD1 (int src, int dest) {
		int ret = 0;
		
		ret = ((dest - src) / BASE_IMG_QR_BLOCKS) / 10;
		
		if (ret < 0) {
			ret = 10 + ret;
		}
		
		return (byte) ret;
	}
	public void encodeQRImg( String srcFile,
						String destFile) throws IOException{
		
		BufferedImage src = ImageIO.read(new File(srcFile));
		int srcWidth, srcHeight, maxWidth, x, y;
		BufferedImage dest = null, qrImg = null, contentQRImg = null;
		
		srcWidth = src.getWidth();
		srcHeight = src.getHeight();
		
		maxWidth = srcWidth;
		if (maxWidth < srcHeight) {
			maxWidth = srcHeight;
		}
		
		dest = new BufferedImage(maxWidth, maxWidth, BufferedImage.TYPE_INT_RGB);

		QRPosition pos = computeQRCodePosition(srcWidth, srcHeight);

		// Copy the original picture
		for (x = 0; x < srcWidth; x++) {
			for (y = 0; y < srcHeight; y++) {
				
				int rgb = src.getRGB(x, y);
				
				// Set the lowest 1 bit in the rgb value
				rgb &= SPECIAL_MARK_BIT;
				
				assert(rgb % 2 == 0);
				
				src.setRGB(x, y, rgb);
				
				dest.setRGB(x, y, rgb);
				
			}
			
		}
		
		// Move the overlapped area to the enlarged backup area, 
		// If the enlarged backup area cannot hold it, 
		// Strip to other parts of the image, 
		// One possible optimization here is to keep 1/4 - 1/3 of
		// the image in place while moving the remaining pixels
		int overlapArea = computeOverlapArea(srcWidth, srcHeight, pos, maxWidth);
		
		int freeExtendedArea = computeFreeExtArea(srcWidth, srcHeight, pos, maxWidth);
		
		int remainingLines = 0;
		
		if (freeExtendedArea < overlapArea) {
			
			int lostWidth = computeLostWidth(pos, overlapArea - freeExtendedArea);
			
			remainingLines = moveToFreeExtArea(src, dest, srcWidth, srcHeight, maxWidth, pos, lostWidth, overlapArea, freeExtendedArea);
			
			//pos.size -= lostWidth;
			
			distributeLinesFromArea(src, dest, srcWidth, srcHeight, maxWidth, pos, lostWidth, remainingLines);
			
		} else {

			remainingLines = moveToFreeExtArea(src, dest, srcWidth, srcHeight, maxWidth, pos, 0, overlapArea, freeExtendedArea);
			
			if (remainingLines != 0) {
				
				System.err.println("Error in logic");
				
			}
			assert(remainingLines == 0);
			
		}
		
		// Fulfill the other parts of the backup area if any
		if (freeExtendedArea > overlapArea) {
			
			fillRemainingPart(dest, srcWidth, srcHeight, maxWidth, pos, freeExtendedArea - overlapArea);
			
		}
		
		// Choose a 256-bit, 3-bit for each numerical number, total 86 numbers
		// But we have 149 numbers as the budget, so still 63 numbers left
		// These numbers can be used to remember the bytes that have been
		// replaced in the QR image area
		// A minimal of 32 integers, one still has 117 integers
		// how many pixels can be saved here? 
		// 24-bits = 8 integers, 117/8 = 15 pixels
		// It is unbalanced, how to make use the remaining 117 integers? 
		// It can contain the tag information,
		// Or it can contain other auxiliary information that is open to define
		
		// Choose the 8 integers
		String contentCode = getNumbersInString(8);
		
		contentQRImg = generateQRImg(contentCode, maxWidth);
		
		WBRatio contentRatio = computeRatio (contentQRImg);
		
		ThresholdPair tp = computeThresholdRGB (contentRatio, BASE_IMG_QR_BLOCKS, BASE_IMG_QR_BLOCKS, dest);
		
		if (tp.excessiveEqualNumbers > 0) {
			
			adjustRGBForThreshold(dest, BASE_IMG_QR_BLOCKS, BASE_IMG_QR_BLOCKS, tp);
			
		}
		
		computeMutationMap(tp);
		
		assert(checkCells(tp.mutationMap, FIRST_LAYER_PARTITION_NUMBER));
		
		// Jumble the image (1) to generate the mapping information, and 
		// (2) to transform the image, exclude the QR area though
		jumblePrimaryImage(dest, tp);
		
		String primaryKey = "Hello, world";
		
		// Generate the string key
		String encryptedStr = encryptedMapping(tp.mutationMap, contentCode, primaryKey);
		
		encryptedStr = encryptedStr + getNineRightDigit(tp.threshold); 
		
		// Generate the QR Image
		qrImg = generateQRImg(encryptedStr, maxWidth / LINE_RATIO_OF_BIG_OVER_SMALL_QR);
		
		assert(pos.x +  maxWidth / LINE_RATIO_OF_BIG_OVER_SMALL_QR == maxWidth);
		assert(pos.size == maxWidth / LINE_RATIO_OF_BIG_OVER_SMALL_QR);
		
		// Combine the QR Image and the original picture
		for (x = pos.x; x < maxWidth; x++) {
			for (y = 0; y < pos.size; y++) {
				
				int rgb = qrImg.getRGB(x - pos.x, y);
				
				dest.setRGB(x, y, rgb);
			}
		}
		

		ImageIO.write(dest,"JPG",new File(destFile));
	}
	


	/*	Compute the fundamental mapping information: 
	 *	
	 *	First partition the input into 2x5 evenly-sized regions, 
	 * 
	 *  (33x33 - 11x11)/10= 96, this can be relaxed to 97.  
	 *  How to deal with the missing 2? 
	 *  Solution: when the mapping is done, choose one pair to be 96 each. 
	 *  That's said, the region size is determined after the mapping is done. 
	 *  So in total there are 8x97 + 2x96
	 *   
	 *  One needs to define the 10 regions manually after the mapping as
	 *  there are two regions with smaller size
	 *  
	 *  For the internal mapping, 97 mapping in total. But what happens for the 96 
	 *  mapping? So what should be the strategy? 
	 *  So stick with the 96 mapping first, insert the one remaining randomly for 
	 *  each of the 8 regions
	 *  
	 *  So in total there are three categories of mappings:
	 *  (1) the 10 mappings, x1, 
	 *  (2) the 96 mappings, x10, 
	 *  (3) the 8 mapping for the additional 1 slot, x1, and 2 mapping for the excluded slots
	 *  
	 *  So in total there are 114 mappings, each takes 2 digits, so in total 
	 *  228 digits, there are 3035 digits budget, so the number of large regions
	 *  is (3035 - 228) / (96 * 2) = 14, far than enough as in total there are 10 
	 *  mappings in total
	 *  
	 *  There are 4*2*96 digits that are available, which can be used for many 
	 *  other purposes
	 *  
	 *  The resulting full lookup table can be built on-the-fly
	 *  
	 *  [O]tp: the mutationMap is the output
	 * */
	private void computeMutationMap(ThresholdPair tp) {
		
		int i = 0, j = 0;
		
		tp.mutationMap = new byte[FIRST_LAYER_PARTITION_NUMBER + 2 * FIRST_LAYER_PARTITION_NUMBER * SECOND_LAYER_PARTITION_NUMBER
		                         + 2 * (SECOND_LAYER_EXCEPTION_NUMBER + SECOND_LAYER_CHOOSE_NUMBER)];
		
		for (i = 0; i < FIRST_LAYER_PARTITION_NUMBER; i++) {
			
			tp.mutationMap[i] = (byte) i;
		}
		
		for (i = 0; i < FIRST_LAYER_PARTITION_NUMBER; i++) {
			
			for (j = 0; j < 2 * SECOND_LAYER_PARTITION_NUMBER; j += 2) {
			
				/* Little-endian */
				tp.mutationMap[FIRST_LAYER_PARTITION_NUMBER + i * 2 * SECOND_LAYER_PARTITION_NUMBER + j] = getValueXD1(j / 2);
				tp.mutationMap[FIRST_LAYER_PARTITION_NUMBER + i * 2 * SECOND_LAYER_PARTITION_NUMBER + j + 1] = getValueXD0(j / 2);
			
			}
		}
		
		// Random mapping among 1's
        Random rand = new Random();
        int ri;
        
        assert(FIRST_LAYER_PARTITION_NUMBER == SECOND_LAYER_EXCEPTION_NUMBER + SECOND_LAYER_CHOOSE_NUMBER);
        
		for (i = 0; i < SECOND_LAYER_EXCEPTION_NUMBER + SECOND_LAYER_CHOOSE_NUMBER; i ++) {

			while ((ri = rand.nextInt(SECOND_LAYER_PARTITION_NUMBER)) == i );
			
			/* Little-endian */
			tp.mutationMap[FIRST_LAYER_PARTITION_NUMBER + FIRST_LAYER_PARTITION_NUMBER * 2 * SECOND_LAYER_PARTITION_NUMBER + 2 * i] = getValueXD1(ri);
			tp.mutationMap[FIRST_LAYER_PARTITION_NUMBER + FIRST_LAYER_PARTITION_NUMBER * 2 * SECOND_LAYER_PARTITION_NUMBER + 2 * i + 1] = getValueXD0(ri);
			
		}
		
		for (i = SECOND_LAYER_EXCEPTION_NUMBER; i < SECOND_LAYER_CHOOSE_NUMBER + SECOND_LAYER_EXCEPTION_NUMBER; i ++) {

			while ((ri = rand.nextInt(FIRST_LAYER_PARTITION_NUMBER)) == i - SECOND_LAYER_EXCEPTION_NUMBER);
			
			/* Little-endian */
			tp.mutationMap[FIRST_LAYER_PARTITION_NUMBER + FIRST_LAYER_PARTITION_NUMBER * 2 * SECOND_LAYER_PARTITION_NUMBER + 2 * ri] = SECOND_LAYER_PARTITION_NUMBER / 10 + 1;
			tp.mutationMap[FIRST_LAYER_PARTITION_NUMBER + FIRST_LAYER_PARTITION_NUMBER * 2 * SECOND_LAYER_PARTITION_NUMBER + 2 * ri + 1] = SECOND_LAYER_PARTITION_NUMBER % 10;
			
		}
		
		formatPreserveMutation(tp.mutationMap, FIRST_LAYER_PARTITION_NUMBER);
		

		for (i = 0; i < FIRST_LAYER_PARTITION_NUMBER; i++) {
    		for (j = 0; j < SECOND_LAYER_PARTITION_NUMBER; j++) {
                while ((ri = rand.nextInt(SECOND_LAYER_PARTITION_NUMBER)) == j );

                byte tmp = tp.mutationMap[FIRST_LAYER_PARTITION_NUMBER + i * 2 * SECOND_LAYER_PARTITION_NUMBER + 2 * j];
                byte tmp1 = tp.mutationMap[FIRST_LAYER_PARTITION_NUMBER + i * 2 * SECOND_LAYER_PARTITION_NUMBER + 2 * j + 1];
                
                tp.mutationMap[FIRST_LAYER_PARTITION_NUMBER + i * 2 * SECOND_LAYER_PARTITION_NUMBER + 2 * j] 
                		= tp.mutationMap[FIRST_LAYER_PARTITION_NUMBER + i * 2 * SECOND_LAYER_PARTITION_NUMBER + 2 * ri];
                tp.mutationMap[FIRST_LAYER_PARTITION_NUMBER + i * 2 * SECOND_LAYER_PARTITION_NUMBER + 2 * j + 1] 
                		= tp.mutationMap[FIRST_LAYER_PARTITION_NUMBER + i * 2 * SECOND_LAYER_PARTITION_NUMBER + 2 * ri + 1];
                
                tp.mutationMap[FIRST_LAYER_PARTITION_NUMBER + i * 2 * SECOND_LAYER_PARTITION_NUMBER + 2 * ri] = tmp;
                tp.mutationMap[FIRST_LAYER_PARTITION_NUMBER + i * 2 * SECOND_LAYER_PARTITION_NUMBER + 2 * ri + 1] = tmp1;
            }
		} 

		
		int replaceIndex = SECOND_LAYER_PARTITION_NUMBER + 1;
		byte replaceXD0 = getValueXD0(replaceIndex);
		byte replaceXD1 = getValueXD1(replaceIndex);
		
		for (i = 0; i < FIRST_LAYER_PARTITION_NUMBER; i ++) {

			if (tp.mutationMap[FIRST_LAYER_PARTITION_NUMBER + FIRST_LAYER_PARTITION_NUMBER * 2 * SECOND_LAYER_PARTITION_NUMBER + 2 * i] <= SECOND_LAYER_PARTITION_NUMBER / 10) {
			
				ri = tp.mutationMap[FIRST_LAYER_PARTITION_NUMBER + FIRST_LAYER_PARTITION_NUMBER * 2 * SECOND_LAYER_PARTITION_NUMBER + 2 * i];
				ri *= 10;
				ri += tp.mutationMap[FIRST_LAYER_PARTITION_NUMBER + FIRST_LAYER_PARTITION_NUMBER * 2 * SECOND_LAYER_PARTITION_NUMBER + 2 * i + 1];
				
				tp.mutationMap[FIRST_LAYER_PARTITION_NUMBER + FIRST_LAYER_PARTITION_NUMBER * 2 * SECOND_LAYER_PARTITION_NUMBER + 2 * i] = tp.mutationMap[FIRST_LAYER_PARTITION_NUMBER + i * 2 * SECOND_LAYER_PARTITION_NUMBER + 2 * ri];
				tp.mutationMap[FIRST_LAYER_PARTITION_NUMBER + FIRST_LAYER_PARTITION_NUMBER * 2 * SECOND_LAYER_PARTITION_NUMBER + 2 * i + 1] = tp.mutationMap[FIRST_LAYER_PARTITION_NUMBER + i * 2 * SECOND_LAYER_PARTITION_NUMBER + 2 * ri + 1];
				
				tp.mutationMap[FIRST_LAYER_PARTITION_NUMBER + i * 2 * SECOND_LAYER_PARTITION_NUMBER + 2 * ri] = replaceXD1;
				tp.mutationMap[FIRST_LAYER_PARTITION_NUMBER + i * 2 * SECOND_LAYER_PARTITION_NUMBER + 2 * ri + 1] = replaceXD0;
			
				
			}
		}
		
	}
	
	public class HashValueAndIndex implements Comparable<HashValueAndIndex>{
		public HashValueAndIndex(int digest, byte i) {
			// TODO Auto-generated constructor stub
			hashValue = digest;
			originalIndex = i;
		}
		int hashValue;
		byte originalIndex;
		@Override
		public int compareTo(HashValueAndIndex arg0) {
			// TODO Auto-generated method stub
			if (hashValue > arg0.hashValue) {
        		return 1;
        	} else if (hashValue == arg0.hashValue) {
        		return 0;
        	} else {
        		return -1;
        	}
		}

	};

	private void formatPreserveMutation(byte[] mutationMap,
			int size) {
		// TODO Auto-generated method stub
		ArrayList<HashValueAndIndex> hashValue = new ArrayList<HashValueAndIndex>();
		boolean finished = false;
		MessageDigest md = null;
	    
		try {
	        md = MessageDigest.getInstance("SHA-1");
	    }
	    catch(NoSuchAlgorithmException e) {
	        e.printStackTrace();
	    }
		
	    finished = false;
	    Random rm = new Random();
	    
	    while(finished == false) {
	    
	    	hashValue.clear();
	    	
			for(int i = 0; i < size; i++) {
				
				try {
					int seed = rm.nextInt();
					String padded = "deadbeaf" + mutationMap[i] + "deadbeafdead" + seed;
					byte padByte[] = padded.getBytes();
					
					int hv = 0;
					
					md.update(padByte);
					md.digest(padByte, 0, 21);
					
					for (int j = 0; j < 8; j++) {
						
						hv += padByte[j];
						hv <<= 8;
						
					}
					
					hashValue.add(new HashValueAndIndex(hv, 
							mutationMap[i]));
				} catch (DigestException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			Collections.sort(hashValue,
					new Comparator<HashValueAndIndex>(){
                public int compare(HashValueAndIndex s1,HashValueAndIndex s2){
                    // Write your logic here.
                	if (s1.hashValue > s2.hashValue) {
                		return 1;
                	} else if (s1.hashValue == s2.hashValue) {
                		return 0;
                	} else {
                		return -1;
                	}
              }});

			for (int i = 0; i < size; i++) {
				mutationMap[i] = hashValue.get(i).originalIndex;
			}
			
			
			finished = true;
			
			for (int i = 0; i < size; i++) {
				
				if (mutationMap[i] == (byte)i) {
					finished = false;
				}
				
			}
			
		
	    }
		
	}

	private String formatPreserveEncryption (int[] blockMapping, String contentCode,
			String primaryKey) {
		
		byte[] salt = contentCode.getBytes();
		int iterations = 10000;
		SecretKeyFactory factory;
		String ret = null;
		int [] encryptedMapping = new int[blockMapping.length];	
		
		
		SecretKey tmp;
		try {
			factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
			tmp = factory.generateSecret(new PBEKeySpec(primaryKey.toCharArray(), salt, iterations, 128));

			SecretKeySpec key = new SecretKeySpec(tmp.getEncoded(), "AES");
			
			Cipher aes = Cipher.getInstance("AES/ECB/PKCS5Padding");
			aes.init(Cipher.ENCRYPT_MODE, key);
			
			
			
			byte[] ciphertext = aes.doFinal(Arrays.toString(blockMapping).getBytes());
			
			ret = Arrays.toString(encryptedMapping);
			
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeySpecException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalBlockSizeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (BadPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return ret;
	}

	private String encryptedMapping(byte[] mutationMap, String contentCode,
			String primaryKey) {
		// TODO Auto-generated method stub
		String ret = null;
		
		byte[] salt = contentCode.getBytes();
		int iterations = 10000;
		SecretKeyFactory factory;
		
		if (iterations == 10000) {
			ret = new String(); 
			
			int i = 0;
			
			for (i = 0; i < mutationMap.length; i++) {
				
				ret = ret.concat((mutationMap[i]) + "");
				
			}
			
			
			return ret;
		}
		
		SecretKey tmp;
		try {
			factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
			tmp = factory.generateSecret(new PBEKeySpec(primaryKey.toCharArray(), salt, iterations, 128));

			SecretKeySpec key = new SecretKeySpec(tmp.getEncoded(), "AES");
			
			Cipher aes = Cipher.getInstance("AES/ECB/PKCS5Padding");
			aes.init(Cipher.ENCRYPT_MODE, key);
			byte[] ciphertext = aes.doFinal(Arrays.toString(mutationMap).getBytes());
			
			ret = new String(ciphertext);
			
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeySpecException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalBlockSizeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (BadPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		return ret;
	}

	private String decryptMapping(String ciphertext, String contentCode,
			String primaryKey) {
		// TODO Auto-generated method stub
		String ret = null;
		
		byte[] salt = contentCode.getBytes();
		int iterations = 10000;
		SecretKeyFactory factory;
		
			
		
		SecretKey tmp;
		try {
			factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
			tmp = factory.generateSecret(new PBEKeySpec(primaryKey.toCharArray(), salt, iterations, 128));

			SecretKeySpec key = new SecretKeySpec(tmp.getEncoded(), "AES");
			
			Cipher aes = Cipher.getInstance("AES/ECB/PKCS5Padding");
			aes.init(Cipher.DECRYPT_MODE, key);
			String cleartext = new String(aes.doFinal(ciphertext.getBytes()));
			
			ret = cleartext;
			
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeySpecException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeyException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IllegalBlockSizeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (BadPaddingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		return ret;
	}
	
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		if (args.length == 2) {
			try {
				
				int count = 0;
				
				do {

					ScaleImage si = new ScaleImage();
					
					si.encodeQRImg(args[0], args[1]);
					
					count++;
				
				} while (count < 1);
				
				//si.decodeQRImg(args[0], args[1]);
			
			} catch (Exception e) {
				System.out.println(e);
			}
		} else {
			System.out.println("\nUsage: java -jar ScaleImage2.jar srcfile destfile\n");
		}
	}

}
