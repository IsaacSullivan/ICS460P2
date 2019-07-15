import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
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

public class Receiver extends Application {
	private TextField ipAddressField = new TextField("localhost");
	private TextField portField = new TextField("1005");
	private TextField percentageCorruptedField = new TextField("10");
	private TextField percentageDroppedField = new TextField("5");
	private Button receive = new Button("Receive");
	private String ipAddress;
	private int port;

	private int percentageCorrupted;
	private int percentageDropped;
	private static File file = new File("image_received.jpg");
	private Status ackStatus;

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
		Application.launch(args);
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		GridPane pane = new GridPane();
		pane.setHgap(5);
		pane.setVgap(5);

		pane.add(new Label("IP Address"), 0, 0);
		pane.add(ipAddressField, 1, 0);
		pane.add(new Label("Port"), 0, 1);
		pane.add(portField, 1, 1);
		pane.add(new Label("Dropped, %"), 0, 2);
		pane.add(percentageDroppedField, 1, 2);
		pane.add(new Label("Corrupted, %"), 0, 3);
		pane.add(percentageCorruptedField, 1, 3);
		receive.setMaxWidth(100);
		receive.setOnAction(e -> run());
		pane.add(receive, 1, 4);

		pane.setAlignment(Pos.CENTER);
		ipAddressField.setAlignment(Pos.BOTTOM_RIGHT);
		portField.setAlignment(Pos.BOTTOM_RIGHT);
		percentageCorruptedField.setAlignment(Pos.BOTTOM_RIGHT);
		percentageDroppedField.setAlignment(Pos.BOTTOM_RIGHT);
		GridPane.setHalignment(receive, HPos.RIGHT);

		Scene scene = new Scene(pane, 250, 250);

		primaryStage.setTitle("Receiver");
		primaryStage.setScene(scene);
		primaryStage.show();
	}

	public void run() {
		initializeFields();
		receiveFile();
	}

	private void initializeFields() {
		String readField = ipAddressField.getText();
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

	public Status determineStatus() {
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

	public void sendAck(DatagramSocket socket, ReceiverPacket packet) throws IOException {
		DatagramPacket ackPacket = createAckPacket(packet);
		System.out.println(
				"SENDing ACK " + packet.getAckno() + " " + System.currentTimeMillis() + " " + ackStatus.text + "\n");
		if (ackStatus != Status.DROPPED) {
			socket.send(ackPacket);
		}
	}

	public void receiveFile() {
		DatagramPacket partialFile = new DatagramPacket(new byte[512], 512);
		int nextDatagram = 1;
		try (DatagramSocket socket = new DatagramSocket(port); OutputStream output = new FileOutputStream(file);) {
			socket.receive(partialFile);
			while (partialFile.getLength() > 0) {
				port = partialFile.getPort();
				short checksum = bytesToShort(new byte[] { partialFile.getData()[0], partialFile.getData()[1] });
				short len = bytesToShort(new byte[] { partialFile.getData()[2], partialFile.getData()[3] });
				int ackno = bytesToInt(Arrays.copyOfRange(partialFile.getData(), 4, 8));
				int seqno = bytesToInt(Arrays.copyOfRange(partialFile.getData(), 8, 12));
				ReceiverPacket dataPacket = new ReceiverPacket(checksum, len, ackno);

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
}
