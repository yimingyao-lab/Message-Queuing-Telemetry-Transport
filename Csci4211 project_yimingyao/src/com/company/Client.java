package com.company;

import java.io.*;
import java.net.Socket;
import java.util.Base64;

public class Client {
    Socket clientSocket = null;
    BufferedReader in = null;
    PrintWriter out = null;
    BufferedReader typed = null;
    Thread threadReadInMsg = null;

    Base64.Encoder encoder = Base64.getEncoder();

    public Client() {
        ClientStart();
    } // constructor

    /**
     This method is to show the tutorials (options) user can do.
     */
    void showMenu() {
        System.out.println("Features available:");
        System.out.println("\tCONN: connect to a server");
        System.out.println("\tDISC: disconnect from a server");
        System.out.println("\tLIST: query all the topics");
        System.out.println("\tSUB: subscribe to topics");
        System.out.println("\tUNSUB: unsubscribe to topics");
        System.out.println("\tPUBRETAIN: subscribe to topics with retain flag");
        System.out.println("\tPUB: publish messages to any topic");
    }

    /**
     * Clients can connect to a server and exchange messages with it.
     * This message will be sent from client to server after socket connection being established.
     * @throws IOException
     */
    void conn() throws IOException {
        clientSocket = new Socket("localhost", 9999);
        in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream())));
        threadReadInMsg = new Thread(new ReadInMsgFromServer(in));
        threadReadInMsg.start();
    }

    /**
     * Client can send request to subscribe to topics they are interested in
     * @param cmd
     * @return updated command with encoding topic bytes.
     * @throws IOException
     */

    String sub(String cmd) throws IOException {
        System.out.println("Type TOPIC to sub:");
        String topic = typed.readLine();
        cmd += "|" + encoder.encodeToString(topic.getBytes());
        return cmd;
    }

    /**
     * This function is to unsubscribe the topics, users will not receive messages anymore.
     * @param cmd
     * @return command with following bytes.
     * @throws IOException
     */
    String unsub(String cmd) throws IOException {
        System.out.println("Type TOPIC to unsub:");
        String topic = typed.readLine();
        cmd += "|" + encoder.encodeToString(topic.getBytes());
        return cmd;
    }

    /**
     * Clients can send request to publish messages to any topic The server handles.
     * @param cmd
     * @return return update of command with encoding bytes.
     * @throws IOException
     */
    String pub(String cmd) throws IOException {
        System.out.println("Type TOPIC to pub:");
        String topic = typed.readLine();
        System.out.println("Type msg to pub:");
        String msg = typed.readLine();
        cmd += "|" + encoder.encodeToString(topic.getBytes()) + "|" + encoder.encodeToString(msg.getBytes());
        return cmd;
    }

    /**
     * Main function to start client.
     */
    void ClientStart() {
        try {
            typed = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("Type your ID:");
            String id = typed.readLine(); //read for id
            if (!id.isEmpty()) {
                showMenu(); //show menu
                String cmd = "";
                while ((cmd = typed.readLine()) != null) {//read in input
                    if (clientSocket == null && cmd.equals("CONN")) {
                        conn();
                    } else if (clientSocket != null && cmd.equals("DISC")) {
                    } else if (clientSocket != null && cmd.equals("SUB")) {
                        cmd = sub(cmd);
                    } else if (clientSocket != null && cmd.equals("UNSUB")) {
                        cmd = unsub(cmd);
                    } else if (clientSocket != null && cmd.equals("PUB")) {
                        cmd = pub(cmd);
                    } else if (clientSocket != null && cmd.equals("PUBRETAIN")) {
                        cmd = pub(cmd);
                    } else if (clientSocket != null && cmd.equals("LIST")) {
                    } else {
                        if (clientSocket == null) {
                            System.out.println("Need to connect server first!");
                        } else if (clientSocket != null && cmd.equals("CONN")) {
                            System.out.println("CONN Dup!"); //repeatedly connection
                        } else {
                            System.out.println("ERROR");
                        }
                        showMenu();
                        continue;
                    }

                    if (clientSocket != null) {
                        out.println(cmd + "|id" + encoder.encodeToString(id.getBytes()));
                        out.flush();

                        if (cmd.equals("DISC")) {
                            while (threadReadInMsg.isAlive()) {
                                Thread.sleep(100); //thread sleep for 100 ms.
                            }
                            break;
                        }
                    }
                }
            } else {
                System.out.println("ID empty!");
            }
        } catch (IOException | InterruptedException e) {
            System.out.println("IO Exception:" + e.getMessage());
        } finally {
            System.out.println("Bye!");
            try {
                if (clientSocket != null) {
                    clientSocket.close(); //close socket.
                    clientSocket = null;
                }
            } catch (IOException e) {
                // ignore exception on close
            }
        }
    }


    class ReadInMsgFromServer implements Runnable {
        BufferedReader in = null;

        // read message from server.
        public ReadInMsgFromServer(BufferedReader in) {
            this.in = in;
        }

        /**
         * this method is to read the data/message from server for communicate.
         */
        @Override
        public void run() {
            try {
                String data;//read in from server
                while ((data = in.readLine()) != null) {
                    System.out.println(data);
                    if (data.equals("DISC_ACK")) {
                        break;
                    }
                }
                // Handle exceptions
            } catch (IOException e) {
                System.out.println("IO Exception:" + e.getMessage());
            } finally {
            }
        }
    }
}
