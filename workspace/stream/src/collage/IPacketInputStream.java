package collage;

import java.util.concurrent.BlockingQueue;

import com.xuggle.xuggler.IPacket;

public abstract class IPacketInputStream {

  protected boolean isStarted = false;
  protected boolean isInterrupted = false;
  protected boolean isDone = false;
  protected boolean isDoneLoading = false;

  protected BlockingQueue<IPacket> packets;
  
  /**
   * Start stream, producing IPackets for consumption
   */
  public void start() {
    if (isStarted)
      return;
    isStarted = true;
  }
  
  /**
   * Internal use only
   * Signifies normal completion of stream
   */
  protected void finish() {
    if (isDone)
      return;
    isDone = true;
  }
  
  /**
   * Close the stream, releasing any resources
   * If stream is not already done, performs interrupt
   */
  public void close() {
    if (isInterrupted || isDone)
      return;
    isInterrupted = true;
    isDone = true;
  }
  
  /**
   * @return the next <code>IPacket</code> in queue, or null if none exists  
   */
  public IPacket tryNextPacket() {
    if (isDone)
      return null;
    IPacket ret = packets.poll();
    if (packets.isEmpty() && isDoneLoading)
      finish();
    return ret;
  }
  
  /**
   * @return the next <code>IPacket</code> in queue, waiting as long as necessary,
   *   or null if stream is complete and none exists  
   */
  public IPacket getNextPacket() {
    if (isDone || (packets.isEmpty() && isDoneLoading))
      return null;
    IPacket ret = null;
    try {
      ret = packets.take();
      if (packets.isEmpty() && isDoneLoading)
        finish();
    } catch (InterruptedException e) {
      e.printStackTrace();
      close();
    }
    return ret;
  }

  public boolean isStarted() {
    return isStarted;
  }
  public boolean isInterrupted() {
    return isInterrupted;
  }
  public boolean isDoneLoading() {
    return isDoneLoading;
  }
  public boolean isDone() {
    return isDone;
  }
}
