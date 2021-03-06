package collage;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;

import com.xuggle.xuggler.IPacket;

public class IPacketDatagramOutputStream extends IPacketOutputStream {
  
  private InetAddress destAddress;
  private int destPort;
  private DatagramSocket socket;
  private DatagramPacket oPacket;
  
  private byte[] buf;
  int buf_length;
  
  private long iPacketNum;

  /**
   * Creates an <code>IPacketDatagramOutputStream</code> that will send data to IP
   *   <code>destAddress</code> on port <code>destPort</code>
   * @param destAddress destination IP address
   * @param destPort destination port
   * @throws SocketException if failed to make <code>DatagramSocket</code>
   */
  public IPacketDatagramOutputStream(InetAddress destAddress, int destPort) throws SocketException {
    this.destAddress = destAddress;
    this.destPort = destPort;
    socket = new DatagramSocket();
    
    iPacketNum = 0;
  }

  @Override
  public void close() {
    if (!socket.isClosed())
      socket.close();
  }

  @Override
  public void write(IPacket packet) throws IOException {
    IPacketFlat flat = IPacketFlat.encode(packet);
    ByteBuffer packetBuf = ByteBuffer.wrap(flat.flatData());
    SequenceFrameDatagramPacket frame;
    int iPacketFrameIndex = 0, iPacketNumFrames, iPacketFrameLength;
    iPacketNumFrames = (int) Math.ceil((double)packetBuf.remaining()/(CollageGlobal.DATAGRAM_PACKET_BUFFER_SIZE - SequenceFrameDatagramPacket.HEADER_SIZE));
    while (packetBuf.hasRemaining()) {
      buf_length = Math.min(CollageGlobal.DATAGRAM_PACKET_BUFFER_SIZE - SequenceFrameDatagramPacket.HEADER_SIZE, packetBuf.remaining());
      buf = new byte[buf_length];
      packetBuf.get(buf);
      iPacketFrameLength = buf_length;
      frame = SequenceFrameDatagramPacket.encode(iPacketNum, iPacketFrameIndex, iPacketNumFrames, iPacketFrameLength, buf);
      oPacket = new DatagramPacket(frame.frameBytes(), frame.frameBytes().length, destAddress, destPort);
      socket.send(oPacket);
      iPacketFrameIndex++;
    }
    iPacketNum++;
  }

}
