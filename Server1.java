import java.io.*;
import java.util.*;
import java.net.*;
import java.nio.ByteBuffer;

public class Server1 {

	private int port;
	private String fname;
	private float p;
	private Random r;
	private DatagramSocket server;
	public Server1(int port, String fname, float p){
		this.port = port;
		this.fname = fname;
		this.p = p;	
		this.r = new Random();
		try {
			this.server = new DatagramSocket(port);
		} catch (SocketException e) {
			System.out.println(e.getMessage());
		}
	}
	
	void receiveFile() throws Exception{
		File f = new File(fname);
		FileOutputStream fos = new FileOutputStream(f);
		byte[] receivedSegment;
		int waitingFor = 0;

		while (true) {
			receivedSegment = new byte[4096];
			DatagramPacket receivedPacket = new DatagramPacket(receivedSegment, receivedSegment.length);
			server.receive(receivedPacket);
			int seqNum = ByteBuffer.wrap(Arrays.copyOfRange(receivedPacket.getData(), 0, 4)).getInt();
			short sender_checksum = ByteBuffer.wrap(Arrays.copyOfRange(receivedPacket.getData(), 4, 6)).getShort();
//			byte byte0 = receivedSegment[4]; byte byte1 = receivedSegment[5];
//			System.out.println("Bytes of Checksum:" + byte0 + " " + byte1);
//			int sender_checksum_test = (short) ((byte1 & 0xFF) + ((byte0 & 0xFF) << 8));
//			System.out.println("Sender Checksum test:"+ sender_checksum_test);
//			for(int i=0; i<8;i++){
//				System.out.print(receivedSegment[i] + " ");
//			}
//			System.out.println();
			
			short packetType = ByteBuffer.wrap(Arrays.copyOfRange(receivedPacket.getData(), 6, 8)).getShort();
			byte[] data = new byte[receivedPacket.getLength() - 8];
			System.arraycopy(receivedSegment, 8, data, 0, data.length);
			
			if(packetType == 0){
				System.out.println("Reached End of File");
				fos.flush();
				fos.close();
				server.close();
				break;
			}
			
			float randomNumber = r.nextFloat();
//			System.out.println("RandomNumber: "+randomNumber);
			if (randomNumber <= p) {
				System.out.println("Packet loss, sequence number = " + seqNum);
			} else {
				InetAddress from = receivedPacket.getAddress();
				int portFrom = receivedPacket.getPort();
				short checkSumVerify = (short) checksum(data);
//				System.out.println("Got:"+seqNum + " Looking for:"+waitingFor);
//				System.out.println("Original Checksum:"+sender_checksum + " & Calculated Checksum:"+ checkSumVerify);
//				for(int i=0;i<Math.min(data.length, 15);i++){
//					System.out.print(data[i] + " ");
//				}
//				System.out.println();
				if (checkSumVerify == sender_checksum) {
					// System.out.println("Checksum verified");
					if (seqNum == waitingFor) {
						waitingFor++;
						if (packetType == 21845) {
							// System.out.println("Packet type checked");
							fos.write(data);
							byte[] segment = createAckSegment(waitingFor);
							DatagramPacket ack = new DatagramPacket(segment, segment.length, from, portFrom);
//							System.out.println("Sending Ack for seqNum:"+waitingFor);
							server.send(ack);
						} 
					}else{
//						System.out.println("Sending Ack for seqNum (Resending):"+waitingFor);
						byte[] segment = createAckSegment(waitingFor);
						DatagramPacket ack = new DatagramPacket(segment, segment.length, from, portFrom);
						server.send(ack);
					}
				}
			}
		}
		fos.flush();
		fos.close();
		server.close();
	}
	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub
		if (args.length != 3) {
			System.out.println("Format: <Server port> <Filename> <Probability>");
			return;
		}
		int port = Integer.parseInt(args[0]);
		String filename = args[1];
		float p = Float.parseFloat(args[2]);
		
		Server1 receiver = new Server1(port, filename, p);
		receiver.receiveFile();
	}

	
	byte[] createAckSegment(int seqNum) {
		byte[] segment = new byte[8];
		int ackPacket = 43690; // Packet type 16 bit: 1010101010101010
		int other = 0;
		ByteBuffer b = ByteBuffer.allocate(4);
		b.putInt(seqNum);
		byte[] sequence = b.array();
		b = ByteBuffer.allocate(2);
		b.putShort((short)ackPacket);
		byte[] dataPacket = b.array();
		b = ByteBuffer.allocate(2);
		b.putShort((short)other);
		byte[] empty = b.array();
		
		System.arraycopy(sequence, 0, segment, 0, sequence.length);
		System.arraycopy(empty, 0, segment, 4, empty.length);
		System.arraycopy(dataPacket, 0, segment, 6, dataPacket.length);
		
		return segment;
	}

	long checksum(byte [] b)
    {  
		int i = 0, length = b.length;
		long sum = 0;
		while (length > 0) {
			sum += (b[i++] & 0xff) << 8;
			if ((--length) == 0)
				break;
			sum += (b[i++] & 0xff);
			--length;
		}
		return ((~((sum & 0xFFFF) + (sum >> 16))) & 0xFFFF);
    }
}
