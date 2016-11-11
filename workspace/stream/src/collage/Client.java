package collage;

import java.io.*;
import java.net.*;
import java.text.ParseException;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

import javax.sound.sampled.LineUnavailableException;
import javax.swing.*;

import collage.RTSPPacket.Method;

import com.xuggle.xuggler.Global;
import com.xuggle.xuggler.IAudioSamples;
import com.xuggle.xuggler.IPacket;
import com.xuggle.xuggler.IStreamCoder;
import com.xuggle.xuggler.IVideoPicture;
import com.xuggle.xuggler.video.ConverterFactory;
import com.xuggle.xuggler.video.IConverter;

public class Client {
  JFrame controlFrame = new JFrame("Control Panel");
  JButton optionsButton = new JButton("Options");
  JButton describeButton = new JButton("Describe");
  JButton setupButton = new JButton("Setup");
  JButton playButton = new JButton("Play");
  JButton pauseButton = new JButton("Pause");
  JButton teardownButton = new JButton("Teardown");
  JPanel controlPanel = new JPanel();
  ImageIcon icon;

  /* RTP variables */
  private int rtpPort = 25000; //port where the client will receive the RTP packets
  private ClientWorkerThread workerThread = null;
  
  /* RTSP variables */
  // RTSP states
  private static enum RTSPState {
    INIT, DESCRIBED, READY, PLAYING, CLOSED
  }
  private RTSPState rtspState;
  private Lock rtspStateLock = new ReentrantLock();
  private Socket rtspSocket;     // socket used to send/receive RTSP messages
  private BufferedReader rtspReader;
  private BufferedWriter rtspWriter;
  private String filename; // video file to request to the server
  private Integer rtspSeqNum = 0;     // Sequence number of RTSP messages within the session
  private Integer rtspSessionNum = null;  // ID of the RTSP session (given by the RTSP Server)

  /* MediaStream variables */
  private IStreamCoder audioCoder = null;
  private IStreamCoder videoCoder = null;
  private Integer audioStreamIndex = null;
  private Integer videoStreamIndex = null;
  
  private BufferedIPacketDatagramInputStream iStream;
  
  private FakeDisp disp;
  private FakeSpeaker speaker;
  
  final static String CRLF = "\r\n";

  /* Player */
  private long mFirstVideoTimestampInStream = Global.NO_PTS;
  private long mSystemVideoClockStartTime = 0;

  public Client(String serverHost, int rtspServerPort) throws IOException {
    //Frame
    controlFrame.addWindowListener(new WindowAdapter() {
       public void windowClosing(WindowEvent e) {
         System.exit(0);
       }
    });

    //Buttons
    controlPanel.setLayout(new GridLayout(2,3));
    controlPanel.add(optionsButton);
    controlPanel.add(describeButton);
    controlPanel.add(setupButton);
    controlPanel.add(playButton);
    controlPanel.add(pauseButton);
    controlPanel.add(teardownButton);
    optionsButton .addActionListener(new OptionsButtonListener());
    describeButton.addActionListener(new DescribeButtonListener());
    setupButton   .addActionListener(new SetupButtonListener());
    playButton    .addActionListener(new PlayButtonListener());
    pauseButton   .addActionListener(new PauseButtonListener());
    teardownButton.addActionListener(new TeardownButtonListener());

    controlPanel.setBounds(0,0,300,150);

    controlFrame.getContentPane().add(controlPanel, BorderLayout.CENTER);
    controlFrame.setSize(new Dimension(300,150));
    controlFrame.setVisible(true);

    // Establish a TCP connection with the server to exchange RTSP messages
    InetAddress ServerIPAddr = InetAddress.getByName(serverHost);
    rtspSocket = new Socket(ServerIPAddr, rtspServerPort);
    rtspState = RTSPState.INIT;

    // Set input and output stream filters:
    rtspReader = new BufferedReader(new InputStreamReader(rtspSocket.getInputStream()) );
    rtspWriter = new BufferedWriter(new OutputStreamWriter(rtspSocket.getOutputStream()) );
    
    rtpPort = CollageGlobal.DEFAULT_RTP_PORT;
  }

