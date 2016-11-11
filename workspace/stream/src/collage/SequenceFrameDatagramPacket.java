package collage;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import com.xuggle.ferry.IBuffer;
import com.xuggle.xuggler.IPacket;

/**
 * @author Administrator
 *
 */
public class SequenceFrameDatagramPacket {
  public static final int HEADER_SIZE = Long.BYTES + (3*Integer.BYTES);
  /**
   * Unique sequence number
   */
  private long sequenceNum;
  /**
   * Index of this frame, relative to other frames in the same sequence
   */
  private int frameIndex;
  /**
   * Total number of frames in this sequence 
   */
  private int numSequenceFrames;
  /**
   * Frame length in bytes
   */
  private int frameLength;
  /**
   * Frame data
   */
  private byte[] frameData;
  /**
   * Encoded frame bytes
   */
  private byte[] frameBytes;
  public static SequenceFrameDatagramPacket encode(long sequenceNum, int frameIndex,
      int numSequenceFrames, int frameLength, byte[] frameData) {
    if (frameData.length != frameLength)
      throw new IllegalArgumentException("frameLength: " + frameLength + " != frameData.length: " + frameData.length);
    if (frameIndex >= numSequenceFrames)
      throw new IllegalArgumentException("frameIndex: " + frameIndex + " >= numSequenceFrames: " + numSequenceFrames);
    
    SequenceFrameDatagramPacket packet = new SequenceFrameDatagramPacket();
    
    packet.sequenceNum = sequenceNum;
    packet.frameIndex = frameIndex;
    packet.numSequenceFrames = numSequenceFrames;
    packet.frameLength = frameLength;
    packet.frameData = frameData.clone();
    
    ByteBuffer buf = ByteBuffer.allocate(HEADER_SIZE + frameLength);
    buf.putLong(sequenceNum);
    buf.putInt(frameIndex);
    buf.putInt(numSequenceFrames);
    buf.putInt(frameLength);
    buf.put(frameData);
    packet.frameBytes = buf.array();
    
    return packet;
  }

  public static SequenceFrameDatagramPacket decode(byte[] frameBytes) throws IllegalArgumentException {
    if (frameBytes.length < HEADER_SIZE)
      throw new IllegalArgumentException("Missing header: packet size < " + HEADER_SIZE);

    SequenceFrameDatagramPacket packet = new SequenceFrameDatagramPacket();
      
    packet.frameBytes = frameBytes.clone();
    
    ByteBuffer buf = ByteBuffer.wrap(frameBytes);
    packet.sequenceNum = buf.getLong();
    packet.frameIndex = buf.getInt();
    packet.numSequenceFrames = buf.getInt();
    packet.frameLength = buf.getInt();
    
    packet.frameData = new byte[packet.frameLength];
    buf.get(packet.frameData);
    
    return packet;
  }
  
  public static void main(String[] args) throws IOException {
    byte[] buf = new byte[10000];
    for (int i=0; i<buf.length; i++)
      buf[i] = (byte)i;
    IPacket packet = IPacket.make();
    packet.setData(IBuffer.make(null, buf, 0, buf.length));
    
    IPacketFlat flat = IPacketFlat.encode(packet);
    SequenceFrameDatagramPacket encoded = null, decoded = null;
    try {
      encoded = SequenceFrameDatagramPacket.encode(1, 0, 1, flat.flatData().length, flat.flatData());
      System.out.println("Encoding test passed");
      decoded = SequenceFrameDatagramPacket.decode(encoded.frameBytes);
      System.out.println("Decoding test passed");
      System.out.println("Comparison test " + (encoded.equals(decoded) ? "passed" : "failed"));
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(0);
    }
  }

  @Override
  public int hashCode() {
    int code = 1;
    code = code * 31 + (int)sequenceNum;
    code = code * 17 + frameIndex;
    code = code * 31 + numSequenceFrames;
    code = code * 17 + frameLength;
    code = code * 31 + Arrays.hashCode(frameData);
    code = code * 17 + Arrays.hashCode(frameBytes);
    return code;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof SequenceFrameDatagramPacket)) return false;
    SequenceFrameDatagramPacket other = (SequenceFrameDatagramPacket)obj;
    if (sequenceNum != other.sequenceNum) return false;
    if (frameIndex != other.frameIndex) return false;
    if (numSequenceFrames != other.numSequenceFrames) return false;
    if (frameLength != other.frameLength) return false;
    if (!Arrays.equals(frameData,other.frameData)) return false;
    if (!Arrays.equals(frameBytes,other.frameBytes)) return false;
    return true;
  }

  public long sequenceNum() {
    return sequenceNum;
  }
  public int frameIndex() {
    return frameIndex;
  }
  public int numSequenceFrames() {
    return numSequenceFrames;
  }
  public int frameLength() {
    return frameLength;
  }
  public byte[] frameData() {
    return frameData;
  }
  public byte[] frameBytes() {
    return frameBytes;
  }
}
