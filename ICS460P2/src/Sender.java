import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.awt.EventQueue;

import javax.swing.JFrame;
import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import javax.swing.JSlider;
import javax.swing.event.ChangeListener;
import javax.swing.event.ChangeEvent;
import java.awt.Color;
import java.awt.Font;
import javax.swing.UIManager;

public class Sender {
	private int timeout = 2000;
	private InetAddress host;
	private int percentageCorrupted = 10;
	private int percentageDropped = 5;
	private static String fileName;
	private int fileLength;
	byte[] bytes;

	private JFrame frmSender;
	private JTextField ipAddressInput;
	private JTextField packetSizeInput;
	private JTextField portInput;

	private static String ipAddress = "localhost";
	private static int port = 1005;
	private static short packetSize = 512;

	private enum Status {
		VALID((short) 0, "SENT"), CORRUPT((short) 1, "ERRR"), DROPPED((short) 0, "DROP");
		short checksum;
		String text;

		Status(short checksum, String text) {
			this.checksum = checksum;
			this.text = text;
		}
	}

	public Sender() {
		initialize();
	}

	public static void main(String[] args) {
		// if command line argument isn't provided, the default will be used
		fileName = args.length > 0 ? args[0] : "monarch_butterfly_on_flower.jpg";
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					Sender window = new Sender();
					window.frmSender.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	public void run() {
		// initialize();
		File file = new File(fileName);
		fileToBytes(file);
		sendData(bytes);
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
		byte[] bytes = new byte[2];
		ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
		buffer.putShort(value);
		return buffer.array();
	}

	public byte[] numToBytes(int value) { // converts int to array of bytes
		byte[] bytes = new byte[4];
		ByteBuffer buffer = ByteBuffer.allocate(bytes.length);
		buffer.putInt(value);
		return buffer.array();
	}

	public short bytesToShort(byte[] elements) { // converts array of bytes to short
		ByteBuffer buffer = ByteBuffer.wrap(elements);
		return buffer.getShort();

	}

	public int bytesToInt(byte[] elements) { // converts array of bytes to int
		ByteBuffer buffer = ByteBuffer.wrap(elements);
		return buffer.getInt();
	}

	public void fileToBytes(File file) { // converts file to array of bytes
		fileLength = (int) file.length();
		try (InputStream insputStream = new FileInputStream(file)) {
			bytes = new byte[fileLength];
			insputStream.read(bytes);
		} catch (FileNotFoundException e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println(e.getMessage());
			e.printStackTrace();
		}
	}

	public byte[] joinArrays(byte[] array1, byte[] array2) {
		byte[] united = new byte[array1.length + array2.length];
		for (int i = 0; i < united.length; i++) {
			united[i] = i < array1.length ? array1[i] : array2[i - array1.length];
		}
		return united;
	}

	public DatagramPacket createDataPacket(Packet packet) { // converts packet into datagram
		byte[] checkSumArray = numToBytes(packet.getCksum()); // takes the first two bytes in the packet and puts then in a checksum array
		byte[] packetSizeArray = numToBytes(packet.getLen()); // takes the length of the packet and adds it to two bytes of size array
		byte[] acknoArray = numToBytes(packet.getAckno()); // takes the ack from the packet and adds it to four bytes in an array
		byte[] seqnoArray = numToBytes(packet.getSeqno()); // takes the sequence number of the packet and adds it two four byte array
		byte[] combinedArray = joinArrays(checkSumArray, packetSizeArray); // combines the checksum bytes with the  packet size = chcksum + size
		combinedArray = joinArrays(combinedArray, acknoArray); // combines acknum with chcksum + size = chksum + size + ack
		combinedArray = joinArrays(combinedArray, seqnoArray); // combines sequence with array = seq + chsum + size + ack
		combinedArray = joinArrays(combinedArray, packet.getData()); // appends the data to the header = [seq + chsum +  size + ack] + data
		// create the packet with the array of header + data just created
		return new DatagramPacket(combinedArray, combinedArray.length, host, port);
	}

	// produces printout according to the data extracted from ack datagram
	public boolean processAck(DatagramPacket ack, int seqno) {
		byte[] chksmArray = Arrays.copyOfRange(ack.getData(), 0, 2);
		short ackChksm = bytesToShort(chksmArray);
		byte[] ackNumArray = Arrays.copyOfRange(ack.getData(), 4, 8);
		int ackNum = bytesToInt(ackNumArray);
		System.out.print("AckRcvd " + ackNum);
		if (ackNum < seqno) {
			System.out.println(" DuplAck\n");
			return false;
		} else if (ackChksm == 1) {
			System.out.println(" ErrAck\n");
			return false;
		} else {
			System.out.println(" MoveWnd\n");
			return true;
		}
	}

	public void sendData(byte[] data) { // sends package, receives ack, prints out report
		try (DatagramSocket socket = new DatagramSocket(0)) {
			socket.setSoTimeout(timeout);
			int dataBytesPerTransmission = packetSize - 12;
			int dataBytesInLastTransmission = fileLength % dataBytesPerTransmission;
			int numOfPackets = fileLength % dataBytesPerTransmission == 0 ? fileLength / dataBytesPerTransmission
					: fileLength / dataBytesPerTransmission + 1;
			try {
				host = InetAddress.getByName(ipAddress);
			} catch (IOException e) {
				host = InetAddress.getByName("localhost"); // setting the default
			}

			Packet dataPacket;
			boolean received;
			DatagramPacket current;
			DatagramPacket ack = new DatagramPacket(new byte[8], 8);
			for (int i = 1; i <= numOfPackets; i++) {
				int endOffset = i < numOfPackets ? dataBytesPerTransmission * i
						: dataBytesPerTransmission * (i - 1) + dataBytesInLastTransmission;
				byte[] partialFile = Arrays.copyOfRange(bytes, dataBytesPerTransmission * (i - 1), endOffset);
				short len = (short) (i < numOfPackets ? packetSize : dataBytesInLastTransmission + 12);
				Status packetStatus = determineStatus();
				dataPacket = new Packet(packetStatus.checksum, len, i, i, partialFile);
				current = createDataPacket(dataPacket);
				System.out.printf("SENDing %d %d:%d %d %s\n\n", i, dataBytesPerTransmission * (i - 1), endOffset - 1,
						System.currentTimeMillis(), packetStatus.text);
				received = false;
				if (packetStatus != Status.DROPPED) {
					socket.send(current);
				}

				while (!received) {
					try {
						socket.receive(ack);
						received = processAck(ack, i);
					} catch (SocketTimeoutException e) {
						while (true) {
							System.out.println("Timeout " + i + "\n");
							packetStatus = determineStatus();
							dataPacket.setCksum(packetStatus.checksum);
							current = createDataPacket(dataPacket);
							System.out.printf("ReSend %d %d:%d %d %s\n\n", i, dataBytesPerTransmission * (i - 1),
									endOffset - 1, System.currentTimeMillis(), packetStatus.text);
							if (packetStatus != Status.DROPPED) {
								socket.send(current);
								break;
							}
						}
					}
				}
			}

			socket.send(new DatagramPacket(new byte[0], 0, host, port)); // indicates end of stream

		} catch (IOException e) {
			e.getMessage();
			e.printStackTrace();
		}
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		frmSender = new JFrame();
		frmSender.getContentPane().setBackground(Color.WHITE);
		frmSender.setTitle("Sender");
		frmSender.setBounds(100, 100, 772, 640);
		frmSender.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frmSender.getContentPane().setLayout(null);

		// IP Address
		// IP Address Label
		JLabel ipAddressLabel = new JLabel("IP Address");
		ipAddressLabel.setFont(new Font("Tahoma", Font.BOLD, 19));
		ipAddressLabel.setBounds(17, 19, 105, 23);
		frmSender.getContentPane().add(ipAddressLabel);

		// IP Address Input
		ipAddressInput = new JTextField();
		ipAddressInput.setToolTipText("000.000.0.0\r\n");
		ipAddressInput.setBounds(216, 16, 166, 29);
		frmSender.getContentPane().add(ipAddressInput);
		ipAddressInput.setColumns(10);

		// Timeout
		// Timeout Value from slider
		JLabel timeoutValue = new JLabel("2000");
		timeoutValue.setForeground(Color.BLACK);
		timeoutValue.setFont(new Font("Tahoma", Font.PLAIN, 19));
		timeoutValue.setBackground(Color.WHITE);
		timeoutValue.setBounds(17, 330, 82, 23);
		frmSender.getContentPane().add(timeoutValue);

		JLabel timeoutLabel = new JLabel("Timeout (ms)");
		timeoutLabel.setFont(new Font("Tahoma", Font.BOLD, 19));
		timeoutLabel.setBounds(17, 291, 131, 23);
		frmSender.getContentPane().add(timeoutLabel);

		JSlider timeOutSlider = new JSlider();
		timeOutSlider.setValue(2000);
		timeOutSlider.setBorder(UIManager.getBorder("TextPane.border"));
		timeOutSlider.setBackground(Color.WHITE);
		timeOutSlider.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent arg0) {
				timeoutValue.setText(Integer.toString(timeOutSlider.getValue()));

				if (Integer.parseInt(timeoutValue.getText()) > 3500) {
					timeoutValue.setForeground(Color.RED);
				} else if (Integer.parseInt(timeoutValue.getText()) > 2000) {
					timeoutValue.setForeground(Color.orange);
				} else if (Integer.parseInt(timeoutValue.getText()) > 0) {
					timeoutValue.setForeground(Color.GREEN);
				}

			}
		});

		timeOutSlider.setPaintTicks(true);
		timeOutSlider.setPaintTrack(true);
		timeOutSlider.setMajorTickSpacing(1000);
		timeOutSlider.setMinorTickSpacing(500);
		timeOutSlider.setMaximum(5000);
		timeOutSlider.setBounds(216, 291, 428, 64);
		frmSender.getContentPane().add(timeOutSlider);
		timeOutSlider.setPaintLabels(true);

		// Packet Size
		// Packet Size Label
		JLabel packetSizeLabel = new JLabel("Packet Size (bytes)");
		packetSizeLabel.setFont(new Font("Tahoma", Font.BOLD, 19));
		packetSizeLabel.setBounds(13, 107, 186, 23);
		frmSender.getContentPane().add(packetSizeLabel);

		// Packet size input
		packetSizeInput = new JTextField();
		packetSizeInput.setBounds(216, 104, 166, 29);
		frmSender.getContentPane().add(packetSizeInput);
		packetSizeInput.setColumns(10);

		// Port
		// Port Label
		JLabel portLabel = new JLabel("Port");
		portLabel.setFont(new Font("Tahoma", Font.BOLD, 19));
		portLabel.setBounds(13, 61, 82, 23);
		frmSender.getContentPane().add(portLabel);

		// Port Input
		portInput = new JTextField();
		portInput.setBounds(216, 58, 166, 29);
		frmSender.getContentPane().add(portInput);
		portInput.setColumns(10);

		// Corrupt
		// Corrupt Label
		JLabel corrutLabel = new JLabel("Corrupt %");
		corrutLabel.setFont(new Font("Tahoma", Font.BOLD, 19));
		corrutLabel.setBounds(17, 182, 152, 23);
		frmSender.getContentPane().add(corrutLabel);

		// Corrupt value
		JLabel corruptValue = new JLabel("10");
		corruptValue.setForeground(Color.BLACK);
		corruptValue.setFont(new Font("Tahoma", Font.PLAIN, 19));
		corruptValue.setBackground(Color.WHITE);
		corruptValue.setBounds(17, 218, 82, 23);
		frmSender.getContentPane().add(corruptValue);

		// Corrupt Slider
		JSlider corruptSlider = new JSlider();
		corruptSlider.setValue(10);
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
		corruptSlider.setBounds(216, 182, 428, 72);
		frmSender.getContentPane().add(corruptSlider);

		// Dropped
		// Dropped Label
		JLabel droppedLabel = new JLabel("Dropped %");
		droppedLabel.setFont(new Font("Tahoma", Font.BOLD, 19));
		droppedLabel.setBounds(17, 386, 152, 23);
		frmSender.getContentPane().add(droppedLabel);

		// Dropped Value
		JLabel droppedValue = new JLabel("5");
		droppedValue.setForeground(Color.BLACK);
		droppedValue.setFont(new Font("Tahoma", Font.PLAIN, 19));
		droppedValue.setBackground(Color.WHITE);
		droppedValue.setBounds(17, 428, 82, 23);
		frmSender.getContentPane().add(droppedValue);

		// Dropped Slider
		JSlider droppedSlider = new JSlider();
		droppedSlider.setValue(5);
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
		droppedSlider.setBounds(216, 386, 428, 72);
		frmSender.getContentPane().add(droppedSlider);

		// SEND PRESSED
		JButton btnSend = new JButton("SEND");
		btnSend.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent f) {

				if (ipAddressInput.getText().isEmpty()) {
					ipAddressInput.setText(ipAddress);
					JOptionPane.showMessageDialog(null,
							"Invalid value given for IP ADDRESS, using default: " + ipAddress);
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

				if (packetSizeInput.getText().isEmpty()) {
					packetSizeInput.setText(String.valueOf(packetSize));
					JOptionPane.showMessageDialog(null,
							"Invalid value given for Packet Size, using default: " + packetSize);
				} else {
					try {
						packetSize = Short.parseShort(packetSizeInput.getText());
					} catch (NumberFormatException e) {
						JOptionPane.showMessageDialog(null,
								"Invalid value given for Packet Size, using default: " + packetSize);
					}
				}

				timeout = Integer.parseInt(timeoutValue.getText());
				percentageCorrupted = Integer.parseInt(corruptValue.getText());
				percentageDropped = Integer.parseInt(droppedValue.getText());

				run();
			}
		});

		btnSend.setBounds(327, 526, 131, 31);
		frmSender.getContentPane().add(btnSend);
	}
}
