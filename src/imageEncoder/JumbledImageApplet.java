package imageEncoder;
/*
 * Copyright (c) 1995, 2008, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle or the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */ 


import java.io.*;
import java.net.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;

import javax.imageio.*;
import javax.swing.*;

import java.util.Random;

@SuppressWarnings("serial")
class JumbledImage extends Component {

	private final int RGB_VALUE_MASK = (1<<24) - 1;
	private final int SINGLE_CHANNEL_MASK = 0xff;
	private final int SPECIAL_MARK_BIT = 0xfffefffe;
	
    private int numlocs = 33;
    private int numcells = numlocs*numlocs;
    private int[] cells;
    private int[] coef;
    private BufferedImage bi;
    int w, h, cw, ch;
    int drawMode = 0;
    
    
    public JumbledImage(URL imageSrc) {
        try {
            bi = ImageIO.read(imageSrc);
          
            w = bi.getWidth(null);
            h = bi.getHeight(null);
            System.out.println("Width: " + w + ", height: " + h);
        } catch (IOException e) {
            System.out.println("Image could not be read");
//            System.exit(1);
        }
        cw = w/numlocs;
        ch = h/numlocs;
        cells = new int[numcells];
        coef = new int[numcells];
        for (int i=0;i<numcells;i++) {
            cells[i] = i;
            coef[i] = 0;
        }
    }

    public void changeContent(int src, int dst) {
    	
    	int dx, dy, width, height, x, y;
    	char rDelta = 0, gDelta = 0, bDelta = 0;    	
    	int rShift = 0, gShift = 0, bShift = 0;
    	
    	dx = ((dst - src)%numlocs + numlocs) % numlocs;
    	dy = ((dst - src)/numlocs + numlocs) % numlocs;

		rShift = ((dx  + dy) % 8);
		gShift = ((dx * dx + dy * dy) % 8);
		bShift = ((dx * dx * dx + dy * dy * dy) % 8);

		rDelta = (char) ((dx  + dy) % 256);
		gDelta = (char) ((dx * dx + dy * dy) % 256);
		bDelta = (char) ((dx * dx * dx + dy * dy * dy) % 256);
    	
    	width = w/numlocs;
    	height = h/numlocs;
    	
    	x = (src%numlocs) * width;
    	y = (src/numlocs) * height;
    	
    	BufferedImage subImg = bi.getSubimage(x, y, width, height);
    	
    	
    	for (x = 0; x < width; x++) {
    		
    		for (y = 0; y < height; y++) {
    			
    			int rgb = subImg.getRGB(x, y) ;
    			
    			int alpha = rgb - (rgb & RGB_VALUE_MASK);
    			
    			char r = (char) ((rgb >>> 16) & SINGLE_CHANNEL_MASK);
    			char g = (char) ((rgb >>> 8) & SINGLE_CHANNEL_MASK);
    			char b = (char) (rgb & SINGLE_CHANNEL_MASK);
    			
    			r = circular_shift(r, rShift);
    			g = circular_shift(g, gShift);
    			b = circular_shift(b, bShift);
    			
    			r = (char) ((r + rDelta) & SINGLE_CHANNEL_MASK);
    			g = (char) ((g + gDelta) & SINGLE_CHANNEL_MASK);
    			b = (char) ((b + bDelta) & SINGLE_CHANNEL_MASK);
    			
    			int trgt_rgb;
    			
    			
    			trgt_rgb = alpha;
    			trgt_rgb <<= 8;
    			trgt_rgb |= r;
    			trgt_rgb <<= 8;
    			trgt_rgb |= g;
    			trgt_rgb <<= 8;
    			trgt_rgb |= b;

    			/*if (src < numlocs) {
    				System.out.println("For " + src + "," + x + "," + y + ": [" + Integer.toHexString(rgb) 
    						+ "," + Integer.toHexString(trgt_rgb) + "]" + ", rshift " + rShift + ", gshift " + gShift
    						+ ", bShift " + bShift + ", dx " + dx + ", dy " + dy + ", src " + src + ", dst " + dst);
    			}*/
    			
    			/*	How to deal with different aspect ratios? 
    			 *   (1) Recursive? 
    			 *   (2) First make it rectangle by putting the QR code on the side
    			 * */
    				
    			subImg.setRGB(x, y, trgt_rgb );
    		}
    		
    	}
    	
    	
    }
    
