package collage;

import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.sound.sampled.LineUnavailableException;

import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IStreamCoder;
import com.xuggle.xuggler.IVideoPicture;
import com.xuggle.xuggler.video.ConverterFactory;
import com.xuggle.xuggler.video.IConverter;

import collage.FakeDisp;
import collage.MediaStream;
import collage.RTSPPacket.TransportMode;
import collage.RTSPPacket.TransportProtocol;

public class RTSPServerSession {
  private static enum State {
    INIT, READY, PLAYING, CLOSED
  }

  /* RTSP variables */
  private BufferedWriter rtspWriter = null;
  private State state = null;
  private Lock stateLock = new ReentrantLock();
  private Integer sessionNum = null;
  
  /* RTP variables */
  private InetAddress rtpClientAddress = null; //Client IP address
  private Integer rtpClientPort = -1; //destination port for RTP packets  (given by the RTSP Client)

  private TransportProtocol transportProtocol = null;
  private TransportMode transportMode = null;

  /* Stream variables */
  private MediaStream mediaStream = null;
  private IPacketDatagramOutputStream oStream = null;

  /* Worker threads */
  private RTSPServerSessionWorkerThread workerThread;

  final static String CRLF = "\r\n";

  private IStreamCoder audioCoder = null;
  private IStreamCoder videoCoder = null;

  /* Debug variables */
  private boolean debug = false;
  private FakeDisp disp = null;
  private FakeSpeaker speaker = null;

  public RTSPServerSession(Integer sessionNum, InetAddress clientIPAddress, BufferedWriter rtspWriter, MediaStream mediaStream) throws SocketException {
    this.rtpClientAddress = clientIPAddress;
    this.sessionNum = sessionNum;
    this.rtspWriter = rtspWriter;
    this.mediaStream = mediaStream;
    this.state = State.INIT;
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
    cVideoCoder.setTimeBase(sVideoCoder.getTimeBase());
    if (cVideoCoder.open(null, null) < 0)
      throw new RuntimeException("Failed to open client video coder");
    this.audioCoder = cAudioCoder;
    this.videoCoder = cVideoCoder;
    disp = new FakeDisp("Server", 0, 300, 300, 150);
    disp.setRelative(false);
    try {
      speaker = new FakeSpeaker(audioCoder.getSampleRate(),
          (int)IAudioSamples.findSampleBitDepth(audioCoder.getSampleFormat()), audioCoder.getChannels());
    } catch (LineUnavailableException e) {
      e.printStackTrace();
    }
  }
          
  // TODO: implement
  //  make sure to get client_port in initial SETUP call
  public RTSPResponsePacket handleRTSPRequest(RTSPRequestPacket request) throws IOException {
    RTSPResponsePacket response = null;
    switch (request.reqType()) {
    case SETUP:
      response = genSetupResponse(request);
      break;
    case PLAY:
      response = genPlayResponse(request);
      break;
    case PAUSE:
      response = genPauseResponse(request);
      break;
    default:
      response = genInvalidMethodResponse(request);
      break;
    }
    if (response == null)
      response = genInternalErrorResponse(request);
    return response;
  }
  
  private RTSPResponsePacket genInternalErrorResponse(RTSPRequestPacket request) throws IOException {
    RTSPResponsePacket response = null;
    response = RTSPResponsePacket.encode(request.reqType(), RTSPPacket.ErrorCode.INTERNAL_ERROR,
        request.rtspSeqNum(), null);
    return response;
  }
  private RTSPResponsePacket genInvalidMethodResponse(RTSPRequestPacket request) throws IOException {
    RTSPResponsePacket response = null;
    response = RTSPResponsePacket.encode(request.reqType(), RTSPPacket.ErrorCode.INVALID_METHOD,
        request.rtspSeqNum(), null);
    return response;
  }
  private RTSPResponsePacket genPlayResponse(RTSPRequestPacket request) throws IOException {
    RTSPResponsePacket response = null;
    if (state != State.READY) {
      response = RTSPResponsePacket.encode(request.reqType(), RTSPPacket.ErrorCode.INVALID_METHOD_IN_STATE,
          request.rtspSeqNum(), sessionNum);
    } else {
      updateState(State.PLAYING);
      response = RTSPResponsePacket.encode(request.reqType(), RTSPPacket.ERROR_CODE_OK,
          request.rtspSeqNum(), sessionNum);
    }
    return response;
  }
  private RTSPResponsePacket genPauseResponse(RTSPRequestPacket request) throws IOException {
    RTSPResponsePacket response = null;
    if (state != State.PLAYING) {
      response = RTSPResponsePacket.encode(request.reqType(), RTSPPacket.ErrorCode.INVALID_METHOD_IN_STATE,
          request.rtspSeqNum(), sessionNum);
    } else {
      updateState(State.READY);
      response = RTSPResponsePacket.encode(request.reqType(), RTSPPacket.ERROR_CODE_OK,
          request.rtspSeqNum(), sessionNum);
    }
    return response;
  }
  private RTSPResponsePacket genSetupResponse(RTSPRequestPacket request) throws IOException {
    RTSPResponsePacket response = null;
    if (!request.url().equals(mediaStream.getUniqueId())) {
      response = RTSPResponsePacket.encode(request.reqType(), RTSPPacket.ErrorCode.NOT_FOUND,
          request.rtspSeqNum(), sessionNum);
    } else if (request.clientPorts().length == 0 || request.transportProtocol() == null || request.transportMode() == null) {
      response = RTSPResponsePacket.encode(request.reqType(), RTSPPacket.ErrorCode.BAD_REQUEST,
          request.rtspSeqNum(), sessionNum);
    } else {
      rtpClientPort = request.clientPorts()[0];
      transportProtocol = request.transportProtocol();
      transportMode = request.transportMode();
      oStream = new IPacketDatagramOutputStream(rtpClientAddress, rtpClientPort);
      workerThread = new RTSPServerSessionWorkerThread();
      workerThread.start();
      response = RTSPResponsePacket.encode(RTSPPacket.Method.SETUP, RTSPPacket.ERROR_CODE_OK, request.rtspSeqNum(), sessionNum,
          transportProtocol, transportMode, new Integer[] {rtpClientPort}, null);
      updateState(State.READY);
  }
    return response;
  }

