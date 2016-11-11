package collage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.ParseException;
import java.util.Arrays;

public class RTSPRequestPacket extends RTSPPacket {
  private Method method = null;
  private Integer rtspSeqNum = null;
  private Integer sessionNum = null;
  private String url = null;

  private String rawData = null;
  
  // Transport
  private TransportProtocol transportProtocol = null;
  private Integer[] clientPorts = {};
  private TransportMode transportMode = null;
  
  private boolean isStarted = false;
  private boolean isDone = false;

  protected RTSPRequestPacket() {}
  
  public static RTSPRequestPacket make() {
    return new RTSPRequestPacket();
  }
  /**
   * Create and encode a new <code>RTSPRequestPacket</code>.
   * @return the encoded <code>RTSPRequestPacket</code>
   * @throws IllegalArgumentException if invalid arguments to encode
   */
  public static RTSPRequestPacket encode(Method reqType, String url, Integer rtspSeqNum, Integer sessionNum) throws IllegalArgumentException {
    RTSPRequestPacket packet = new RTSPRequestPacket();
    packet.method = reqType;
    packet.rtspSeqNum = rtspSeqNum;
    packet.sessionNum = sessionNum;
    packet.url = url;
    
    packet.encode();
    
    return packet;
  }
  /**
   * Create and encode a new <code>RTSPRequestPacket</code>.
   * For a SETUP request, additional parameters are needed.
   * @return the encoded <code>RTSPRequestPacket</code>
   * @throws IllegalArgumentException if invalid arguments to encode
   */
  public static RTSPRequestPacket encode(Method method, String url, Integer rtspSeqNum, Integer sessionNum,
      TransportProtocol transportProtocol, TransportMode transportMode, Integer[] clientPorts) throws IllegalArgumentException {
    RTSPRequestPacket packet = new RTSPRequestPacket();
    packet.method = method;
    packet.rtspSeqNum = rtspSeqNum;
    packet.sessionNum = sessionNum;
    packet.url = url;
    packet.transportProtocol = transportProtocol;
    packet.transportMode = transportMode;
    packet.clientPorts = clientPorts;
    
    packet.encode();
    
    return packet;
  }
  
  public static void main(String argv[]) throws Exception {
    if (argv.length < 1)
      throw new RuntimeException("Usage: <filename>");
    String url = argv[0];
    RTSPRequestPacket request; 
    RTSPRequestPacket decoded; 
    StringReader stringReader;
    BufferedReader reader;
    
    // OPTIONS test
    request = RTSPRequestPacket.encode(Method.OPTIONS, "*", 0, null);
    decoded = RTSPRequestPacket.make();
    stringReader = new StringReader(request.rawData());
    reader = new BufferedReader(stringReader);
    while (!decoded.isDone())
      decoded.decode(reader);
    if (!request.equals(decoded))
      System.err.println(request.reqType() + " test failed");
    else
      System.out.println(request.reqType() + " test passed");
    
    // DESCRIBE test
    request = RTSPRequestPacket.encode(Method.DESCRIBE, url, 1, null);
    decoded = RTSPRequestPacket.make();
    stringReader = new StringReader(request.rawData());
    reader = new BufferedReader(stringReader);
    while (!decoded.isDone())
      decoded.decode(reader);
    if (!request.equals(decoded))
      System.err.println(request.reqType() + " test failed");
    else
      System.out.println(request.reqType() + " test passed");

    // SETUP test
    request = RTSPRequestPacket.encode(Method.SETUP, url, 2, null,
        TransportProtocol.RTP, TransportMode.MULTICAST, new Integer[] {554});
    decoded = RTSPRequestPacket.make();
    stringReader = new StringReader(request.rawData());
    reader = new BufferedReader(stringReader);
    while (!decoded.isDone())
      decoded.decode(reader);
    if (!request.equals(decoded))
      System.err.println(request.reqType() + " test failed");
    else
      System.out.println(request.reqType() + " test passed");

    // PLAY test
    request = RTSPRequestPacket.encode(Method.PLAY, url, 3, 123456);
    decoded = RTSPRequestPacket.make();
    stringReader = new StringReader(request.rawData());
    reader = new BufferedReader(stringReader);
    while (!decoded.isDone())
      decoded.decode(reader);
    if (!request.equals(decoded))
      System.err.println(request.reqType() + " test failed");
    else
      System.out.println(request.reqType() + " test passed");

    // PAUSE test
    request = RTSPRequestPacket.encode(Method.PAUSE, url, 4, 123456);
    decoded = RTSPRequestPacket.make();
    stringReader = new StringReader(request.rawData());
    reader = new BufferedReader(stringReader);
    while (!decoded.isDone())
      decoded.decode(reader);
    if (!request.equals(decoded))
      System.err.println(request.reqType() + " test failed");
    else
      System.out.println(request.reqType() + " test passed");

    // TEARDOWN test
    request = RTSPRequestPacket.encode(Method.TEARDOWN, url, 5, 123456);
    decoded = RTSPRequestPacket.make();
    stringReader = new StringReader(request.rawData());
    reader = new BufferedReader(stringReader);
    while (!decoded.isDone())
      decoded.decode(reader);
    if (!request.equals(decoded))
      System.err.println(request.reqType() + " test failed");
    else
      System.out.println(request.reqType() + " test passed");
  }
  
