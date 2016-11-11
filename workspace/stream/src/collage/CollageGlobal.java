package collage;

import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.ICodec;
import com.xuggle.xuggler.IPixelFormat;
import com.xuggle.xuggler.IRational;
import com.xuggle.xuggler.IStreamCoder;

public class CollageGlobal {
  public static final Quality DEFAULT_QUALITY = Quality.MEDIUM;
  
  public static ICodec.ID DEFAULT_VIDEO_CODEC_ID = ICodec.ID.CODEC_ID_H264;
  public static IPixelFormat.Type DEFAULT_VIDEO_PIXEL_FORMAT = IPixelFormat.Type.YUV420P;
  public static IRational DEFAULT_VIDEO_TIME_BASE = IRational.make(1, 30);
  
  public static ICodec.ID DEFAULT_AUDIO_CODEC_ID = ICodec.ID.CODEC_ID_MP3;
  public static Integer DEFAULT_AUDIO_SAMPLE_RATE = new Integer(44100);
  public static IAudioSamples.Format DEFAULT_AUDIO_SAMPLE_FORMAT = IAudioSamples.Format.FMT_S16;
  public static Integer DEFAULT_AUDIO_CHANNELS = 2;

  public static final int DATAGRAM_PACKET_BUFFER_SIZE = 64*1024 - 29;

  public static final String TEST_STREAM_NAME = "Test-Stream";
  public static final String DEFAULT_TEST_FILE = "K:\\Kpop\\[MV] Rania - Dr. Feel Good (Korean Ver.) (GomTV 1080p).avi";
  public static final int DEFAULT_RTSP_PORT = 554;
  public static final int DEFAULT_RTP_PORT = 25000;
  
  public static enum Quality {
    MOBILE (640,  360),
    LOW    (853,  480),
    MEDIUM (1280, 720),
    HIGH   (1920, 1080);
    
    private final int width;
    private final int height;

    Quality(int width, int height) {
      this.width = width;
      this.height = height;
    }
    public int width()  { return width;  }
    public int height() { return height; } 
  }
  
  public static IStreamCoder getDefaultVideoEncoder() {
    return getDefaultVideoEncoder(DEFAULT_QUALITY);
  }
  public static IStreamCoder getDefaultVideoEncoder(Quality quality) {
    IStreamCoder videoEncoder = IStreamCoder.make(IStreamCoder.Direction.ENCODING, ICodec.findEncodingCodec(CollageGlobal.DEFAULT_VIDEO_CODEC_ID));
    if (videoEncoder == null)
      return null;
    videoEncoder.setWidth(quality.width());
    videoEncoder.setHeight(quality.height());
    videoEncoder.setPixelType(CollageGlobal.DEFAULT_VIDEO_PIXEL_FORMAT);
    videoEncoder.setTimeBase(CollageGlobal.DEFAULT_VIDEO_TIME_BASE);
    if (videoEncoder.open(null, null) < 0)
      return null;
    return videoEncoder;
  }
  public static IStreamCoder getDefaultVideoDecoder() {
    return getDefaultVideoDecoder(DEFAULT_QUALITY);
  }
  public static IStreamCoder getDefaultVideoDecoder(Quality quality) {
    IStreamCoder videoDecoder = IStreamCoder.make(IStreamCoder.Direction.ENCODING, ICodec.findEncodingCodec(CollageGlobal.DEFAULT_VIDEO_CODEC_ID));
    if (videoDecoder == null)
      return null;
    videoDecoder.setWidth(quality.width());
    videoDecoder.setHeight(quality.height());
    videoDecoder.setPixelType(CollageGlobal.DEFAULT_VIDEO_PIXEL_FORMAT);
    videoDecoder.setTimeBase(CollageGlobal.DEFAULT_VIDEO_TIME_BASE);
    if (videoDecoder.open(null, null) < 0)
      return null;
    return videoDecoder;
  }
  public static IStreamCoder getDefaultAudioEncoder() {
    return getDefaultAudioEncoder(DEFAULT_QUALITY);
  }
  public static IStreamCoder getDefaultAudioEncoder(Quality quality) {
    IStreamCoder audioEncoder = IStreamCoder.make(IStreamCoder.Direction.ENCODING, ICodec.findEncodingCodec(CollageGlobal.DEFAULT_AUDIO_CODEC_ID));
    if (audioEncoder == null)
      return null;
    audioEncoder.setChannels(DEFAULT_AUDIO_CHANNELS);
    audioEncoder.setSampleRate(DEFAULT_AUDIO_SAMPLE_RATE);
    audioEncoder.setSampleFormat(DEFAULT_AUDIO_SAMPLE_FORMAT);
    if (audioEncoder.open(null, null) < 0)
      return null;
    return audioEncoder;
  }
  public static IStreamCoder getDefaultAudioDecoder() {
    return getDefaultAudioDecoder(DEFAULT_QUALITY);
  }
  public static IStreamCoder getDefaultAudioDecoder(Quality quality) {
    IStreamCoder audioDecoder = IStreamCoder.make(IStreamCoder.Direction.DECODING, ICodec.findEncodingCodec(CollageGlobal.DEFAULT_AUDIO_CODEC_ID));
    if (audioDecoder == null)
      return null;
    audioDecoder.setChannels(DEFAULT_AUDIO_CHANNELS);
    audioDecoder.setSampleRate(DEFAULT_AUDIO_SAMPLE_RATE);
    audioDecoder.setSampleFormat(DEFAULT_AUDIO_SAMPLE_FORMAT);
    if (audioDecoder.open(null, null) < 0)
      return null;
    return audioDecoder;
  }
}
