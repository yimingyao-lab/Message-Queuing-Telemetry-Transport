package com.company;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Scanner;

import static java.lang.System.out;

public class Server {
    String subTag = ".sub.";
    String retainTag = ".retain.";

    ServerThread serverThread;
    List<ServerThread> serverThreadList = new ArrayList<>();
    List<String> topicsList = new ArrayList<>();
    String[] defaultTopics = new String[]{"WEATHER", "NEWS", "HEALTH", "SECURITY"}; //default topics.

    Base64.Decoder decoder = Base64.getDecoder();
    Base64.Encoder encoder = Base64.getEncoder();

    public Server() {
        ServerStart();
    }

    /**
     * This function is to start server.
     */
    void ServerStart() {
        for (String topics : defaultTopics) {
            topicsList.add(topics);
        }
        out.println("ServerStart:");
        try {
            ServerSocket listenSocket = new ServerSocket(9999);
            while (true) {
                Socket clientSocket = listenSocket.accept();
                serverThread = new ServerThread(clientSocket); //multiple thread.
                serverThreadList.add(serverThread);
                new Thread(serverThread).start();
            }
        } catch (IOException e) {
            out.println("IO Exception:" + e.getMessage());
        }
    }

    class ServerThread implements Runnable {
        String id = null;//user id
        Socket clientSocket = null;
        PrintWriter out = null;
        Scanner in = null;

        public ServerThread(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        /**
         * This method is to run for handling mutiple clients with different thread.
         */
        @Override
        public void run() {
            try {
                // Set up "in" to read from the client socket
                in = new Scanner(clientSocket.getInputStream());

                // Set up "out" to write to the client socket
                out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream())));

                while (true) {
                    String data = in.nextLine();
                    System.out.println(data);
                    String msg = "";

                    if (data.startsWith("DISC" + "|")) {
                        msg = "DISC_ACK";
                    } else if (data.startsWith("CONN" + "|")) {
                        msg = conn(data);
                    } else if (data.startsWith("PUB" + "|")) {
                        msg = pub(data);
                    } else if (data.startsWith("PUBRETAIN" + "|")) {
                        msg = pubretain(data);
                    } else if (data.startsWith("SUB" + "|")) {
                        msg = sub(data);
                    } else if (data.startsWith("UNSUB" + "|")) {
                        msg = unsub(data);
                    } else if (data.startsWith("LIST" + "|")) {
                        msg = topicList();
                    }

                    out.println(msg);//return msg to client
                    out.flush();

                    if (data.startsWith("DISC")) {
                        break;
                    }
                }