  public boolean startStream(String filename) {
    // init RTSP state:
    updateRTSPState(RTSPState.INIT);
    this.filename = filename;
    return true;
  }
  public RTSPState rtspState() {
    return rtspState;
  }
  private boolean waitRTSPState(RTSPState state) {
    synchronized (rtspStateLock) {
      try {
        while (this.rtspState != state) {
          rtspStateLock.wait();
        }
      } catch (InterruptedException e) {
        e.printStackTrace();
        return false;
      }
      return true;
    }
  }
  public void updateRTSPState(RTSPState state) {
    synchronized (rtspStateLock) {
      this.rtspState = state;
      rtspStateLock.notifyAll();
      System.out.println("New RTSP state: " + this.rtspState);
    }
  }
  
  //------------------------------------
  //main
  //------------------------------------
  public static void main(String argv[]) throws Exception {
    if (argv.length < 3)
      throw new RuntimeException("Usage: <rtsp_server_host> <rtsp_server_port> <stream-identifier>");
    
    String serverHost = argv[0];
    int rtspServerPort = Integer.parseInt(argv[1]);
    String filename = argv[2];

    Client client = new Client(serverHost, rtspServerPort);
    client.startStream(filename);
  }


  class OptionsButtonListener implements ActionListener{
    public void actionPerformed(ActionEvent e){
      System.out.println("Options Button pressed !");      
      sendRTSPRequest(RTSPPacket.Method.OPTIONS);
      try {
        RTSPResponsePacket response = getRTSPResponse(Method.OPTIONS);
        System.out.println("Received response: " + System.lineSeparator() + response);
        if (response.errorCode() != RTSPPacket.ERROR_CODE_OK)
          System.err.println("Error in response: " + System.lineSeparator() + response.rawData());
        else
          System.out.println("Received response: " + System.lineSeparator() + response.rawData());
      } catch (SocketException e1) {
        System.err.println("Server unexpectedly closed connection");
        System.exit(0);
      } catch (IOException | IllegalArgumentException | ParseException e1) {
        e1.printStackTrace();
        System.exit(0);
      }
    }
  }
  
  class DescribeButtonListener implements ActionListener{
    public void actionPerformed(ActionEvent e){
      System.out.println("Describe Button pressed !");      
      sendRTSPRequest(RTSPPacket.Method.DESCRIBE);

      try {
        RTSPResponsePacket response = getRTSPResponse(Method.DESCRIBE);
        if (response.errorCode() != RTSPPacket.ERROR_CODE_OK)
          System.err.println("Error in response: " + System.lineSeparator() + response.rawData());
        else if ((response.aStreamIndex() == null || response.audioCoder() == null) &&
            (response.vStreamIndex() == null || response.videoCoder() == null)) {
          System.err.println("Incomplete stream description: " + System.lineSeparator() + response.rawData());
        } else {
          audioStreamIndex = response.aStreamIndex();
          audioCoder = IStreamCoder.make(IStreamCoder.Direction.DECODING,response.audioCoder());
          if (audioCoder.open(null, null) < 0)
            throw new RuntimeException("Unable to open given audio coder");
          videoStreamIndex = response.vStreamIndex();
          videoCoder = IStreamCoder.make(IStreamCoder.Direction.DECODING,response.videoCoder());
          if (videoCoder.open(null, null) < 0)
            throw new RuntimeException("Unable to open given video coder");
          updateRTSPState(RTSPState.DESCRIBED);
        }
      } catch (SocketException e1) {
        System.err.println("Server unexpectedly closed connection");
        System.exit(0);
      } catch (IOException | IllegalArgumentException | ParseException e1) {
        e1.printStackTrace();
        System.exit(0);
      }
    }
  }
  
