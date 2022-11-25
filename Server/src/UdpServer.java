
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class UdpServer {
  public static void main(String[] args) throws Exception {
    DatagramSocket socket = new DatagramSocket(new InetSocketAddress("192.168.1.80", 8090));
    byte[] buffer = new byte[512];
    DatagramPacket packet = new DatagramPacket(buffer, 0, buffer.length);
    for (;;) {
      socket.receive(packet);
      int length = packet.getLength();
      int offset = packet.getOffset();
      String data = new String(packet.getData(), offset, length, StandardCharsets.ISO_8859_1);
      System.out.printf("[packet from %s]: %s\n", packet.getAddress(), data);
    }

  }
}