                // Handle exceptions
            } catch (IOException e) {
                out.println("IO Exception:" + e.getMessage());
            } finally {
                finishServerThread();
                try {
                    if (clientSocket != null) {
                        clientSocket.close(); //close connection.
                    }
                } catch (IOException e) {
                    // ignore exception on close
                }
            }
        }

        /**
         * clients can connect to a server and exchange message with it. This message will be sent from client to server
         * after socket connection being established.
         * @param data read from client
         * @return current state message
         * @throws IOException
         */
        String conn(String data) throws IOException {
            String msg = "ERROR";
            String[] datas = data.split("\\|");
            if (datas != null && datas.length == 2) {
                id = datas[1].substring(2);
                id = new String(decoder.decode(id)); //decoder with String (to determine what kind of topics)
                File file = new File(".");
                if (file.exists()) {
                    msg = "CONN_ACK";
                    String subMsg = "";
                    String retainMsg = "";
                    File[] files = file.getCanonicalFile().listFiles(); //generate file.
                    for (File f : files) {//find all sub topic
                        String name = f.getName();
                        if (name.startsWith(id + subTag)) {//find sub with this id
                            int index = name.indexOf(subTag);
                            if (index != -1) {
                                String subTopic = name.substring(index + subTag.length());
                                subTopic = new String(decoder.decode(subTopic));
                                subMsg += "\n\t" + subTopic;

                                //send stored retain msg to sub user
                                for (File retain : files) {//find all retain
                                    if (retain.getName().startsWith(retainTag)) {
                                        String retainTopic = retain.getName().substring(retainTag.length());
                                        retainTopic = new String(decoder.decode(retainTopic));
                                        if (isPubTopicMatchesSubTopic(retainTopic, subTopic)) { //check topic and pulish whether or not match.
                                            BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(retain)));
                                            String read = in.readLine();
                                            if (!read.isEmpty()) {
                                                retainMsg = "\n" + "PUBRETAIN" + ":" + subTopic + ":" + read;
                                            }
                                            in.close();
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (!subMsg.isEmpty()) {
                        msg += "\nTopics you have subscribed:";
                        msg += subMsg;
                    }
                    if (!retainMsg.isEmpty()) {
                        msg += retainMsg;
                    }
                }
            }
            return msg;
        }

        /**
         * This function is to return List of topics to msg.
         * @return String of topic list.
         */
        String topicList() {
            String msg = "Topics available:";
            for (String top : topicsList) {
                msg += "\n\t" + top;
            }
            return msg;
        }

        /**
         * This is a flag with which can be set when publishing a message. If a message is published with this flag set
         * this message will be stored on the server and any subscriber subscribes to this topic later will receive
         * this message right after subscription. Only one message can be retained for each topic which means the old
         * ones will be replaced by the latest retain message published to each topic.
         * @param data read from client
         * @return current state of user.
         * @throws IOException
         */
        String pubretain(String data) throws IOException {
            String msg = "ERROR";
            String[] datas = data.split("\\|");
            if (datas != null && datas.length == 4) {
                String topic = new String(decoder.decode(datas[1]));
                if (isTopicValid(topic)) { //check topic whether or not valid.
                    if (userHasSubForThisTopic(topic, id)) { //check whether or not user subscribe.
                        String m = new String(decoder.decode(datas[2]));
                        retainStore(topic, m);
                        for (ServerThread s : serverThreadList) {
                            s.pubToAllSubUser("PUBRETAIN", topic, m, id); //send message to all subscribe suers.
                        }
                        msg = "SUCCESS";
                    } else {
                        msg = "Need to sub first!";
                    }
                } else {
                    msg = "TOPIC invalid!";
                }
            }
            return msg;
        }

        /**
         * This function is to publish message through server to client.
         * @param data read from client
         * @return current state (SUCCESS or ERROR or TOPIC INVALID, NEED TO SUB FIRST)
         * @throws IOException
         */
        String pub(String data) throws IOException {
            String msg = "ERROR";
            String[] datas = data.split("\\|");
            if (datas != null && datas.length == 4) {
                String topic = new String(decoder.decode(datas[1]));
                if (isTopicValid(topic)) { // check the topic whether or not valid
                    if (userHasSubForThisTopic(topic, id)) { //check the user whether or not subscribe.
                        String m = new String(decoder.decode(datas[2]));
                        for (ServerThread s : serverThreadList) {
                            s.pubToAllSubUser("PUB", topic, m, id); //send message to all subscribe suers.
                        }
                        msg = "SUCCESS";
                    } else {
                        msg = "Need to sub first!";
                    }
                } else {
                    msg = "TOPIC invalid!";
                }
            }
            return msg;
        }

        /**
         * This function is to subscribe topics from server to client.
         * @param data read from client
         * @return current state.
         * @throws IOException
         */
        String sub(String data) throws IOException {
            String msg = "ERROR";
            String[] datas = data.split("\\|");
            if (datas != null && datas.length == 3) {
                String topic = datas[1];
                topic = new String(decoder.decode(topic));
                if (isTopicValid(topic)) { //check the topic whether of not valid.
                    subStore(topic, id);
                    msg = "SUCCESS";
                } else {
                    msg = "TOPIC invalid!";
                }
            }
            return msg;
        }

        /**
         * This method is to unsubscribe the topic that user subscribe.
         * @param data read from client
         * @return current state of user.
         * @throws IOException
         */
        String unsub(String data) throws IOException {
            String msg = "ERROR";
            String[] datas = data.split("\\|");
            if (datas != null && datas.length == 3) {
                String topic = datas[1];
                topic = new String(decoder.decode(topic));
                if (isTopicValid(topic)) { //check the topic whether or not valid.
                    unsubStore(topic, id);
                    msg = "SUCCESS";
                } else {
                    msg = "TOPIC invalid!";
                }
            }
            return msg;
        }

        /**
         * This method is for retain flag, that write message to the buffer.
         * @param topic
         * @param msg message to publish
         * @throws IOException
         */
        void retainStore(String topic, String msg) throws IOException {
            FileOutputStream out = new FileOutputStream(new File(".retain." + encoder.encodeToString(topic.getBytes())));
            out.write(msg.getBytes());
            out.close();
        }

        /**
         * This method is to store the topic of subscribe. create new file to record.
         * @param topic the topic of subscribe.
         * @param id subscribe of id.
         * @throws IOException
         */
        void subStore(String topic, String id) throws IOException {
            FileOutputStream out = new FileOutputStream(new File(id + ".sub." + encoder.encodeToString(topic.getBytes())));
            out.close();
        }

        /**
         * This method is to store the topic of unsubscribe. create new file to record, if unscribe the topic just delete the file.
         * @param topic the topic of subscribe.
         * @param id subscribe of id.
         * @throws IOException
         */
        void unsubStore(String topic, String id) throws IOException {
            File file = new File(id + ".sub." + encoder.encodeToString(topic.getBytes()));
            if (file.exists()) {
                file.delete();
            }
        }

        /**
         * This method for publish feature, send message through the server to client.
         * @param pubtype pub/retain type of publish
         * @param topic topic
         * @param msg message
         * @param from sender id
         * @throws IOException
         */
        public void pubToAllSubUser(String pubtype, String topic, String msg, String from) throws IOException {
            if (userHasSubForThisTopic(topic, id)) {
                out.println(from + ":" + pubtype + ":" + topic + ":" + msg);
                out.flush();
            }
        }

        /**
         * this method is to check the user whether or not subscribe this topic, return true -
         * subscribe, otherwise false.
         * @param pubTopic topic
         * @param id user id
         * @return true user has already subscribed, otherwise false
         * @throws IOException
         */
        boolean userHasSubForThisTopic(String pubTopic, String id) throws IOException {
            //Phase II
            File file = new File(".");
            if (file.exists()) {
                for (File f : file.getCanonicalFile().listFiles()) {
                    String name = f.getName();
                    //find all of sub topics of this id
                    if (f.isFile() && name.startsWith(id + subTag)) {
                        int index = name.indexOf(subTag);
                        if (index != -1) {
                            String subTopic = name.substring(index + subTag.length());
                            subTopic = new String(decoder.decode(subTopic));
                            if (isPubTopicMatchesSubTopic(pubTopic, subTopic)) { //because the topic publish message should same as subscribe topic
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }

        /**
         * This method is to check the public topic whether or not match with subscribe topic.
         * @param pubTopic topic of publish.
         * @param subTopic topic of subscribe.
         * @return mathes - true, otherwise false.
         */
        boolean isPubTopicMatchesSubTopic(String pubTopic, String subTopic) {
            String[] pubTopics = pubTopic.split("\\/");
            String[] subTopics = subTopic.split("\\/");
            if (pubTopics != null && subTopics != null) {
                //pub home/room
                //sub home/room/1
                if (pubTopic.contains("#")) {
                    //If there is a # sign, you need to extend the level to the longer level, and the extended part is filled with a plus sign
                    pubTopics = parseWildcard(pubTopics, Math.max(pubTopics.length, subTopics.length));
                }
                if (subTopic.contains("#")) {
                    //If there is a # sign, you need to extend the level to the longer level, and the extended part is filled with a plus sign
                    subTopics = parseWildcard(subTopics, Math.max(pubTopics.length, subTopics.length));
                }
                if (pubTopics.length == subTopics.length) {
                    //After the extension with the # sign,2 topics are of the same length.
                    for (int i = 0; i < pubTopics.length; i++) {
                        if (pubTopics[i].equals("#") || subTopics[i].equals("#")) {
                        } else if (pubTopics[i].equals("+") || subTopics[i].equals("+")) {
                        } else if (!pubTopics[i].equals(subTopics[i])) {
                            //return false;
                        }
                    }
                } else {
                    //After the expansion, the level length is different, certainly does not match
                    return false;
                }
            }
            return true;
        }

        /**
         * This method is to parse wildcard "+" sign can be anyone but in the home/
         * @param topics string of topic
         * @return the list of topics with new representation.
         */
        String[] parseWildcard(String[] topics, int items) {
            String[] topics2 = new String[items];
            for (String p : topics2) {
                p = "+";
            }
            for (int i = 0; i < topics.length; i++) {
                topics2[i] = topics[i];
            }
            return topics2;
        }

        /**
         * This method is to check the topic whether or not valid, return ture - valid, otherwise false.
         * @param topic topic we typed String.
         * @return true of valid, false of otherwise.
         */
        boolean isTopicValid(String topic) {
            //Phase II
            //Multi-level wildcard (#)
            if (countCharMatches(topic, '#') == 1) {
                int index = topic.indexOf('#');
                //# should be the last character
                if (index != topic.length() - 1) {
                    return false;
                }
                //# should always follow a topic level separator
                if (topic.charAt(index - 1) != '/') {
                    return false;
                }
            }

            //Single level wildcard (+)
            if (countCharMatches(topic, '+') == 1) {
                int matches = 0;
                String[] topics = topic.split("\\/");
                if (topics != null) {
                    for (String t : topics) {
                        if (t.equals("+")) {
                            matches++;
                        }
                    }
                }
                if (matches != 1) {
                    return false;
                }
            }
            return true;
        }

        /**
         * This method is to count special character and return the number..
         * @param str string of topic
         * @param ch special character
         * @return how many special character.
         */
        int countCharMatches(String str, char ch) {
            int matches = 0;
            for (int i = 0; i < str.length(); i++) {
                if (str.charAt(i) == ch) {
                    matches++;
                }
            }
            return matches;
        }

        /**
         * This method is to end for server thread, just remove thread to list.
         */
        void finishServerThread() {
            if (serverThreadList.contains(this)) {
                serverThreadList.remove(this);
            }
        }
    }
}