  class SetupButtonListener implements ActionListener{
    public void actionPerformed(ActionEvent e){
      System.out.println("Setup Button pressed !");      

      if (rtspState == RTSPState.DESCRIBED) {
        rtspSeqNum = 1;
        sendRTSPRequest(RTSPPacket.Method.SETUP);

        try {
          RTSPResponsePacket response = getRTSPResponse(Method.SETUP);
          if (response.errorCode() != RTSPPacket.ERROR_CODE_OK)
            System.err.println("Error in response: " + System.lineSeparator() + response.rawData());
          else if (response.sessionNum() == null) {
            System.err.println("No session number in SETUP response, try again: " + System.lineSeparator() + response.rawData());
          } else {
            iStream = new BufferedIPacketDatagramInputStream(rtpPort);
            workerThread = new ClientWorkerThread();
            workerThread.start();
            updateRTSPState(RTSPState.READY);
            rtspSessionNum = response.sessionNum();
            disp = new FakeDisp("Client", 0, 150, 300, 150);
            disp.setRelative(false);
            speaker = new FakeSpeaker(audioCoder.getSampleRate(),
                (int)IAudioSamples.findSampleBitDepth(audioCoder.getSampleFormat()), audioCoder.getChannels());
          }
        } catch (SocketException e1) {
          System.err.println("Server unexpectedly closed connection");
          System.exit(0);
        } catch (IOException | IllegalArgumentException | ParseException e1) {
          e1.printStackTrace();
          System.exit(0);
        } catch (LineUnavailableException e1) {
          e1.printStackTrace();
          System.exit(0);
        }
      } else {
        System.out.println("DESCRIBE must be called first");
      }
    }
  }
  
  class PlayButtonListener implements ActionListener {
    public void actionPerformed(ActionEvent e){
    	System.out.println("Play Button pressed !"); 

      if (rtspState == RTSPState.READY) {
    	  rtspSeqNum++;
    	  sendRTSPRequest(RTSPPacket.Method.PLAY);

    	  try {
          RTSPResponsePacket response = getRTSPResponse(Method.PLAY);
          if (response.errorCode() != RTSPPacket.ERROR_CODE_OK)
            System.err.println("Error in response: " + System.lineSeparator() + response.rawData());
          else
            updateRTSPState(RTSPState.PLAYING);
        } catch (SocketException e1) {
          System.err.println("Server unexpectedly closed connection");
          System.exit(0);
        } catch (IOException | IllegalArgumentException | ParseException e1) {
          e1.printStackTrace();
          System.exit(0);
        }
      } else {
        System.out.println("SETUP must be called first");
      }
    }
  }

  class PauseButtonListener implements ActionListener {
    public void actionPerformed(ActionEvent e){
      System.out.println("Pause Button pressed !");   

      if (rtspState == RTSPState.PLAYING) {
    	  rtspSeqNum++;
    	  sendRTSPRequest(RTSPPacket.Method.PAUSE);
	
    	  try {
          RTSPResponsePacket response = getRTSPResponse(Method.PAUSE);
          if (response.errorCode() != RTSPPacket.ERROR_CODE_OK)
            System.err.println("Error in response: " + System.lineSeparator() + response.rawData());
          else
            updateRTSPState(RTSPState.READY);
        } catch (SocketException e1) {
          System.err.println("Server unexpectedly closed connection");
          System.exit(0);
        } catch (IOException | IllegalArgumentException | ParseException e1) {
          e1.printStackTrace();
          System.exit(0);
        }
      } else {
        System.out.println("Stream not currently playing");
      }
    }
  }