    public int convertYCC(int rgb) {
    	int ycc = 0;
    	
    	int R = (rgb >> 16) & 0xff;
    	int G = (rgb >> 8) & 0xff;
    	int B = rgb & 0xff;
    	
    	int Y = ( (  66 * R + 129 * G +  25 * B + 128) >> 8) +  16;
    	int U = ( ( -38 * R -  74 * G + 112 * B + 128) >> 8) + 128;
    	int V = ( ( 112 * R -  94 * G -  18 * B + 128) >> 8) + 128;
    	
    	ycc = (Y << 16) + (U << 8) + V;
    	
    	return ycc;
    }
    
    public int convertRGB(int ycc) {
    	int rgb = 0;
    	

    	int Y = (ycc >> 16) & 0xff;
    	int U = (ycc >> 8) & 0xff;
    	int V = ycc & 0xff;
    	
    	int C = Y - 16;
    	int D = U - 128;
    	int E = V - 128;
    	
    	int R = (( 298 * C           + 409 * E + 128) >> 8) & 0xff;
    	int G = (( 298 * C - 100 * D - 208 * E + 128) >> 8) & 0xff;
    	int B = (( 298 * C + 516 * D           + 128) >> 8) & 0xff;
    	
    	rgb = (R << 16) + (G << 8) + B;
    	
    	return rgb;
    	
    }
    
    public char circular_shift(char v, int shift) {
    	
    	return (char) (((v>>>shift) | (v<<(8 - shift)) ) & SINGLE_CHANNEL_MASK);
    	
    }
    
