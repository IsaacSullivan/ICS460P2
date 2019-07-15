
public class ReceiverPacket {
	private short cksum; // 16-bit 2-byte
	private short len; // 16-bit 2-byte
	private int ackno; // 32-bit 4-byte

	public ReceiverPacket(short cksum, short len, int ackno) {
		this.cksum = cksum;
		this.len = len;
		this.ackno = ackno;
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

	public void setCksum(short cksum) {
		this.cksum = cksum;
	}

	public void setLen(short len) {
		this.len = len;
	}

	public void setAckno(int ackno) {
		this.ackno = ackno;
	}
}
