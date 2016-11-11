package collage;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import com.xuggle.xuggler.IAudioSamples;

public class FakeSpeaker {
	  static int count = 0;
	  
	  private String name = "Client " + count;
	  private boolean audible = true;

	  private SourceDataLine mLine;
	  
	  public FakeSpeaker(int sampleRate, int sampleBitDepth, int channels) throws LineUnavailableException {
	    AudioFormat audioFormat = new AudioFormat(sampleRate, sampleBitDepth, channels,
	        true, /* xuggler defaults to signed 16 bit samples */
	        false);
	    DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
	    try {
	      mLine = (SourceDataLine) AudioSystem.getLine(info);
	      mLine.open(audioFormat);
	    } catch (LineUnavailableException e) {
	      throw e;
	    }
	    mLine.start();
	  }
    public FakeSpeaker(String name, int sampleRate, int sampleBitDepth, int channels) throws LineUnavailableException {
      this(sampleRate, sampleBitDepth, channels);
      this.name = name;
    }

    public String getName() {
      return name;
    }
	  public void setName(String name) {
	    this.name = name;
	  }

	  public void setAudible(boolean audible) {
	    this.audible = audible;
	  }
	  
	  public void play(IAudioSamples samples)
	  {
	    if (mLine != null || !audible) {
	      byte[] rawBytes = samples.getData().getByteArray(0, samples.getSize());
	      mLine.write(rawBytes, 0, samples.getSize());
	    }
	  }
	  public void close() {
	    if (mLine != null)
	    {
	      mLine.drain();
	      mLine.close();
	      mLine=null;
	    }
	  }
}
