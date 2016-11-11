package collage;

import java.awt.BorderLayout;
import java.awt.image.BufferedImage;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class FakeDisp {
	  static int count = 0;
	  int id;
	  JFrame frame;
	  JPanel mainPanel;
	  JLabel iconLabel;
	  int x, y, width, height;
	  
	  private String name = "Client " + count;
	  private boolean relative = true;
	  
	  public FakeDisp(int x, int y, int width, int height) {
		  id = count;
		  count++;
		  this.x = x;
		  this.y = y;
		  this.width = width;
		  this.height = height;
		  frame = new JFrame();
		  mainPanel = new JPanel();
		  iconLabel = new JLabel();
		  mainPanel.setLayout(null);
		  mainPanel.add(iconLabel);
		  frame.getContentPane().add(mainPanel, BorderLayout.CENTER);
		  frame.setBounds(x, y, width, height);
		  frame.setTitle(name);
		  frame.setVisible(true);
		  iconLabel.setBounds(0, 0, width, height);
	  }
    public FakeDisp(String name, int x, int y, int width, int height) {
      this(x, y, width, height);
      frame.setTitle(name);
    }

    public String getName() {
      return name;
    }
	  public void setName(String name) {
	    this.name = name;
	    frame.setName(name);
	  }

	  public void setRelative(boolean relative) {
	    this.relative = relative;
	  }
	  public void setUndecorated(boolean undecorated) {
		  frame.setUndecorated(undecorated);
	  }
	  public void setBounds(int width, int height) {
	    this.width = width;
	    this.height = height;
	    frame.setBounds(frame.getX(), frame.getY(), width, height);
	  }
	  public void update(BufferedImage image) {
		  int imgX = 0, imgY = 0;
	    if (relative) {
		    imgX = frame.getX();
	      imgY = frame.getY();
		  }
		  ImageIcon icon = new ImageIcon(image.getSubimage(imgX,imgY,
		      Math.min(image.getWidth(), width),Math.min(image.getHeight(), height)));
		  iconLabel.setIcon(icon);
	  }
    public void updateFull(BufferedImage image) {
      this.width = image.getWidth();
      this.height = image.getHeight();
      frame.setBounds(frame.getX(), frame.getY(), width, height);
      iconLabel.setBounds(0, 0, width, height);
      ImageIcon icon = new ImageIcon(image);
      iconLabel.setIcon(icon);
    }
}