  class TeardownButtonListener implements ActionListener {
    public void actionPerformed(ActionEvent e){
      System.out.println("Teardown Button pressed !");  

  	  rtspSeqNum++;
      sendRTSPRequest(RTSPPacket.Method.TEARDOWN);

      try {
        RTSPResponsePacket response = getRTSPResponse(Method.TEARDOWN);
        if (response.errorCode() != RTSPPacket.ERROR_CODE_OK)
          System.err.println("Error in response: " + System.lineSeparator() + response.rawData());
        else {     
          updateRTSPState(RTSPState.CLOSED);
          System.exit(0);
        }
      } catch (SocketException e1) {
        System.err.println("Server unexpectedly closed connection");
        System.exit(0);
      } catch (IOException | IllegalArgumentException | ParseException e1) {
        e1.printStackTrace();
        System.exit(0);
      }
    }
  }

  private class ClientWorkerThread extends Thread {
    IVideoPicture picture = null;
    IAudioSamples samples = null;
    public void run() {
      IPacket iPacket; 
      IStreamCoder decoder;
      iStream.start();
      while (rtspState != Client.RTSPState.CLOSED) {
        if (!waitRTSPState(RTSPState.PLAYING))
          continue;
        iPacket = iStream.getNextPacket();
        System.out.println("Received packet: " + iPacket);

        if (iPacket.getStreamIndex() == videoStreamIndex) {
          decoder = videoCoder;
          if (picture == null)
            picture = IVideoPicture.make(decoder.getPixelType(), decoder.getWidth(), decoder.getHeight());
          IConverter converter = ConverterFactory.createConverter(
              ConverterFactory.XUGGLER_BGR_24, decoder.getPixelType(), decoder.getWidth(), decoder.getHeight());

          decoder.decodeVideo(picture, iPacket, 0);
          if (picture.isComplete()) {
            System.out.println("MediaStream decoded video: " + picture);
            BufferedImage nextImg = converter.toImage(picture);
            disp.update(nextImg);
            long delay = millisecondsUntilTimeToDisplay(picture);
            try {
              if (delay > 0)
                Thread.sleep(delay);
            } catch (InterruptedException e) {
              return;
            }
            picture = null;
          }
        } else if (iPacket.getStreamIndex() == audioStreamIndex) {
          decoder = audioCoder;
          if (samples == null)
            samples = IAudioSamples.make(1024, decoder.getChannels());
          int offset = 0;
          while(offset < iPacket.getSize())
          {
            int bytesDecoded = decoder.decodeAudio(samples, iPacket, offset);
            if (bytesDecoded < 0)
              throw new RuntimeException("got error decoding audio in: " + filename);
            System.out.println("MediaStream decoded audio: " + samples);
            offset += bytesDecoded;
            if (samples.isComplete()) {
              speaker.play(samples);
              samples = null;
            }
          }
        }
      }
    }
  }

  private RTSPResponsePacket getRTSPResponse(RTSPPacket.Method reqType) throws IOException, IllegalArgumentException, ParseException {
    RTSPResponsePacket response = RTSPResponsePacket.make(reqType);
    while (!response.isDone())
      response.decode(rtspReader);
    return response;
  }
  private void sendRTSPRequest(RTSPPacket.Method method) {
    RTSPRequestPacket request = null;
    if (rtspState == RTSPState.INIT)
      request = RTSPRequestPacket.encode(method, filename, rtspSeqNum, null,
          RTSPPacket.DEFAULT_TRANSPORT_PROTOCOL, RTSPPacket.DEFAULT_TRANSPORT_MODE, new Integer[] {rtpPort});
    else
      request = RTSPRequestPacket.encode(method, filename, rtspSeqNum, rtspSessionNum,
          RTSPPacket.DEFAULT_TRANSPORT_PROTOCOL, RTSPPacket.DEFAULT_TRANSPORT_MODE, new Integer[] {rtpPort});
    try {
      System.out.println("Sending request: " + System.lineSeparator() + request.rawData());
      rtspWriter.write(request.rawData());
      rtspWriter.flush();
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(0);
    }
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
}
