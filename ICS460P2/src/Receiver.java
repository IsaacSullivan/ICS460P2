import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class Receiver {
	private String ipAddress = "localhost";
	private int port = 1005;

	private int percentageCorrupted = 10;
	private int percentageDropped = 5;
	private static File file;
	private Status ackStatus;
	
	
	private JFrame frmReceiver;
	private JTextField ipAddressInput;
	private JTextField portInput;

	private enum Status {
		VALID((short) 0, "SENT"), CORRUPT((short) 1, "ERR"), DROPPED((short) 0, "DROP");
		short checksum;
		String text;

		Status(short checksum, String text) {
			this.checksum = checksum;
			this.text = text;
		}
	}

	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					Receiver window = new Receiver();
					window.frmReceiver.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	public Receiver() {
		initialize();
	}
	
	public void run() {
		receiveFile();
	}

	public Status determineStatus() { // randomly determines whether the status is VALID, CORRUPT, or DROPPED
		double random = Math.random();
		if (random < percentageCorrupted / 100.0) {
			return Status.CORRUPT;
		} else if (random < (percentageCorrupted + percentageDropped) / 100.0) {
			return Status.DROPPED;
		} else {
			return Status.VALID;
		}
	}

	public byte[] numToBytes(short value) { // converts short to array of bytes
		return ByteBuffer.allocate(2).putShort(value).array();
	}

	public byte[] numToBytes(int value) { // converts int to array of bytes
		return ByteBuffer.allocate(4).putInt(value).array();
	}

	public short bytesToShort(byte[] elements) { // converts array of bytes to short
		return ByteBuffer.wrap(elements).getShort();
	}

	public int bytesToInt(byte[] elements) { // converts array of bytes to int
		return ByteBuffer.wrap(elements).getInt();
	}

	// determines status, creates, and returns datagram packet
	public DatagramPacket createAckPacket(ReceiverPacket packet) throws IOException {
		ackStatus = determineStatus();
		InetAddress host;
		try {
			host = InetAddress.getByName(ipAddress);
		} catch (IOException ex) {
			host = InetAddress.getByName("localhost"); // setting the default
		}
		short checksum = ackStatus.checksum;
		short len = packet.getLen();
		int ackno = packet.getAckno();
		DatagramPacket ack = new DatagramPacket(new byte[8], 8, host, port);
		ack.setData(
				new byte[] { numToBytes(checksum)[0], numToBytes(checksum)[1], numToBytes(len)[0], numToBytes(len)[1],
						numToBytes(ackno)[0], numToBytes(ackno)[1], numToBytes(ackno)[2], numToBytes(ackno)[3] });
		return ack;
	}

	// sends ack and prints out report
	public void sendAck(DatagramSocket socket, ReceiverPacket packet) throws IOException {
		DatagramPacket ackPacket = createAckPacket(packet);
		System.out.println(
				"SENDing ACK " + packet.getAckno() + " " + System.currentTimeMillis() + " " + ackStatus.text + "\n");
		if (ackStatus != Status.DROPPED) {
			socket.send(ackPacket);
		}
	}

	public void receiveFile() { // receives file and prints out report
		DatagramPacket partialFile = new DatagramPacket(new byte[512], 512);
		int nextDatagram = 1;
		try (DatagramSocket socket = new DatagramSocket(port); OutputStream output = new FileOutputStream(file);) {
			socket.receive(partialFile);
			while (partialFile.getLength() > 0) { // while there is more data to process
				port = partialFile.getPort();
				short checksum = bytesToShort(new byte[] { partialFile.getData()[0], partialFile.getData()[1] });
				short len = bytesToShort(new byte[] { partialFile.getData()[2], partialFile.getData()[3] });
				int ackno = bytesToInt(Arrays.copyOfRange(partialFile.getData(), 4, 8));
				int seqno = bytesToInt(Arrays.copyOfRange(partialFile.getData(), 8, 12));
				ReceiverPacket dataPacket = new ReceiverPacket(checksum, len, ackno);
				// if actual data length in the package received is not equal to the value of
				// the length field specified by sender, discard the package
				if (partialFile.getLength() != len) {
					socket.receive(partialFile);
					continue;
				}

				System.out.print(seqno < nextDatagram ? "DUPL " : "RECV ");
				System.out.print(System.currentTimeMillis() + " " + seqno + " ");
				if (seqno != nextDatagram) {
					System.out.println("!Seq\n");
				} else {
					System.out.println(checksum == 0 ? "RECV\n" : "CRPT\n");
				}

				if (checksum == 0) {
					sendAck(socket, dataPacket);
					if (seqno == nextDatagram) {
						output.write(partialFile.getData(), 12, len - 12);
						nextDatagram++;
					}
				}
				socket.receive(partialFile);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {

		file = new File("image_received.jpg");

		frmReceiver = new JFrame();
		frmReceiver.getContentPane().setBackground(Color.WHITE);
		frmReceiver.setTitle("Receiver");
		frmReceiver.setBounds(100, 100, 772, 410);
		frmReceiver.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frmReceiver.getContentPane().setLayout(null);

		// IP Address Label
		JLabel ipAddressLabel = new JLabel("IP Address");
		ipAddressLabel.setFont(new Font("Tahoma", Font.BOLD, 19));
		ipAddressLabel.setBounds(13, 15, 105, 23);
		frmReceiver.getContentPane().add(ipAddressLabel);

		// IP Address Input
		ipAddressInput = new JTextField();
		ipAddressInput.setToolTipText("000.000.0.0\r\n");
		ipAddressInput.setBounds(216, 16, 166, 29);
		frmReceiver.getContentPane().add(ipAddressInput);
		ipAddressInput.setColumns(10);

		// Port
		// Port Label
		JLabel portLabel = new JLabel("Port");
		portLabel.setFont(new Font("Tahoma", Font.BOLD, 19));
		portLabel.setBounds(13, 61, 82, 23);
		frmReceiver.getContentPane().add(portLabel);

		// Port Input
		portInput = new JTextField();
		portInput.setBounds(216, 58, 166, 29);
		frmReceiver.getContentPane().add(portInput);
		portInput.setColumns(10);

		// Corrupt
		// Corrupt Label
		JLabel corrutLabel = new JLabel("Corrupt %");
		corrutLabel.setFont(new Font("Tahoma", Font.BOLD, 19));
		corrutLabel.setBounds(13, 109, 152, 23);
		frmReceiver.getContentPane().add(corrutLabel);

		// Corrupt value
		JLabel corruptValue = new JLabel("0");
		corruptValue.setForeground(Color.BLACK);
		corruptValue.setFont(new Font("Tahoma", Font.PLAIN, 19));
		corruptValue.setBackground(Color.WHITE);
		corruptValue.setBounds(13, 143, 82, 23);
		frmReceiver.getContentPane().add(corruptValue);

		// Corrupt Slider
		JSlider corruptSlider = new JSlider();
		corruptSlider.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent arg0) {
				corruptValue.setText(Integer.toString(corruptSlider.getValue()));

				if (Integer.parseInt(corruptValue.getText()) > 70) {
					corruptValue.setForeground(Color.RED);
				} else if (Integer.parseInt(corruptValue.getText()) > 30) {
					corruptValue.setForeground(Color.orange);
				} else if (Integer.parseInt(corruptValue.getText()) > 0) {
					corruptValue.setForeground(Color.GREEN);
				}
			}
		});

		corruptSlider.setBackground(Color.WHITE);
		corruptSlider.setMajorTickSpacing(20);
		corruptSlider.setPaintLabels(true);
		corruptSlider.setPaintTicks(true);
		corruptSlider.setMinorTickSpacing(10);
		corruptSlider.setBounds(216, 109, 428, 72);
		frmReceiver.getContentPane().add(corruptSlider);

		// Dropped
		// Dropped Label
		JLabel droppedLabel = new JLabel("Dropped %");
		droppedLabel.setFont(new Font("Tahoma", Font.BOLD, 19));
		droppedLabel.setBounds(13, 190, 152, 23);
		frmReceiver.getContentPane().add(droppedLabel);

		// Dropped Value
		JLabel droppedValue = new JLabel("New label");
		droppedValue.setForeground(Color.BLACK);
		droppedValue.setFont(new Font("Tahoma", Font.PLAIN, 19));
		droppedValue.setBackground(Color.WHITE);
		droppedValue.setBounds(13, 224, 82, 23);
		frmReceiver.getContentPane().add(droppedValue);

		// Dropped Slider
		JSlider droppedSlider = new JSlider();
		droppedSlider.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent arg0) {
				droppedValue.setText(Integer.toString(droppedSlider.getValue()));

				if (Integer.parseInt(droppedValue.getText()) > 70) {
					droppedValue.setForeground(Color.RED);
				} else if (Integer.parseInt(droppedValue.getText()) > 30) {
					droppedValue.setForeground(Color.orange);
				} else if (Integer.parseInt(droppedValue.getText()) > 0) {
					droppedValue.setForeground(Color.GREEN);
				}
			}
		});

		droppedSlider.setBackground(Color.WHITE);
		droppedSlider.setMajorTickSpacing(20);
		droppedSlider.setPaintLabels(true);
		droppedSlider.setPaintTicks(true);
		droppedSlider.setMinorTickSpacing(10);
		droppedSlider.setBounds(216, 192, 428, 72);
		frmReceiver.getContentPane().add(droppedSlider);

		// SEND PRESSED
		JButton btnSend = new JButton("RECEIVE");
		btnSend.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent f) {

				if (ipAddressInput.getText().isEmpty()) {
					ipAddressInput.setText(ipAddress);
					JOptionPane.showMessageDialog(null, "Invalid value given for IP ADDRESS, using default: " + ipAddress);
					
				} else {
					ipAddress = ipAddressInput.getText();
				}

				if (portInput.getText().isEmpty()) {
					portInput.setText(String.valueOf(port));
					JOptionPane.showMessageDialog(null, "Invalid value given for Port, using default: " + port);
					
				} else {
					try {
						port = Integer.parseInt(portInput.getText());
					} catch (NumberFormatException e) {
						JOptionPane.showMessageDialog(null, "Invalid value given for Port, using default: " + port);
					}
				}

				percentageCorrupted = Integer.parseInt(corruptValue.getText());
				percentageDropped = Integer.parseInt(droppedValue.getText());

				run();
			}
		});

		btnSend.setBounds(282, 324, 131, 31);
		frmReceiver.getContentPane().add(btnSend);
	}
}
