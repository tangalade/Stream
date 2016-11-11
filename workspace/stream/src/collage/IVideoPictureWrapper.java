package collage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import com.xuggle.xuggler.IPixelFormat;
import com.xuggle.xuggler.IVideoPicture;
import com.xuggle.xuggler.video.ConverterFactory;
import com.xuggle.xuggler.video.IConverter;

public class IVideoPictureWrapper {
  private byte[] imageData;
  private BufferedImage image;
  private IVideoPicture pic;
  private int width;
  private int height;
  public IVideoPictureWrapper(IVideoPicture pic) {
    this.pic = pic;
    this.width = pic.getWidth();
    this.height = pic.getHeight();
    // And finally, convert the picture to an image and display it
    IConverter converter = ConverterFactory.createConverter(
        ConverterFactory.XUGGLER_BGR_24, IPixelFormat.Type.BGR24, width, height);
    image = converter.toImage(pic);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try {
      ImageIO.write(image, "jpg", baos);
    } catch (IOException e) {
      throw new RuntimeException("Error writing IVideoPicture image to byte array");
    }
    imageData = baos.toByteArray();
  }
  
  public IVideoPictureWrapper(BufferedImage image) {
    this.image = image;
    this.width = image.getWidth();
    this.height = image.getHeight();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try {
      ImageIO.write(image, "jpg", baos);
    } catch (IOException e) {
      throw new RuntimeException("Error writing IVideoPicture image to byte array");
    }
    imageData = baos.toByteArray();
  }

  public byte[] byteArray() {
    return imageData;
  }
  public BufferedImage bufferedImage() {
    return image;
  }
  public IVideoPicture iVideoPicture() {
    return pic;
  }
}