  private void decodeMethod(String line) throws ParseException {
    String[] tokens = line.split("\\s+");
    if (tokens.length != 3)
      throw new ParseException("Invalid method line: " + line, 0);

    method = Method.valueOf(tokens[0]);
    try {
      url = URLDecoder.decode(tokens[1],CHAR_ENCODING);
    } catch (UnsupportedEncodingException e) {
      throw new ParseException("Incorrectly encoded url: " + tokens[1], 0);
    }
    if (!tokens[2].equals(RTSP_VERSION))
      throw new IllegalArgumentException("Unsupported RTSP Version: " + tokens[2]);
  }
  private void decodeSeqNum(String line) throws ParseException {
    String[] tokens = line.split(":");
    if (tokens.length != 2)
      throw new ParseException("Invalid sequence num line: " + line, 0);
    
    try {
      rtspSeqNum = Integer.parseInt(tokens[1].trim());
    } catch (NumberFormatException e) {
      throw new ParseException("Invalid sequence num: " + tokens[1], 0);
    }
  }
  private void decodeSessionNum(String line) throws ParseException {
    String[] tokens = line.split(":");
    if (tokens.length != 2)
      throw new ParseException("Invalid session num line: " + line, 0);
    
    try {
      sessionNum = Integer.parseInt(tokens[1].trim());
    } catch (NumberFormatException e) {
      throw new ParseException("Invalid session num: " + tokens[1], 0);
    }
  }
  public void decodeTransport(String line) throws ParseException {
    String[] tokens = line.split(":",2);
    if (tokens.length != 2)
      throw new ParseException("Invalid transport line: " + line, 0);
    String[] params = tokens[1].trim().split(";");
    for (int i=0; i<params.length; i++) {
      boolean found = false;
      try {
      if (params[i].contains("client_port")) {
        String[] paramPair = params[i].split("=");
        if (paramPair.length != 2)
          throw new ParseException("Invalid param in transport line: " + params[i], 0);
        paramPair = paramPair[1].split("-");
        clientPorts = new Integer[paramPair.length];
        for (int j=0; j<paramPair.length; j++)
          clientPorts[j] = Integer.parseInt(paramPair[j]);
        found = true;
      } else {
        for (int j=0; j<TransportProtocol.values().length; j++)
          if (TransportProtocol.values()[j].name().equals(params[i])) {
            transportProtocol = TransportProtocol.values()[j];
            found = true;
          }
        for (int j=0; j<TransportMode.values().length; j++)
          if (TransportMode.values()[j].rtspName().equals(params[i])) {
            transportMode = TransportMode.values()[j];
            found = true;
          }
      }
      } catch (Exception e) {
        throw new ParseException("Invalid param in transport line: " + params[i], 0);
      }
      if (!found)
        throw new ParseException("Unknown transport parameter: " + params[i], 0);
      
    }
  }
  public void decode(String line) throws ParseException {
    if (line.length() == 0 && !isStarted)
      return;
    if (rawData == null)
      rawData = "";
    rawData += line + CRLF;
    if (!isStarted) {
      decodeMethod(line);
      isStarted = true;
      return;
    }
    if (line.length() == 0) {
      isDone = true;
      return;
    }
    String[] pair = line.split(":",2);
    if (pair.length != 2)
      throw new IllegalArgumentException("Invalid header line: " + line);
    switch (pair[0]) {
    case "CSeq":
      decodeSeqNum(line);
      break;
    case "Session":
      decodeSessionNum(line);
      break;
    case "Transport":
      decodeTransport(line);
      break;
    default:
      throw new IllegalArgumentException("Unsupported header type: " + pair[0]);
    }
  }
  /**
   * Call repeatedly on same <code>BufferedReader</code>. Check <code>isDone()</code> for response completeness.
   * @param reader already open <code>BufferedReader</code>
   * @throws IOException if failed to read line from <code>reader</code>
   * @throws ParseException if error parsing input format
   * @throws IllegalArgumentException if correctly formatted invalid input
   */
  public void decode(BufferedReader reader) throws IOException, ParseException, IllegalArgumentException {
    if (isDone)
      throw new RuntimeException("Packet already complete");
    String line = reader.readLine();
    decode(line);
  }
  
