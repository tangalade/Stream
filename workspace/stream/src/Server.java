


import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.awt.event.*;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;

import collage.FakeDisp;
import collage.IVideoPictureWrapper;
import collage.MediaStream;
import collage.RTPpacket;

public class Server extends JFrame implements ActionListener {
  private static final long serialVersionUID = 3152642026560550486L;

  //RTP variables:
  //----------------
  DatagramSocket RTPsocket; //socket to be used to send and receive UDP packets
  DatagramPacket senddp; //UDP packet containing the video frames

  InetAddress ClientIPAddr; //Client IP address
  int RTP_dest_port = 0; //destination port for RTP packets  (given by the RTSP Client)

  //Video variables:
  //----------------
  int imagenb = 0; //image nb of the image currently transmitted
  MediaStream liveStream; //VideoStream object used to access video frames
  static int JPEG_TYPE = 26; //RTP payload type for JPEG type
  static int FRAME_PERIOD = 100; //Frame period of the video to stream, in ms
  static int VIDEO_LENGTH = 500; //length of the video in frames

  Timer timer; //timer used to send the images at the video frame rate
  byte[] buf; //buffer used to store the images to send to the client 

  //RTSP variables
  //----------------
  //rtsp states
  final static int INIT = 0;
  final static int READY = 1;
  final static int PLAYING = 2;
  //rtsp message types
  final static int SETUP = 3;
  final static int PLAY = 4;
  final static int PAUSE = 5;
  final static int TEARDOWN = 6;

  static int state; //RTSP Server state == INIT or READY or PLAY
  Socket RTSPsocket; //socket used to send/receive RTSP messages
  //input and output stream filters
  static BufferedReader RTSPBufferedReader;
  static BufferedWriter RTSPBufferedWriter;
  static String VideoFileName; //video file requested from the client
  static int RTSP_ID = 123456; //ID of the RTSP session
  int RTSPSeqNb = 0; //Sequence number of RTSP messages within the session
  
  final static String CRLF = "\r\n";

  FakeDisp disp = new FakeDisp(0,0,500,500);

