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
import javafx.application.Application;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

public class Sender extends Application {
	private TextField packetSizeField = new TextField("512");
	private TextField timeoutField = new TextField("2000");
	private TextField ipAddressField = new TextField("localhost");
	private TextField portField = new TextField("1005");
	private TextField percentageCorruptedField = new TextField("10");
	private TextField percentageDroppedField = new TextField("5");
	private Button send = new Button("Send");
	private short packetSize = 512;
	private int timeout = 2000;
	private String ipAddress = "localhost";
	private int port = 1005;
	private InetAddress host;
	private int percentageCorrupted = 10;
	private int percentageDropped = 5;
	private static String fileName;
	private int fileLength;
	byte[] bytes;

	private enum Status {
		VALID((short) 0, "SENT"), CORRUPT((short) 1, "ERRR"), DROPPED((short) 0, "DROP");
		short checksum;
		String text;

		Status(short checksum, String text) {
			this.checksum = checksum;
			this.text = text;
		}
	}

	public static void main(String[] args) {
		// if command line argument isn't provided, the default will be used
		String filePath = System.getProperty("user.dir") + "\\src\\monarch_butterfly_on_flower.jpg";
		fileName = args.length > 0 ? args[0] : filePath;
		Application.launch(args);
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		GridPane pane = new GridPane();
		pane.setHgap(5);
		pane.setVgap(5);
		pane.add(new Label("Packet Size"), 0, 0);
		pane.add(packetSizeField, 1, 0);
		pane.add(new Label("Timeout"), 0, 1);
		pane.add(timeoutField, 1, 1);
		pane.add(new Label("IP Address"), 0, 2);
		pane.add(ipAddressField, 1, 2);
		pane.add(new Label("Port"), 0, 3);
		pane.add(portField, 1, 3);
		pane.add(new Label("Dropped, %"), 0, 4);
		pane.add(percentageDroppedField, 1, 4);
		pane.add(new Label("Corrupted, %"), 0, 5);
		pane.add(percentageCorruptedField, 1, 5);
		send.setMaxWidth(100);
		send.setOnAction(e -> run());
		pane.add(send, 1, 6);

		pane.setAlignment(Pos.CENTER);
		packetSizeField.setAlignment(Pos.BOTTOM_RIGHT);
		timeoutField.setAlignment(Pos.BOTTOM_RIGHT);
		ipAddressField.setAlignment(Pos.BOTTOM_RIGHT);
		portField.setAlignment(Pos.BOTTOM_RIGHT);
		percentageCorruptedField.setAlignment(Pos.BOTTOM_RIGHT);
		percentageDroppedField.setAlignment(Pos.BOTTOM_RIGHT);
		GridPane.setHalignment(send, HPos.RIGHT);

		Scene scene = new Scene(pane, 250, 250);

		primaryStage.setTitle("Sender");
		primaryStage.setScene(scene);
		primaryStage.show();
	}

	public void run() {
		initializeFields();
		File file = new File(fileName);
		fileToBytes(file);
		sendData(bytes);
	}