  private String encodeMethod() throws UnsupportedEncodingException {
    String ret = method.toString() + " ";
    ret += URLEncoder.encode(url,CHAR_ENCODING);
    ret += " " + RTSP_VERSION + CRLF;
    return ret;
  }
  private String encodeSeqNum() {
    return "CSeq: " + rtspSeqNum + CRLF;
  }
  private String encodeSessionNum() {
    String ret = "";
    if (sessionNum != null)
      ret = "Session: " + sessionNum + CRLF;
    return ret;
  }
  private String encodeTransport() {
    String ret = "";
    if (transportProtocol != null || clientPorts.length > 0 || transportMode != null) {
      ret += "Transport: ";
      if (transportProtocol != null) {
        for (int i=0; i<TransportProtocol.values().length-1; i++)
          ret += TransportProtocol.values()[i] + "/";
        ret += TransportProtocol.values()[TransportProtocol.values().length-1] + ";";
      }
      if (clientPorts.length > 0) {
        ret += "client_port=" + clientPorts[0];
        if (clientPorts.length > 1)
          ret += "-" + clientPorts[1];
        ret += ";";
      }
      if (transportMode != null) {
        ret += transportMode.rtspName() + ";";
      }
      ret += CRLF;
    }
    return ret;
  }
  private void encode() {
    String encodedPacket = "";
    try {
      encodedPacket += encodeMethod();
    } catch (UnsupportedEncodingException e) {
      throw new IllegalArgumentException("Failed to encode url: " + url);
    }
    encodedPacket += encodeSeqNum();
    encodedPacket += encodeSessionNum();
    encodedPacket += encodeTransport();
    encodedPacket += CRLF;
    this.rawData = encodedPacket;
  }
  
  @Override
  public int hashCode() {
    int code = 1;
    code = code * 31 + method.ordinal();
    code = code * 17 + rtspSeqNum;
    code = code * 31 + sessionNum;
    code = code * 17 + url.hashCode();
    code = code * 31 + transportProtocol.ordinal();
    code = code * 17 + Arrays.hashCode(clientPorts);
    code = code * 31 + transportMode.ordinal();
    return code;
  }
  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof RTSPRequestPacket)) return false;
    RTSPRequestPacket other = (RTSPRequestPacket)obj;
    if (method != other.method) return false;
    if (rtspSeqNum != other.rtspSeqNum && !(rtspSeqNum != null && rtspSeqNum.equals(other.rtspSeqNum))) return false;
    if (sessionNum != other.sessionNum && !(sessionNum != null && sessionNum.equals(other.sessionNum))) return false;
    if (!url.equals(other.url)) return false;
    if (transportProtocol != other.transportProtocol) return false;
    if (clientPorts.length != 0 && !Arrays.equals(clientPorts, other.clientPorts)) return false;
    if (transportMode != other.transportMode) return false;
    return true;
  }

  @Override
  public String toString() {
    String out = "";
    if (method != null) out += "Method: " + method + CRLF;
    if (rtspSeqNum != null) out += "CSeq: " + rtspSeqNum + CRLF;
    if (sessionNum != null) out += "Session: " + sessionNum + CRLF;
    if (url != null) out += "Url: " + url + CRLF;
    if (transportProtocol != null) out += "Transport Protocol: " + transportProtocol + CRLF;
    if (clientPorts.length > 0) out += "Client Ports: " + clientPorts + CRLF;
    if (transportMode != null) out += "Transport Mode: " + transportMode + CRLF;
    if (rawData != null) out += rawData + CRLF;
    return out;
  }
  
  public Method reqType() {
    return method;
  }
  public Integer rtspSeqNum() {
    return rtspSeqNum;
  }
  public Integer sessionNum() {
    return sessionNum;
  }
  public String url() {
    return url;
  }
  public TransportProtocol transportProtocol() {
    return transportProtocol;
  }
  public Integer[] clientPorts() {
    return clientPorts;
  }
  public TransportMode transportMode() {
    return transportMode;
  }
  public String rawData() {
    return rawData;
  }
  public boolean isStarted() {
    return isStarted;
  }
  public boolean isDone() {
    return isDone;
  }
}