  public Server(){

    //init Frame
    super("Server");

    //init Timer
    timer = new Timer(FRAME_PERIOD, this);
    timer.setInitialDelay(0);
    timer.setCoalesce(true);

    //allocate memory for the sending buffer
    buf = new byte[15000]; 

    //Handler to close the main window
    addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
	//stop the timer and exit
	timer.stop();
	System.exit(0);
      }});

  }
          
  //------------------------------------
  //main
  //------------------------------------
  public static void main(String argv[]) throws Exception
  {
	  //create a Server object
    Server theServer = new Server();
    //show GUI:
    theServer.pack();
    theServer.setVisible(true);

    //get RTSP socket port from the command line
    int RTSPport = Integer.parseInt(argv[0]);
   
    //Initiate TCP connection with the client for the RTSP session
    ServerSocket listenSocket = new ServerSocket(RTSPport);
    theServer.RTSPsocket = listenSocket.accept();
    listenSocket.close();

    //Get Client IP address
    theServer.ClientIPAddr = theServer.RTSPsocket.getInetAddress();

    //Initiate RTSPstate
    state = INIT;

    //Set input and output stream filters:
    RTSPBufferedReader = new BufferedReader(new InputStreamReader(theServer.RTSPsocket.getInputStream()) );
    RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(theServer.RTSPsocket.getOutputStream()) );

    //Wait for the SETUP message from the client
    int request_type;
    boolean done = false;
    while(!done)
    {
      request_type = theServer.parse_RTSP_request(); //blocking
      
      if (request_type == SETUP)
      {
        done = true;

        //update RTSP state
        state = READY;
        System.out.println("New RTSP state: READY");
   
        //Send response
        theServer.send_RTSP_response();
   
  	    //init the VideoStream object:
  	    theServer.liveStream = new MediaStream();
  
  	    //init RTP socket
  	    theServer.RTPsocket = new DatagramSocket();
  	  }
    }

    //loop to handle RTSP requests
    while(true)
    {
    	//parse the request
    	request_type = theServer.parse_RTSP_request(); //blocking
    	    
    	if ((request_type == PLAY) && (state == READY))
  	  {
  	    //send back response
  	    theServer.send_RTSP_response();
  	    //start timer
  	    theServer.timer.start();
  	    //update state
  	    state = PLAYING;
  	    System.out.println("New RTSP state: PLAYING");
  	  }
    	else if ((request_type == PAUSE) && (state == PLAYING))
    	{
  	    //send back response
  	    theServer.send_RTSP_response();
  	    //stop timer
  	    theServer.timer.stop();
  	    //update state
  	    state = READY;
  	    System.out.println("New RTSP state: READY");
  	  }
    	else if (request_type == TEARDOWN)
  	  {
  	    //send back response
  	    theServer.send_RTSP_response();
  	    //stop timer
  	    theServer.timer.stop();
  	    //close sockets
  	    theServer.RTSPsocket.close();
  	    theServer.RTPsocket.close();
  
  	    System.exit(0);
  	  }
    }
  }


  //------------------------
  //Handler for timer
  //------------------------
  public void actionPerformed(ActionEvent e) {
    //update current imagenb
    imagenb++;
     
  	try {
  	  //get next frame to send from the video, as well as its size
  	  IVideoPictureWrapper frame = null;
  
  	  //Builds an RTPpacket object containing the frame
  	  RTPpacket rtp_packet = new RTPpacket(JPEG_TYPE, imagenb, (int)frame.iVideoPicture().getTimeStamp(), frame.byteArray(), frame.byteArray().length);
  	  
  	  //get to total length of the full rtp packet to send
  	  int packet_length = rtp_packet.getlength();
  
  	  //retrieve the packet bitstream and store it in an array of bytes
  	  byte[] packet_bits = new byte[packet_length];
  	  rtp_packet.getpacket(packet_bits);
  
  	  //send the packet as a DatagramPacket over the UDP socket 
  	  senddp = new DatagramPacket(packet_bits, packet_length, ClientIPAddr, RTP_dest_port);
  	  RTPsocket.send(senddp);
  
  	  //print the header bitstream
  	  rtp_packet.printheader();
  
  	  //update GUI
  	  disp.updateFull(frame.bufferedImage());
  	  System.out.println("Send frame #" + imagenb + " of length " + senddp.getLength());
      System.out.println(Arrays.toString(Arrays.copyOfRange(packet_bits, 0, 30)));

//      RTPpacket clientPacket = new RTPpacket(senddp.getData(), senddp.getLength());
//      int payload_length = clientPacket.getpayload_length();
//      byte [] payload = new byte[payload_length];
//      clientPacket.getpayload(payload);
//      ByteArrayInputStream clientIn = new ByteArrayInputStream(payload);
//      BufferedImage image = ImageIO.read(clientIn);
//      clientDisp.updateFull(image);
  	}
  	catch(Exception ex)
	  {
	    System.out.println("Exception caught: "+ex);
	    System.exit(0);
	  }
  }

  //------------------------------------
  //Parse RTSP Request
  //------------------------------------
  private int parse_RTSP_request()
  {
    int request_type = -1;
    try{
      //parse request line and extract the request_type:
      String RequestLine = RTSPBufferedReader.readLine();
      //System.out.println("RTSP Server - Received from Client:");
      System.out.println(RequestLine);

      StringTokenizer tokens = new StringTokenizer(RequestLine);
      Matcher m = Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(RequestLine);
      m.find();
//      String request_type_string = m.group(1);
      String request_type_string = tokens.nextToken();

      //convert to request_type structure:
      if ((new String(request_type_string)).compareTo("SETUP") == 0)
        request_type = SETUP;
      else if ((new String(request_type_string)).compareTo("PLAY") == 0)
        request_type = PLAY;
      else if ((new String(request_type_string)).compareTo("PAUSE") == 0)
        request_type = PAUSE;
      else if ((new String(request_type_string)).compareTo("TEARDOWN") == 0)
        request_type = TEARDOWN;

      if (request_type == SETUP)
      {
        //extract VideoFileName from RequestLine
    	  m.find();
    	  VideoFileName = m.group(1);
    	  VideoFileName = VideoFileName.substring(1, VideoFileName.length()-1);
      }

      //parse the SeqNumLine and extract CSeq field
      String SeqNumLine = RTSPBufferedReader.readLine();
      System.out.println(SeqNumLine);
      tokens = new StringTokenizer(SeqNumLine);
      tokens.nextToken();
      RTSPSeqNb = Integer.parseInt(tokens.nextToken());
	
      //get LastLine
      String LastLine = RTSPBufferedReader.readLine();
      System.out.println(LastLine);

      if (request_type == SETUP)
      {
    	  //extract RTP_dest_port from LastLine
    	  tokens = new StringTokenizer(LastLine);
    	  for (int i=0; i<3; i++)
    	    tokens.nextToken(); //skip unused stuff
    	  RTP_dest_port = Integer.parseInt(tokens.nextToken());
    	}
      //else LastLine will be the SessionId line ... do not check for now.
    }
    catch(Exception ex)
    {
      System.out.println("Exception caught: "+ex);
      System.exit(0);
    }
    return(request_type);
  }

  //------------------------------------
  //Send RTSP Response
  //------------------------------------
  private void send_RTSP_response()
  {
    try{
      RTSPBufferedWriter.write("RTSP/1.0 200 OK"+CRLF);
      RTSPBufferedWriter.write("CSeq: "+RTSPSeqNb+CRLF);
      RTSPBufferedWriter.write("Session: "+RTSP_ID+CRLF);
      RTSPBufferedWriter.flush();
      //System.out.println("RTSP Server - Sent response to Client.");
    }
    catch(Exception ex)
      {
	System.out.println("Exception caught: "+ex);
	System.exit(0);
      }
  }
}