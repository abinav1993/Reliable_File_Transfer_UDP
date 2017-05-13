import java.io.*;
import java.util.*;
import java.net.*;
import java.nio.ByteBuffer;

public class WorkingClient {
	private byte[] data;
	private InetAddress serverIP;
	private int serverport;
	private int n;
	private int mss;
	private String path;
	private DatagramSocket client;
	
	public WorkingClient(InetAddress serverIP, int serverport, int n, int mss, String filepath){
		this.serverIP = serverIP;
		this.serverport = serverport;
		this.n = n;
		this.mss = mss;
		this.path =filepath;
		
		try{
			this.client = new DatagramSocket(9000);
			File f = new File(path);
			FileInputStream fis = new FileInputStream(f);
			data = new byte[fis.available()];
			fis.read(data);
//			System.out.println("data length:" + data.length);
			fis.close();
		}catch(Exception e){
			System.out.println(e.getMessage());
		}
	}
	
	public static void main(String[] args) throws Exception {
		if (args.length != 5) {
			System.out.println("Format: <Server-host-name> <server-port> <filename/path> <N> <MSS>");
			return;
		}
		String hostname = args[0];
		int port = Integer.parseInt(args[1]);
//		File f = new File(args[2]);
		String path = args[2];
		int n = Integer.parseInt(args[3]);
		int mss = Integer.parseInt(args[4]);
		InetAddress IPAddress = InetAddress.getByName(hostname);
		WorkingClient sender = new WorkingClient(IPAddress, port, n, mss, path);
		System.out.println("Total running time in (msecs): " + sender.transferFile());
	}
	
	long transferFile() throws IOException{
		int lastSent = 0;
		int NoAck = 0;
		int maxSeqNum = (int) Math.ceil((float) data.length / mss);
		System.out.println("Total number of packets:" + maxSeqNum);
		long start = System.currentTimeMillis();
		
		while (true) {
			while (lastSent - NoAck < n && lastSent < maxSeqNum) {
				DatagramPacket p = rdt_send(data, mss, lastSent, serverIP, serverport);
//				System.out.println("Sending packet:"+lastSent);
//				System.out.println();
				client.send(p);
				client.setSoTimeout(1000);
				// withOutAck.add(p);
				lastSent++;
			}
			try {
				byte[] ackData = new byte[64];
				DatagramPacket ack = new DatagramPacket(ackData, ackData.length);
				client.receive(ack);
				int AckedSeqNum = ByteBuffer.wrap(Arrays.copyOfRange(ack.getData(), 0, 4)).getInt();
//				System.out.println("Ack reveived for packet:"+AckedSeqNum);
				if (AckedSeqNum == maxSeqNum || maxSeqNum == 0)
					break;
				NoAck = Math.max(NoAck, AckedSeqNum);
			} catch (SocketTimeoutException e) {
				// for(int i = NoAck; i < lastSent; i++){
				// client.send(withOutAck.get(i));
				// System.out.println("Resending the packets again");
				// }
				System.out.println("Timeout, sequence number = " + NoAck);
				lastSent = NoAck;
			}
		}
		long end = System.currentTimeMillis();
		System.out.println("Sending End of File packet");
		byte[] segment = createSegment("EOF".getBytes(), lastSent, "EOF");
		DatagramPacket p = new DatagramPacket(segment, segment.length, serverIP, serverport);
		client.send(p);
		client.close();
		return end - start;
	}
	
	DatagramPacket rdt_send(byte[] data, int mss, int lastSent, InetAddress IPAddress, int port){
		byte[] pData = new byte[mss];
		pData = Arrays.copyOfRange(data, lastSent * mss,
				((lastSent * mss) + mss) > data.length ? data.length : (lastSent * mss) + mss);
//		for(int i=0;i<Math.min(pData.length,15);i++){
//			System.out.print(pData[i] + " ");
//		}
		byte[] segment = createSegment(pData, lastSent, "data");
		DatagramPacket p = new DatagramPacket(segment, segment.length, IPAddress, port);
		return p;
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

	byte[] createSegment(byte[] data, int seq, String type){
		byte[] segment = new byte[8];
		ByteBuffer b = ByteBuffer.allocate(4);
		b.putInt(seq);
		byte[] sequence = b.array();//getSequenceNumber(seq);
		b = ByteBuffer.allocate(2);
		if(type.equals("data"))
			b.putShort((short)21845);  // Packet type 16 bit: 0101010101010101
		else
			b.putShort((short)0);
		byte[] dataPacket = b.array(); 
		
		short checksum = (short)checksum(data);
		//b = ByteBuffer.allocate(2);
		//b.putShort((short)checksum);  
		byte[] check = new byte[] { (byte) (checksum >>> 8), (byte) checksum };
		//System.out.println("Bytes of Checksum:" + check[0] + " " + check[1]);

		System.arraycopy(sequence, 0, segment, 0, sequence.length);
		System.arraycopy(check, 0, segment, 4, check.length);
		System.arraycopy(dataPacket, 0, segment, 6, dataPacket.length);
		byte result[] = new byte[segment.length + data.length];
		System.arraycopy(segment, 0, result, 0, segment.length);
		
		System.arraycopy(data, 0, result, 8, data.length);
		return result;	
	}
}
