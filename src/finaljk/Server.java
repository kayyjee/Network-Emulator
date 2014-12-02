package finaljk;


import java.io.*;
import java.net.*;
import java.util.*;
import java.lang.*;
import java.util.logging.Level;
import java.util.logging.Logger;

class Server {
    private static ArrayList<Packet> Window;
    private static int receivePacketNumber = 0;
    private static DatagramPacket sendPacket;
    private static DatagramSocket clientSocket;
    private static InetAddress NetEmuIPAddress;
    public static ArrayList<Packet> PacketArray;
    private static int[] checkedPackets;
    private static int WindowSize = 10;
    private static int seqNumber = 0;
    private static int totalPackets = 50;
    private static int packetNumber = 0;
    private static byte[] receiveData = new byte[1024];
    private static byte[] sendData = new byte[1024];
    private static int bitLoss = 5; // ~5% of packets will be dropped. 
    static PrintWriter writer;
    private static DatagramPacket ReceivePacket;
    private static DatagramSocket serverSocket;
    private static InetAddress IPAddress;
    private static long delay = 666;
    
    public static void main(String args[]) throws Exception {
        //Create log writer.
        writer = new PrintWriter("HostB_Log.txt", "UTF-8");
        
        
        
        PacketArray = new ArrayList<Packet>();
        checkedPackets = new int[totalPackets - 1];
        int b;

        for (b = 0; b < totalPackets - 1; b++) {
            checkedPackets[b] = 0;
        }
        System.out.println("What is the IP address of the network emulator?");
        Scanner input2 = new Scanner(System.in);
        String netIP = input2.nextLine();
        NetEmuIPAddress = InetAddress.getByName(netIP); 
        
        System.out.println("What would you like to do? Send or Receive?");
        Scanner input3 = new Scanner(System.in);
        String choice = input3.nextLine();
        
        if (choice.equalsIgnoreCase("send")) {         
            System.out.println("How much simulated network delay would you like (milliseconds)?");
            Scanner input = new Scanner(System.in);
            delay = input.nextLong();
            clientSocket = new DatagramSocket(7005);
            System.out.println("Preparing to send");
            CreatePackets();
            sendStart();
        }
        else {
            serverSocket = new DatagramSocket(7008);
            System.out.println("Waiting to receive...");
            receive();
        }

    }
    
    public static void receive() throws Exception{
        while (true) {

            ReceivePacket = new DatagramPacket(receiveData, receiveData.length);
            serverSocket.receive(ReceivePacket);
            IPAddress = ReceivePacket.getAddress();

            byte[] sendData = ReceivePacket.getData();

            ByteArrayInputStream in = new ByteArrayInputStream(sendData);

            ObjectInputStream is = new ObjectInputStream(in);

            Packet packet = (Packet) is.readObject();
            System.out.println("Data Packet received = " + packet);
            writer.println("Data Packet received = " + packet);

            if (packet.getPacketType() == 3) {
                SendEnd(packet);
                writer.close();
            } else {
                int seqNum = packet.getSeqNum();
                int windowSize = packet.getWindowSize();

                packet = new Packet(2, seqNum, windowSize, seqNum);

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                ObjectOutputStream os = new ObjectOutputStream(outputStream);
                os.writeObject(packet);
                sendData = outputStream.toByteArray();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, 7007);

                serverSocket.send(sendPacket);
                System.out.println("ACK Packet Sent = " + packet);
                writer.println("ACK Packet Sent = " + packet);
            }
        }
    }

    public static void SendEnd(Packet packet) throws IOException {
        int seqNum = packet.getSeqNum();
        int windowSize = packet.getWindowSize();

        packet = new Packet(4, seqNum, windowSize, seqNum);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(outputStream);
        os.writeObject(packet);
        sendData = outputStream.toByteArray();
        DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, 7007);

        serverSocket.send(sendPacket);
        System.out.println("EoT Packet Sent = " + packet);
        writer.println("EoT Packet Sent = " + packet);
    }
    
    public static void sendStart() throws Exception{
        int i = 0;
        int k = 0;
        int l = 0;
        
        
        while (!PacketArray.isEmpty()) {

            PrepareWindow();

            for (int c = 0; c < Window.size(); c++) {

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                ObjectOutputStream os = new ObjectOutputStream(outputStream);
                os.writeObject(PacketArray.get(l));
                byte[] sendData = outputStream.toByteArray();
                sendPacket = new DatagramPacket(sendData, sendData.length, NetEmuIPAddress, 7006);
                Send(sendPacket);
                Packet packet2 = (Packet) Window.get(l);
                
                System.out.println("SENT Data Packet - " + packet2.getSeqNum());
                writer.println("SENT Data Packet - " + packet2);

                l++;
                packetNumber++;

            }
            

            k = 0;
            l = 0;
            receivePacketNumber = 0;
            int timeOut = (int)delay*3;
            clientSocket.setSoTimeout(timeOut);

            for (i = 0; i < WindowSize; i++) {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);

                try {
                    clientSocket.receive(receivePacket);
                    byte[] receivedata = receivePacket.getData();

                    ByteArrayInputStream in = new ByteArrayInputStream(receivedata);
                    ObjectInputStream is = new ObjectInputStream(in);

                    try {

                        Packet packet2 = (Packet) is.readObject();
                        System.out.println("Packet ACK received - " + packet2);
                        writer.println("Packet ACK received - " + packet2);
                        

                        if (packet2.getPacketType() == 4) {
                            System.out.println("End of Transmission Received, Goodbye!");
                            writer.println("End of Transmission Received, Goodbye");
                            writer.close();
                            System.exit(0);
                        }
                        CheckOffReceivedPackets(packet2);
                        receivePacketNumber++;

                    } catch (ClassNotFoundException e) {

                        e.printStackTrace();

                    }

                } catch (Exception e) {
                    
                    System.out.println("Timeout on packet - " + PacketArray.get(0).getSeqNum());
                    writer.println("Timeout on packet - " + PacketArray.get(0));

                    break;
                }

            }
            //clientSocket.close();
        }
    }
    

    public static ArrayList CreatePackets() throws IOException {
        int i;

        for (i = 0; i < totalPackets; i++) {
            Packet packet = new Packet(1, i, WindowSize, i);
            PacketArray.add(packet);
        }
        Packet finalPacket = new Packet(3, totalPackets, WindowSize, totalPackets);
        PacketArray.add(finalPacket);
        return PacketArray;

    }

    public static void Send(DatagramPacket Packets) throws IOException {
        try {
            Thread.sleep(delay);
        } catch(InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        clientSocket.send(sendPacket);
        //send(Packets);
    }

    public static void CheckOffReceivedPackets(Packet packet) {
        //if packet is received, array at that seqnumber becomes 1. 1 means received. 0 means re-send. 
        for (Packet PacketArray1 : PacketArray) {

        }
        for (int i = 0; i < PacketArray.size(); i++) {
            if (PacketArray.get(i).getSeqNum() == packet.getSeqNum()) {
                PacketArray.remove(i);
            }
        }
        PacketArray.remove(packet);
    }

    public static void PrepareWindow() {
        int i;
        Window = new ArrayList(WindowSize);
        if (PacketArray.size() < WindowSize) {
            for (i = 0; i < PacketArray.size(); i++) {
                Window.add(PacketArray.get(i));
                
            }
        
            
        } else {
            for (i = 0; i < WindowSize; i++) {
                Window.add(PacketArray.get(i));
            }
        }

    }

}