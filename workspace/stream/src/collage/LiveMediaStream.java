package collage;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.xuggle.xuggler.Global;
import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IRational;
import com.xuggle.xuggler.IStreamCoder;
import com.xuggle.xuggler.IVideoPicture;
import com.xuggle.xuggler.video.ConverterFactory;
import com.xuggle.xuggler.video.IConverter;

public class LiveMediaStream
{
  private Queue<IPacketCodableInputStream> inputStreams = new LinkedList<IPacketCodableInputStream>();
  private Lock streamsLock = new ReentrantLock();
  
  private String uniqueId = "Test-Stream"; 
  private int frameNum = 0;
  private long streamStartTimestamp;
  private long curTimestamp = 0;
  // TODO: implement qualities
  private CollageGlobal.Quality quality = CollageGlobal.Quality.MEDIUM;
  

  private long mFirstVideoTimestampInStream = Global.NO_PTS;
  private long mSystemVideoClockStartTime = 0;
  
  private static FakeDisp serverDisp = null;
  private static FakeSpeaker serverSpeaker = null;
  
  public LiveMediaStream() {
    setup();
  }
  public LiveMediaStream(String uniqueId) {
    this.uniqueId = uniqueId;
    setup();
  }
  public void setup() {
    
  }
  
  public static void main(String[] argv) throws Exception {
	  if (argv.length <= 0)
	      throw new IllegalArgumentException("must pass in a filename as the first argument");
	  
	  String filename = argv[0];
	  LiveMediaStream mediaStream = new LiveMediaStream("Test-Stream", 
	      CollageGlobal.getDefaultAudioEncoder(), CollageGlobal.getDefaultVideoEncoder());
	  IPacketCodableInputStream inputStream = new BufferedIPacketFileInputStream(filename);
    mediaStream.queueStream(inputStream);
    IStreamCoder sAudioCoder = mediaStream.getAudioDecoder();
    IStreamCoder sVideoCoder = mediaStream.getVideoDecoder();

    IStreamCoder cAudioCoder = IStreamCoder.make(IStreamCoder.Direction.DECODING, sAudioCoder.getCodecID());
    IStreamCoder cVideoCoder = IStreamCoder.make(IStreamCoder.Direction.DECODING, sVideoCoder.getCodecID());
    cAudioCoder.setSampleRate(sAudioCoder.getSampleRate());
    cAudioCoder.setChannels(sAudioCoder.getChannels());
    if (cAudioCoder.open(null, null) < 0)
      throw new RuntimeException("Failed to open client audio coder");
    cVideoCoder.setWidth(sVideoCoder.getWidth());
    cVideoCoder.setHeight(sVideoCoder.getHeight());
    cVideoCoder.setPixelType(sVideoCoder.getPixelType());
    cVideoCoder.setTimeBase(IRational.make(sVideoCoder.getTimeBase().getNumerator(), sVideoCoder.getTimeBase().getDenominator()));
    if (cVideoCoder.open(null, null) < 0)
      throw new RuntimeException("Failed to open client video coder");

    RTSPResponsePacket response = RTSPResponsePacket.encode(RTSPPacket.Method.DESCRIBE, RTSPPacket.ERROR_CODE_OK, 
        0, null, mediaStream);
    IStreamCoder encAudioCoder = response.audioCoder();
    IStreamCoder encVideoCoder = response.videoCoder();
    RTSPResponsePacket decResponse = RTSPResponsePacket.make(RTSPPacket.Method.DESCRIBE);
    InputStream is = new ByteArrayInputStream(response.rawData().getBytes());
    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
    while (!decResponse.isDone())
      decResponse.decode(reader);
    IStreamCoder decAudioCoder = IStreamCoder.make(IStreamCoder.Direction.DECODING,decResponse.audioCoder());
    IStreamCoder decVideoCoder = IStreamCoder.make(IStreamCoder.Direction.DECODING,decResponse.videoCoder());
    if (decAudioCoder.open(null, null) < 0)
      throw new RuntimeException("Unable to open given audio coder");
    if (decVideoCoder.open(null, null) < 0)
      throw new RuntimeException("Unable to open given video coder");

    IVideoPicture picture = null;
	  IAudioSamples samples = null;
	  while (!mediaStream.isDone()) {
      IPacket packet = mediaStream.getNextPacket();
      System.out.println("MediaStream read packet: " + packet);
      IStreamCoder decoder = mediaStream.getActiveInputStream().getStreamDecoder(packet);

      if (packet.getStreamIndex() == mediaStream.getVideoStreamId()) {
        decoder = decVideoCoder;
        if (picture == null)
          picture = IVideoPicture.make(decoder.getPixelType(), decoder.getWidth(), decoder.getHeight());
        IConverter converter = ConverterFactory.createConverter(
            ConverterFactory.XUGGLER_BGR_24, decoder.getPixelType(), decoder.getWidth(), decoder.getHeight());

        decoder.decodeVideo(picture, packet, 0);
        System.out.println("MediaStream decoded video: " + picture);
        if (picture.isComplete()) {
          BufferedImage nextImg = converter.toImage(picture);
          if (serverDisp == null)
            serverDisp = new FakeDisp(0, 0, nextImg.getWidth(), nextImg.getHeight());
          serverDisp.updateFull(nextImg);
          long delay = mediaStream.millisecondsUntilTimeToDisplay(picture);
          try {
            if (delay > 0)
              Thread.sleep(delay);
          } catch (InterruptedException e) {
            return;
          }
          picture = null;
        }
      } else if (packet.getStreamIndex() == mediaStream.getAudioStreamId()) {
        decoder = cAudioCoder;
        if (samples == null)
          samples = IAudioSamples.make(1024, decoder.getChannels());
        int offset = 0;
        while(offset < packet.getSize())
        {
          int bytesDecoded = decoder.decodeAudio(samples, packet, offset);
          if (bytesDecoded < 0)
            throw new RuntimeException("got error decoding audio in: " + filename);
          System.out.println("MediaStream decoded audio: " + samples);
          offset += bytesDecoded;
          if (samples.isComplete()) {
            if (serverSpeaker == null)
              serverSpeaker = new FakeSpeaker(decoder.getSampleRate(),
                  (int)IAudioSamples.findSampleBitDepth(decoder.getSampleFormat()), decoder.getChannels());
            serverSpeaker.play(samples);
            samples = null;
          }
        }
      }
      // --------------------
      // Client 
      // --------------------
//      ByteArrayInputStream clientIn = new ByteArrayInputStream(nextPic.byteArray());
//      BufferedImage clientImage = ImageIO.read(clientIn);
//      stream.clientDisp.updateFull(clientImage);
  }
	  
  }

