package collage;

import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IStream;
import com.xuggle.xuggler.IStreamCoder;

public abstract class IPacketCodableInputStream extends IPacketInputStream {

  public abstract int getNumStreams();

  public abstract IStreamCoder getStreamEncoder(int index);
  public abstract IStreamCoder getStreamEncoder(IPacket packet);
  public abstract IStreamCoder getStreamDecoder(int index);
  public abstract IStreamCoder getStreamDecoder(IPacket packet);

  public abstract IStream getStream(int index);
  public abstract IStream getStream(IPacket packet);

}
