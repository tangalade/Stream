package collage;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

import com.xuggle.ferry.IBuffer;
import com.xuggle.xuggler.IPacket;

public class BufferedIPacketDatagramInputStream extends IPacketInputStream {
  private int maxBufferSize = -1;
  private IPacketWorkerThread workerThread = null;

  private int port = 0;
  private DatagramSocket socket;
  private DatagramPacket iPacket;

  private byte[] buf; // buffer used to store the images to send to the client 

  /**
   * Reads <code>DatagramPacket</code> packets into <code>IPacket</code> packets, storing into buffer.
   * If <code>maxPacketBufferSize</code> packets are already in the buffer, the oldest received packet is 
   * discarded to make room for the new.
   * @param port local port number to read input from
   * @param maxPacketBufferSize maximum number of <code>IPacket</code> packets to store in buffer
   * @throws IOException
   */
  public BufferedIPacketDatagramInputStream(int port, int maxPacketBufferSize) throws IOException {
    this.port = port;
    this.maxBufferSize = maxPacketBufferSize;
    this.packets = new LinkedBlockingQueue<IPacket>(maxPacketBufferSize);
    setupIPacketInputStream();
  }
  
  public BufferedIPacketDatagramInputStream(int port) throws IOException {
    this(port, 100);
  }
  
  public BufferedIPacketDatagramInputStream() throws IOException {
    this(CollageGlobal.DEFAULT_RTP_PORT, 100);
  }

	private void setupIPacketInputStream() throws IOException {
    socket = new DatagramSocket(port);
    buf = new byte[CollageGlobal.DATAGRAM_PACKET_BUFFER_SIZE];
  }

  private class IPacketWorkerThread extends Thread {
	  public void run() {
			IPacket packet = IPacket.make();
			IPacketFlat flat;
			SequenceFrameDatagramPacket trans = null;
			byte[][] packetFrames = null;
			ByteBuffer concatFrames;
			long iPacketNum = -1;
			boolean iPacketDone = false;
			int buf_size;
			while (!isDone) {
  			iPacket = new DatagramPacket(buf, buf.length);
        
  			try {
          socket.receive(iPacket);
          trans = SequenceFrameDatagramPacket.decode(iPacket.getData());
          if (iPacketNum != trans.sequenceNum()) {
            packetFrames = null;
            iPacketNum = trans.sequenceNum();
          }
          if (packetFrames == null)
            packetFrames = new byte[trans.numSequenceFrames()][];
          packetFrames[trans.frameIndex()] = trans.frameData();
          // check if all IPacket frames have been seen
          iPacketDone = true;
          for (int i=0; i<packetFrames.length; i++)
            if (packetFrames[i] == null)
              iPacketDone = false;
          if (iPacketDone) {
            buf_size = 0;
            for (int i=0; i<packetFrames.length; i++)
              buf_size += packetFrames[i].length;
            concatFrames = ByteBuffer.allocate(buf_size);
            for (int i=0; i<packetFrames.length; i++)
              concatFrames.put(packetFrames[i]);
            flat = IPacketFlat.decode(concatFrames.array());
            packet = flat.iPacket();
            if (packets.size() == maxBufferSize)
              packets.take();
            packets.put(packet);
            packetFrames = null;
            iPacketNum = -1;
          }
  			} catch (IOException e) {
          e.printStackTrace();
          close();
        } catch (IllegalArgumentException e) {
          e.printStackTrace();
          continue;
        } catch (InterruptedException e) {
          e.printStackTrace();
          close();
        }
			}
			isDoneLoading = true;
		}
	}

  protected void cleanup() {
    try {
      workerThread.join();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    socket.close();
  }
  
  public void start() {
    if (isStarted)
      return;
    isStarted = true;

    workerThread = new IPacketWorkerThread();
    workerThread.start();
  }
  public void close() {
    if (isInterrupted || isDone)
      return;
    isInterrupted = true;
    isDone = true;
    cleanup();
  }
  protected void finish() {
    if (isDone)
      return;
    isDone = true;
    cleanup();
  }

  public int getMaxBufferSize() {
    return maxBufferSize;
  }
  public float getFullness() {
    return packets.size()/maxBufferSize*100;
  }

  public static void main(String[] args) {
    byte[] buf = new byte[10000];
    for (int i=0; i<buf.length; i++)
      buf[i] = (byte)i;
    IPacket oPacket = IPacket.make();
    oPacket.setData(IBuffer.make(null, buf, 0, buf.length));
    
    IPacketDatagramOutputStream oStream;
    BufferedIPacketDatagramInputStream iStream; 
    IPacket iPacket;
    IPacketFlat oFlat, iFlat;
    try {
      oStream = new IPacketDatagramOutputStream(InetAddress.getLocalHost(),CollageGlobal.DEFAULT_RTP_PORT);
      iStream = new BufferedIPacketDatagramInputStream(CollageGlobal.DEFAULT_RTP_PORT);
      iStream.start();

      oStream.write(oPacket);
      System.out.println("Write test passed");
      iPacket = iStream.getNextPacket();
      System.out.println("Read test passed");
      oFlat = IPacketFlat.encode(oPacket);
      iFlat = IPacketFlat.encode(iPacket);
      System.out.println("Comparison test " + (oFlat.equals(iFlat) ? "passed" : "failed"));
    } catch (SocketException e) {
      e.printStackTrace();
    } catch (UnknownHostException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
    
  }
}
