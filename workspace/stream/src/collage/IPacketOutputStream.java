package collage;

import java.io.IOException;

import com.xuggle.xuggler.IPacket;

public abstract class IPacketOutputStream {
  /**
   * Close the stream, releasing any resources
   */
  public abstract void close();
  
  /**
   * Output <code>packet</code> onto stream
   * @param packet <code>IPacket</code> to output to stream
   * @throws IOException if an I/O error occurs, in particular if the stream is closed
   */
  public abstract void write(IPacket packet) throws IOException;

}