  public State state() {
    return state;
  }
  private boolean waitState(State state) {
    synchronized (stateLock) {
      try {
        while (this.state != state) {
          stateLock.wait();
        }
      } catch (InterruptedException e) {
        e.printStackTrace();
        return false;
      }
      return true;
    }
  }
  private void updateState(State state) {
    synchronized (stateLock) {
      this.state = state;
      stateLock.notifyAll();
    }
  }
  
  public void close() {
    if (workerThread != null)
      workerThread.interrupt();
    if (oStream != null)
      oStream.close();
    updateState(State.CLOSED);
  }

  private class RTSPServerSessionWorkerThread extends Thread {
    public static final int MAX_ATTEMPTS = 3;
    private void play(IPacket packet) {
      IVideoPicture picture = null;
      IAudioSamples samples = null;
      IStreamCoder decoder = null;
      
      if (packet.getStreamIndex() == mediaStream.getVideoStreamId()) {
        decoder = videoCoder;
        if (picture == null)
          picture = IVideoPicture.make(decoder.getPixelType(), decoder.getWidth(), decoder.getHeight());
        IConverter converter = ConverterFactory.createConverter(
            ConverterFactory.XUGGLER_BGR_24, decoder.getPixelType(), decoder.getWidth(), decoder.getHeight());

        decoder.decodeVideo(picture, packet, 0);
        System.out.println("MediaStream decoded video: " + picture);
        if (picture.isComplete()) {
          BufferedImage nextImg = converter.toImage(picture);
          if (disp == null)
            disp = new FakeDisp(0, 0, nextImg.getWidth(), nextImg.getHeight());
          disp.updateFull(nextImg);
//          long delay = mediaStream.millisecondsUntilTimeToDisplay(picture);
//          try {
//            if (delay > 0)
//              Thread.sleep(delay);
//          } catch (InterruptedException e) {
//            return;
//          }
          picture = null;
        }
      } else if (packet.getStreamIndex() == mediaStream.getAudioStreamId()) {
        decoder = audioCoder;
        if (samples == null)
          samples = IAudioSamples.make(1024, decoder.getChannels());
        int offset = 0;
        while(offset < packet.getSize())
        {
          int bytesDecoded = decoder.decodeAudio(samples, packet, offset);
          if (bytesDecoded < 0)
            throw new RuntimeException("got error decoding audio");
          System.out.println("MediaStream decoded audio: " + samples);
          offset += bytesDecoded;
          if (samples.isComplete()) {
            if (speaker == null)
              try {
                speaker = new FakeSpeaker(decoder.getSampleRate(),
                    (int)IAudioSamples.findSampleBitDepth(decoder.getSampleFormat()), decoder.getChannels());
              } catch (LineUnavailableException e) {
                e.printStackTrace();
              }
            speaker.play(samples);
            samples = null;
          }
        }
      }
    }
    public void run() {
      RTSPServerSession parent = RTSPServerSession.this;
      IPacket oPacket = null;
      int attempt = 0;
      while (state() != RTSPServerSession.State.CLOSED) {
        if (!waitState(RTSPServerSession.State.PLAYING))
          continue;
        if (mediaStream.isDone())
          break;
        attempt++;
        oPacket = mediaStream.getNextPacket();
      
        if (debug)
          play(oPacket);
        
        System.out.println("Sending packet: " + oPacket);
        try {
          oStream.write(oPacket);
          attempt = 0;
        } catch (IOException e) {
          System.err.println("Error sending packet, attempting to resend");
          if (attempt > MAX_ATTEMPTS) {
            System.err.println("Error resending packet " + MAX_ATTEMPTS + " times, breaking connection");
            parent.close();
            return;
          };
        }
        if (mediaStream.isDone())
          break;
      }
    }
  }

}