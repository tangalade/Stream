package collage;
import java.util.concurrent.LinkedBlockingQueue;

import com.xuggle.xuggler.IContainer;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IStream;
import com.xuggle.xuggler.IStreamCoder;

public class BufferedIPacketFileInputStream extends IPacketCodableInputStream {
	private int maxBufferSize = -1;
  private IPacketWorkerThread workerThread = null;

  private String filename = "Name me";

  private IStream[] streams = null;
  private IStreamCoder[] decoders = null;
  private IStreamCoder[] encoders = null;

  private IContainer container = null;

  public BufferedIPacketFileInputStream(String filename, int maxPacketBufferSize) {
    this.filename = filename;
    this.maxBufferSize = maxPacketBufferSize;
    this.packets = new LinkedBlockingQueue<IPacket>(maxPacketBufferSize);
    setupInputPacketStream();
  }
  
  public BufferedIPacketFileInputStream(String filename) {
    this(filename, 100);
  }

	private void setupInputPacketStream() {
    // Create a Xuggler container object
    container = IContainer.make();

    // Open up the container
    if (container.open(filename, IContainer.Type.READ, null) < 0)
      throw new IllegalArgumentException("could not open file: " + filename);
    
    // query how many streams the call to open found
    int numStreams = container.getNumStreams();
    if (numStreams <= 0)
      throw new RuntimeException("No streams in input file: " + filename);
    
    streams = new IStream[numStreams];
    decoders = new IStreamCoder[numStreams];
    encoders = new IStreamCoder[numStreams];
    
    // and iterate through the streams to find the first audio stream
    for(int i = 0; i < numStreams; i++) {
      IStream stream = container.getStream(i);
      IStreamCoder decoder = stream.getStreamCoder();
      IStreamCoder encoder = IStreamCoder.make(IStreamCoder.Direction.ENCODING, decoder);
      if (encoder.open(null, null) < 0)
        System.err.println("Error opening encoder");
      
      streams[i] = stream;
      decoders[i] = decoder;
      encoders[i] = encoder; 
    }
  }

  private class IPacketWorkerThread extends Thread {
	  public void run() {
			IPacket packet = IPacket.make();

			while(container.readNextPacket(packet) >= 0) {
			  try {
			    packets.put(packet);
			  } catch (InterruptedException e) {
          throw new RuntimeException("interrupted while adding new packet to queue");
			  }
			  packet = IPacket.make();
			}
			finish();
			isDoneLoading = true;
		}
	}

	protected void cleanup() {
    if (container != null)
      container.close();
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

    workerThread.interrupt();
    cleanup();
  }
  protected void finish() {
    if (isDone)
      return;
    isDone = true;
    cleanup();
  }
  
  public IContainer getContainer() {
    return container;
  }
  
  public int getMaxBufferSize() {
    return maxBufferSize;
  }
  public float getFullness() {
    return packets.size()/maxBufferSize*100;
  }
  
  public IStreamCoder getStreamDecoder(int index) {
    return decoders[index];
  }
  public IStreamCoder getStreamDecoder(IPacket packet) {
    return decoders[packet.getStreamIndex()];
  }
  public IStreamCoder getStreamEncoder(int index) {
    return encoders[index];
  }
  public IStreamCoder getStreamEncoder(IPacket packet) {
    return encoders[packet.getStreamIndex()];
  }

  public int getNumStreams() {
    return streams.length;
  }
  public IStream getStream(int index) {
    return streams[index];
  }
  public IStream getStream(IPacket packet) {
    return streams[packet.getStreamIndex()];
  }
}