    public void restoreContent(int src, int dst) {
    	
    	int dx, dy, width, height, x, y;
    	int rShift = 0, gShift = 0, bShift = 0;
    	char rDelta = 0, gDelta = 0, bDelta = 0;
    	int maxValue = SINGLE_CHANNEL_MASK + 1;
    	
    	dx = ((dst - src)%numlocs + numlocs) % numlocs;
    	dy = ((dst - src)/numlocs + numlocs) % numlocs;

		rShift = 8 - ((dx  + dy) % 8);
		gShift = 8 - ((dx * dx + dy * dy) % 8);
		bShift = 8 - ((dx * dx * dx + dy * dy * dy) % 8);

		rDelta = (char) ((dx  + dy) % 256);
		gDelta = (char) ((dx * dx + dy * dy) % 256);
		bDelta = (char) ((dx * dx * dx + dy * dy * dy) % 256);
		
		
    	width = w/numlocs;
    	height = h/numlocs;
    	
    	x = (src%numlocs) * width;
    	y = (src/numlocs) * height;
    	
    	BufferedImage subImg = bi.getSubimage(x, y, width, height);
    	
    	for (x = 0; x < width; x++) {
    		
    		for (y = 0; y < height; y++) {
    			
    			int rgb = subImg.getRGB(x, y);
    			int alpha = rgb - (rgb & RGB_VALUE_MASK);
    			char r = (char) ((rgb >>> 16) & SINGLE_CHANNEL_MASK);
    			char g = (char) ((rgb >>> 8) & SINGLE_CHANNEL_MASK);
    			char b = (char) (rgb & SINGLE_CHANNEL_MASK);
    			
    			if(r > rDelta) {
    				
    				r -= rDelta;
    				
    			} else {
    				r = (char) ((r + maxValue - rDelta) & SINGLE_CHANNEL_MASK);
    				
    			}
    		
    			if(g > gDelta) {
    				
    				g -= gDelta;
    				
    			} else {
    				g = (char) ((g + maxValue - gDelta) & SINGLE_CHANNEL_MASK);
    				
    			}
    		

    			if(b > bDelta) {
    				
    				b -= bDelta;
    				
    			} else {
    				b = (char) ((b + maxValue - bDelta) & SINGLE_CHANNEL_MASK);
    				
    			}
    			
    			r = circular_shift(r, rShift);
    			g = circular_shift(g, gShift);
    			b = circular_shift(b, bShift);
    			
    			int trgt_rgb = alpha;
    			
    			trgt_rgb <<= 8;
    			trgt_rgb |= r;
    			trgt_rgb <<= 8;
    			trgt_rgb |= g;
    			trgt_rgb <<= 8;
    			trgt_rgb |= b;
    			
    			
    			/*if (src < numlocs) {
    				System.out.println("2, For " + src + "," + x + "," + y + ": [" + Integer.toHexString(rgb) 
    						+ "," + Integer.toHexString(trgt_rgb) + "]" + ", rshift " + rShift + ", gshift " + gShift
    						+ ", bShift " + bShift + ", dx " + dx + ", dy " + dy + ", src " + src + ", dst " + dst);
    			}*/
    			
    			//System.out.println("2, For " + src + ": [" + rgb + "," + trgt_rgb + "]");
    			
    			subImg.setRGB(x, y, trgt_rgb);
    		}
    		
    	}
    	
    	
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
    
    void jumble() {
        Random rand = new Random();
        int ri;
        for (int i=0; i<numcells; i++) {
            while ((ri = rand.nextInt(numlocs)) == i || zeroDistance(i, ri));

            int tmp = cells[i];
            cells[i] = cells[ri];
            cells[ri] = tmp;

        
        }
        
        
        for (int i=0; i<numcells; i++) {
            
        	changeContent(i, cells[i]);
        
        }
        drawMode = 0;
    }
    
    void restore() {
        for (int i=0; i<numcells; i++) {
        	
            restoreContent(i, cells[i]);
             
        }
        
        drawMode = 1;
    }
    

    public void swapRegion(int dx, int dy, int dx_end, int dy_end,
            int sx, int sy, int sx_end, int sy_end)
    {
    	
    	if(dx == sx && dy == sy) return;
    	
    	BufferedImage srcImg = bi.getSubimage(sx, sy, sx_end - sx, sy_end - sy);
    	
    	BufferedImage dstImg = bi.getSubimage(dx, dy, dx_end - dx, dy_end - dy);
    	
    	
    	int x, y;
    	
    	for(x = dx; x < dx_end; x++) {
    		for(y = dy; y < dy_end; y++) {
    			
    			int tmpRgb = dstImg.getRGB(x - dx, y - dy);
    		
    			dstImg.setRGB(x - dx, y - dy, srcImg.getRGB(x - dx, y - dy));
    			
    			srcImg.setRGB(x - dx, y - dy, tmpRgb);
    			
    		}
    		
    	}
    	
    	
    	
    }

    public Dimension getPreferredSize() {

        System.out.println("2, Width: " + w + ", height: " + h);
        return new Dimension(w, h);
    }

    public void paint(Graphics g) {

    	if (drawMode == 0) {
	        int dx, dy;
	        for (int x=0; x<numlocs; x++) {
	            int sx = x*cw;
	            for (int y=0; y<numlocs; y++) {
	                int sy = y*ch;
	                int cell = cells[x*numlocs+y];
	                dx = (cell / numlocs) * cw;
	                dy = (cell % numlocs) * ch;
	                
	                g.drawImage(bi,
	                            dx, dy, dx+cw, dy+ch,
                            	//sx, sy, sx+cw, sy+ch,
	                            sx, sy, sx+cw, sy+ch,
	                            null);
	               
	                /*swapRegion(dx, dy, dx+cw, dy+ch,
                            sx, sy, sx+cw, sy+ch);*/
	                
	            }
	        }
    	} else {
    		
    		int reverse[] = new int[cells.length];
    		
    		for(int x = 0; x < cells.length; x++) {
    			reverse[cells[x]] = x;
    			//reverse[x] = x;
    		}
    		
    		
	        int dx, dy, sx, sy;
	        for (int x=0; x<numlocs; x++) {
	            sx = x*cw;
	            for (int y=0; y<numlocs; y++) {
	                sy = y*ch;
	                int cell = reverse[x*numlocs+y];
	                dx = (cell / numlocs) * cw;
	                dy = (cell % numlocs) * ch;
	                
	                g.drawImage(bi,
	                            dx, dy, dx+cw, dy+ch,
	                            sx, sy, sx+cw, sy+ch,
	                            null);
	                
	                /*swapRegion(dx, dy, dx+cw, dy+ch,
                            sx, sy, sx+cw, sy+ch);*/
	            }
	        }
	        
	        for(int x = 0; x < numlocs*numlocs; x++) {
	        	cells[x] = x;
	        }
	        
    		
    		
    	}
    }
}

@SuppressWarnings("serial")
public class JumbledImageApplet extends JApplet {