  public boolean isDone() {
    return getActiveInputStream() == null;
  }

  public void queueStream(IPacketCodableInputStream stream) {
    streamsLock.lock();
    inputStreams.add(stream);
    startTopInputStream();
    streamsLock.unlock();
  }
  
  public IPacketCodableInputStream getActiveInputStream() {
    streamsLock.lock();
    IPacketCodableInputStream active = inputStreams.peek();
    while ((active != null) && active.isDone()) {
      inputStreams.remove();
      if (inputStreams.isEmpty()) {
        active = null;
        break;
      }
      active = inputStreams.peek();
      if (active != null)
        startTopInputStream();
    };
    streamsLock.unlock();
    return active;
  }

  private void startTopInputStream() {
    streamsLock.lock();
    IPacketCodableInputStream active = inputStreams.peek();
    if (!active.isStarted()) {
      inAudioStreamId = -1;
      inVideoStreamId = -1;
      for (int i=0; i<active.getNumStreams(); i++) {
        IStreamCoder coder = active.getStreamEncoder(i);
        if ((coder.getCodecType() == ICodec.Type.CODEC_TYPE_AUDIO) && (inAudioStreamId == -1)) {
          inAudioStreamId = i;
        } else if ((coder.getCodecType() == ICodec.Type.CODEC_TYPE_VIDEO) && (inVideoStreamId == -1)) {
          inVideoStreamId = i;
        }
      }
      streamStartTimestamp = System.currentTimeMillis();
      active.start();
    }
    streamsLock.unlock();
  }

  public IPacket getNextPacket() {
    IPacketInputStream stream = getActiveInputStream();
    if (stream == null)
      return null;
    IPacket packet = stream.getNextPacket();
    if (packet == null)
      return null;
    if (packet.getStreamIndex() == inAudioStreamId)
      packet.setStreamIndex(outAudioStreamId);
    else if (packet.getStreamIndex() == inVideoStreamId)
      packet.setStreamIndex(outVideoStreamId);
    return packet;
  }
  
  private long millisecondsUntilTimeToDisplay(IVideoPicture picture)
  {
    long millisecondsToSleep = 0;
    if (mFirstVideoTimestampInStream == Global.NO_PTS)
    {
      // This is our first time through
      mFirstVideoTimestampInStream = picture.getTimeStamp();
      // get the starting clock time so we can hold up frames
      // until the right time.
      mSystemVideoClockStartTime = System.currentTimeMillis();
      millisecondsToSleep = 0;
    } else {
      long systemClockCurrentTime = System.currentTimeMillis();
      long millisecondsClockTimeSinceStartofVideo = systemClockCurrentTime - mSystemVideoClockStartTime;
      // compute how long for this frame since the first frame in the stream.
      // remember that IVideoPicture and IAudioSamples timestamps are always in MICROSECONDS,
      // so we divide by 1000 to get milliseconds.
      long millisecondsStreamTimeSinceStartOfVideo = (picture.getTimeStamp() - mFirstVideoTimestampInStream)/1000;
      final long millisecondsTolerance = 10; // and we give ourselfs 50 ms of tolerance
      millisecondsToSleep = (millisecondsStreamTimeSinceStartOfVideo -
          (millisecondsClockTimeSinceStartofVideo+millisecondsTolerance));
    }
    return millisecondsToSleep;
  }
  
  public int getVideoStreamId() {
    return outVideoStreamId;
  }
  public int getAudioStreamId() {
    return outAudioStreamId;
  }
  public IStreamCoder getAudioDecoder() {
    return getActiveInputStream().getStreamDecoder(inAudioStreamId);
  }
  public IStreamCoder getVideoDecoder() {
    return getActiveInputStream().getStreamDecoder(inVideoStreamId);
  }
  public IStreamCoder getAudioEncoder() {
    return getActiveInputStream().getStreamEncoder(inAudioStreamId);
  }
  public IStreamCoder getVideoEncoder() {
    return getActiveInputStream().getStreamEncoder(inVideoStreamId);
  }
  public String getUniqueId() {
    return uniqueId;
  }
}