	// attempts to set instance variables to input values, and if it fails (throws
	// exception), default values are used instead.
	private void initializeFields() {
		String readField;
		try {
			readField = packetSizeField.getText();
			if (!readField.equals("") && Integer.parseInt(readField) > 0 && Integer.parseInt(readField) <= 512) {
				packetSize = Short.parseShort(readField);
			}
		} catch (NumberFormatException ex) { // ignore, and the program will use defaults

		}
		try {
			readField = timeoutField.getText();
			if (!readField.equals("") && Integer.parseInt(readField) > 0) {
				timeout = Integer.parseInt(readField);
			}
		} catch (NumberFormatException ex) {

		}

		readField = ipAddressField.getText();
		if (!readField.equals("")) {
			ipAddress = readField;
		}
		try {
			readField = portField.getText();
			if (!readField.equals("") && Integer.parseInt(readField) > 0) {
				port = Integer.parseInt(readField);
			}
		} catch (NumberFormatException ex) {

		}
		try {
			readField = percentageCorruptedField.getText();
			if (!readField.equals("") && Integer.parseInt(readField) >= 0) {
				percentageCorrupted = Integer.parseInt(readField);
			}
		} catch (NumberFormatException ex) {

		}
		try {
			readField = percentageDroppedField.getText();
			if (!readField.equals("") && Integer.parseInt(readField) >= 0) {
				percentageDropped = Integer.parseInt(readField);
			}
		} catch (NumberFormatException ex) {

		}
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

	public byte[] numToBytes(short value) {
		return ByteBuffer.allocate(2).putShort(value).array();
	}

	public byte[] numToBytes(int value) {
		return ByteBuffer.allocate(4).putInt(value).array();
	}

	public short bytesToShort(byte[] elements) {
		return ByteBuffer.wrap(elements).getShort();
	}

	public int bytesToInt(byte[] elements) {
		return ByteBuffer.wrap(elements).getInt();
	}

	public void fileToBytes(File file) {
		fileLength = (int) file.length();
		try (InputStream insputStream = new FileInputStream(file)) {
			bytes = new byte[fileLength];
			insputStream.read(bytes); // the original file is now converted to array of bytes
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
		System.arraycopy(array1, 0, united, 0, array1.length);
		System.arraycopy(array2, 0, united, array1.length, array2.length);
		return united;
	}

	public DatagramPacket createDataPacket(Packet packet) {
		byte[] checkSumArray = numToBytes(packet.getCksum());
		byte[] packetSizeArray = numToBytes(packet.getLen());
		byte[] acknoArray = numToBytes(packet.getAckno());
		byte[] seqnoArray = numToBytes(packet.getSeqno());
		byte[] combinedArray = joinArrays(checkSumArray, packetSizeArray);
		combinedArray = joinArrays(combinedArray, acknoArray);
		combinedArray = joinArrays(combinedArray, seqnoArray);
		combinedArray = joinArrays(combinedArray, packet.getData());
		return new DatagramPacket(combinedArray, combinedArray.length, host, port);
	}

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

	public void sendData(byte[] data) {
		try (DatagramSocket socket = new DatagramSocket(0)) {
			socket.setSoTimeout(timeout);
			int bytesPerTransmission = packetSize - 12;
			int bytesInLastTransmission = fileLength % bytesPerTransmission;
			int numOfPackets = fileLength % bytesPerTransmission == 0 ? fileLength / bytesPerTransmission
					: fileLength / bytesPerTransmission + 1;
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
				int endOffset = i < numOfPackets ? bytesPerTransmission * i
						: bytesPerTransmission * (i - 1) + bytesInLastTransmission;
				byte[] partialFile = Arrays.copyOfRange(bytes, bytesPerTransmission * (i - 1), endOffset);
				short len = (short) (i < numOfPackets ? packetSize : bytesInLastTransmission + 12);
				Status packetStatus = determineStatus();
				dataPacket = new Packet(packetStatus.checksum, len, i, i, partialFile);
				current = createDataPacket(dataPacket);
				System.out.printf("SENDing %d %d %d:%d %s\n\n", i, System.currentTimeMillis(),
						bytesPerTransmission * (i - 1), endOffset - 1, packetStatus.text);
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
							System.out.printf("ReSend %d %d %d:%d %s\n\n", i, System.currentTimeMillis(),
									bytesPerTransmission * (i - 1), endOffset - 1, packetStatus.text);
							if (packetStatus != Status.DROPPED) {
								socket.send(current);
								break;
							}
						}
					}
				}
			}
			
			socket.send(new DatagramPacket(new byte[0], 0, host, port));

		} catch (IOException e) {
			e.getMessage();
			e.printStackTrace();
		}
	}
}