    static String imageFileName = "C:\\files\\test.jpg";
    private URL imageSrc;
    
    public JumbledImageApplet () {
    }

    public JumbledImageApplet (URL imageSrc) {
        this.imageSrc = imageSrc;
    }

    public void init() {
        try {
            imageSrc = new URL(getCodeBase(), imageFileName);
        } catch (MalformedURLException e) {
        }
        buildUI();
    }
     
    public void buildUI() {
        final JumbledImage ji = new JumbledImage(imageSrc);
        add("Center", ji);
        final JButton jumbleButton = new JButton("Jumble");
        JButton restoreButton = new JButton("Restore");
        jumbleButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                  
                    ji.jumble();
                    
                    ji.repaint();
                };
        });
        
        restoreButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
              
                ji.restore();
                
                ji.repaint();
            };
        });
        
        Dimension jumbleSize = ji.getPreferredSize();
        resize(jumbleSize.width, jumbleSize.height + 80);
        add("South", jumbleButton);
        add("North", restoreButton);
    }

    public void nonLinearChange()
    {
    	// In the YUV space, change the Y(i) value in a non-linear fashion
    	// That is, put a threshold cap on it, that will be a lot of data
    	// to protect
    	// Instead, one should use an known technique, 
    	// So now the problem is how to encrypt it properly
    	// The point of using (Y, U, V) is to only focus on Y as U and V carry little information
    	// The shuffling encryption can also be used to encrypt the value
    	// That is, for a*x + b, a is X_diff, b is Y_diff
    	
    	// The question is where to store the sign of the Y value, as a*x + b would 
    	// incur an inaccuracy problem of what to put in the shifted bits
    	
    	// So how to store these shifted bits? 
    	// Each encryption block also contains the co-efficients, these
    	// co-efficients are determined such that there is no overflow for 
    	// each Y(i) value, but it is hard to do so
    	// how much to overflow is a question
    	
    	// Addition seems to work with the idea, 
    	// but what should be done for the multiplication? Will power-2 work? 
    	// A simple mechanism may be used, that is, a=2 if no overflow; a=1 if overflows. 
    	// A bit is used to indicate if a=2 or not
    	// The introduction of this step function will make the image more interesting
    	
    	
    	
    }
    
    public static void main(String s[]) {
        JFrame f = new JFrame("Jumbled Image");
        f.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {System.exit(0);}
        });
        URL imageSrc = null;
        try {
             imageSrc = ((new File(imageFileName)).toURI()).toURL();
        } catch (MalformedURLException e) {
        }
        JumbledImageApplet jumbler = new JumbledImageApplet(imageSrc);
        jumbler.buildUI();
        f.add("Center", jumbler);
        f.pack();
        f.setVisible(true);
    }
}
