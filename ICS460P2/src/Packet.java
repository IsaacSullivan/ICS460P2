
public class Packet {
	private short cksum; // 16-bit 2-byte
	private short len; // 16-bit 2-byte
	private int ackno; // 32-bit 4-byte
	private int seqno; // 32-bit 4-byte Data packet Only
	private byte[] data; // 0-500 bytes. Data packet only. Variable

	public Packet(short cksum, short len, int ackno, int seqno, byte[] data) {
		this.cksum = cksum;
		this.len = len;
		this.ackno = ackno;
		this.seqno = seqno;
		this.data = data;
	}

	public short getCksum() {
		return cksum;
	}

	public short getLen() {
		return len;
	}

	public int getAckno() {
		return ackno;
	}

	public int getSeqno() {
		return seqno;
	}

	public byte[] getData() {
		return data;
	}

	public void setCksum(short cksum) {
		this.cksum = cksum;
	}

	public void setLen(short len) {
		this.len = len;
	}

	public void setAckno(int ackno) {
		this.ackno = ackno;
	}

	public void setSeqno(int seqno) {
		this.seqno = seqno;
	}

	public void setData(byte[] data) {
		this.data = data;
	}
}
