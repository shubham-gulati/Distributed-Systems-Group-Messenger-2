package edu.buffalo.cse.cse486586.groupmessenger2;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
public class GroupMessengerActivity2 extends Activity {

    static final String TAG = GroupMessengerActivity.class.getSimpleName();
    static final String REMOTE_PORT0 = "11108";
    static final String REMOTE_PORT1 = "11112";
    static final String REMOTE_PORT2 = "11116";
    static final String REMOTE_PORT3 = "11120";
    static final String REMOTE_PORT4 = "11124";
    static final String NEW = "NEW";
    static final String PROPOSED = "PROPOSED";
    static final String FINAL = "FINAL";
    static final String FAILED = "FAILED";
    static final int SERVER_PORT = 10000;
    String[] remotePorts = new String[]{REMOTE_PORT0, REMOTE_PORT1, REMOTE_PORT2, REMOTE_PORT3, REMOTE_PORT4};
    static ArrayList<String> REMOTE_PORTS = new ArrayList<String>(Arrays.asList(REMOTE_PORT0,
            REMOTE_PORT1, REMOTE_PORT2, REMOTE_PORT3, REMOTE_PORT4));
    static final int timeout_val = 2000;
    static int propoedSequenceNumber = 1;
    static int serverSequenceNumber = 0;
    String myport;
    String packetPort;
    int sendSequence;
    static String failedClient = null;
    int fCount = 0;
    int proposalNumber = 0;
    int val = 0;
    final ContentValues cv = new ContentValues();
    private final Uri cpUri  = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
    static Map<String, Integer> countMessageNumbers = new HashMap();
    static Map<String, Integer> maxSequenceNumberArrived = new HashMap();
    static Map<String, String> ownerMessages = new HashMap<String, String>();
    static Map<String, MessagePacket> mappingsPackets = new HashMap<String, MessagePacket>();
    List<MessagePacket> res = new ArrayList<MessagePacket>();
    GroupMessengerProvider groupMessengerProvider;
    int index = 0;
    static Map<String, HashMap<String, Integer>> proposals = new HashMap<String, HashMap<String, Integer>>();
    HashSet<Integer> alreadyUsed = new HashSet<Integer>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_messenger);
        groupMessengerProvider = new GroupMessengerProvider();

        /*
         * TODO: Use the TextView to display your messages. Though there is no grading component
         * on how you display the messages, if you implement it, it'll make your debugging easier.
         */
        TextView tv = (TextView) findViewById(R.id.textView1);
        tv.setMovementMethod(new ScrollingMovementMethod());

        /*
         * Registers OnPTestClickListener for "button1" in the layout, which is the "PTest" button.
         * OnPTestClickListener demonstrates how to access a ContentProvider.
         */
        findViewById(R.id.button1).setOnClickListener(
                new OnPTestClickListener(tv, getContentResolver()));


        final Button button4 = (Button) findViewById(R.id.button4);
        final EditText editText = (EditText) findViewById(R.id.editText1);
        final Editable text = editText.getText();


        TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        myport = myPort;

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
            return;
        }

        button4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String msg = editText.getText().toString();
                editText.setText("");
                MessagePacket messagePacketself = new MessagePacket();
                messagePacketself.setMsg(msg);
                messagePacketself.setPortMappedId(myPort);
                messagePacketself.setDelivered(false);
                mappingsPackets.put(msg, messagePacketself);
                countMessageNumbers.put(msg, 0);
                ownerMessages.put(msg, myport);
                maxSequenceNumberArrived.put(msg, 0);
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg, myPort, "false");
            }
        });

        /*
         * TODO: You need to register and implement an OnClickListener for the "Send" button.
         * In your implementation you need to get the message from the input box (EditText)
         * and send it to other AVDs.
         */
    }

    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_group_messenger, menu);
        return true;
    }

    /***
     * ServerTask is an AsyncTask that should handle incoming messages. It is created by
     * ServerTask.executeOnExecutor() call in SimpleMessengerActivity.
     *
     * Please make sure you understand how AsyncTask works by reading
     * http://developer.android.com/reference/android/os/AsyncTask.html
     *
     * @author stevko
     * We need to Maintain FIFO and total ordering in this.
     */

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {
        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            try {
                /** Here we are continuously creating socket coz it breaks after one communication **/
                while (true) {
                    Socket socket = null;
                    socket = serverSocket.accept();
                    BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    String message = input.readLine();

                    ObjectOutputStream op = new ObjectOutputStream(socket.getOutputStream());
                    op.writeUTF("dummy");
                    op.flush();

                    String[] parts = message.split("\\:");
                    final String msg1 = parts[0];
                    final String port = parts[1];
                    final String remote_port = parts[2];
                    String message_type = parts[3];

                    if (message_type.equals(NEW)) {
                        serverSequenceNumber += 1;
                        ownerMessages.put(msg1, port);
                        maxSequenceNumberArrived.put(msg1, serverSequenceNumber);

                        MessagePacket messagePacket;

                        if (mappingsPackets.get(msg1) == null) {
                            messagePacket = new MessagePacket();
                            messagePacket.setPortMappedId(myport);
                            messagePacket.setFinalSequenceNumber(serverSequenceNumber);
                            messagePacket.setDelivered(false);
                            messagePacket.setMsg(msg1);
                            mappingsPackets.put(msg1, messagePacket);
                        } else {
                            messagePacket = mappingsPackets.get(msg1);
                        }
                        res.add(messagePacket);

                        GroupMessengerActivity2.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (alreadyUsed.contains(serverSequenceNumber)) {
                                    serverSequenceNumber += 1;
                                }
                                addToSet(serverSequenceNumber);
                                Log.e("sending for "+port+ " " +msg1, String.valueOf(serverSequenceNumber));
                                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg1, port, "true", myport, String.valueOf(serverSequenceNumber));
                            }
                        });

                    } else if (message_type.equals(PROPOSED)) {
                        propoedSequenceNumber = Integer.parseInt(parts[4]);

                        if (proposals.get(msg1) != null) {
                            HashMap<String, Integer> s = proposals.get(msg1);
                            s.put(port, propoedSequenceNumber);
                            proposals.put(msg1, s);
                        } else {
                            HashMap<String, Integer> p = new HashMap<String, Integer>();
                            p.put(port, propoedSequenceNumber);
                            proposals.put(msg1, p);
                        }

                        if (propoedSequenceNumber > serverSequenceNumber) {
                            serverSequenceNumber = propoedSequenceNumber + 1;
                        }

                        increment(msg1);
                        val = getVal(msg1);
                        int maxSeq = maxSequenceNumberArrived.get(msg1);
                        packetPort = ownerMessages.get(msg1);

                        if (failedClient != null) {
                            HashMap<String, Integer> f = proposals.get(msg1);
                            if (f.size() == 4 && !f.containsKey(failedClient)) {
                                Log.e("inside failedc inc", failedClient);
                                increment(msg1);
                            }
                        }

                        if (propoedSequenceNumber > maxSeq) {
                            //Log.e(msg1, "current max "+maxSeq);
                            //Log.e("proposal from port "+port, String.valueOf(propoedSequenceNumber));
                            maxSequenceNumberArrived.put(msg1, propoedSequenceNumber);
                            //Log.e("propoed for "+msg1, String.valueOf(propoedSequenceNumber));
                            MessagePacket messagePacket1 = mappingsPackets.get(msg1);
                            //Log.e("proposed current port "+messagePacket1.getPortMappedId(), "from port "+port);
                            messagePacket1.setPortMappedId(port);
                        } else if (propoedSequenceNumber == maxSeq) {
                            //Log.e("msg is", msg1);
                            //Log.e("remote port "+remote_port, "port "+port);
                            MessagePacket messagePacket1 = mappingsPackets.get(msg1);
                            if (Integer.parseInt(messagePacket1.getPortMappedId()) < Integer.parseInt(port)) {
                                //Log.e("inside port change", port);
                                packetPort = port;
                                //Log.e("current port is", messagePacket1.getPortMappedId());
                                messagePacket1.setPortMappedId(port);
                                //Log.e("port set to", messagePacket1.getPortMappedId());
                            }
                        }

                        if (val == 5) {
                            Log.e("inside val", String.valueOf(val));
                            final MessagePacket messagePacket2 = mappingsPackets.get(msg1);
                            messagePacket2.setFinalSequenceNumber(maxSequenceNumberArrived.get(msg1));
                            messagePacket2.setDelivered(true);
                            //Log.e("setting "+msg1, String.valueOf(messagePacket2.getFinalSequenceNumber()));
                            //Log.e(messagePacket2.getMsg() + " " + messagePacket2.getPortMappedId(), String.valueOf(messagePacket2.isDelivered));

                            GroupMessengerActivity2.this.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, msg1, port, "final", messagePacket2.getPortMappedId(), String.valueOf(maxSequenceNumberArrived.get(msg1)));
                                }
                            });
                        }

                    } else if (message_type.equals(FINAL)) {
                        int current_final_seq = Integer.parseInt(parts[4]);

                        if (current_final_seq > serverSequenceNumber) {
                            serverSequenceNumber = current_final_seq + 1;
                        }

                        MessagePacket messagePacket2 = mappingsPackets.get(msg1);
                        if (messagePacket2 == null) {
                            messagePacket2 = new MessagePacket();
                        }
                        messagePacket2.setDelivered(true);
                        messagePacket2.setFinalSequenceNumber(Integer.parseInt(parts[4]));
                        messagePacket2.setPortMappedId(parts[5]);

                        Collections.sort(res, new CustomComparator());

                        while (res.size() > 0 && res.get(0).isDelivered) {
                            Collections.sort(res, new CustomComparator());
                            MessagePacket m9 = res.remove(0);
                            //Log.e(m9.getMsg(), m9.getFinalSequenceNumber()+ " " + m9.getPortMappedId());
                            publishProgress(m9.getMsg(), String.valueOf(index));
                            index++;
                        }

                    } else if (message_type.equals(FAILED)) {

                    }
                }
            } catch (SocketTimeoutException e) {
                failedClient = myport;
                Log.e("failed client "+failedClient, "SocketTimeout");
                //e.printStackTrace();
            } catch (UnknownHostException e) {
                failedClient = myport;
                Log.e("failed client "+failedClient, "UnknownHost");
                //e.printStackTrace();
            } catch (IOException e) {
                failedClient = myport;
                Log.e("failed client "+failedClient, "IOException");
                //here we need to check if any exception occcurs
                //e.printStackTrace();
            }

            /*
             * TODO: Fill in your server code that receives messages and passes them
             * to onProgressUpdate().
             */
            return null;
        }

        public synchronized void addToSet(int no) {
            if (alreadyUsed.contains(no)) {
                serverSequenceNumber+=1;
            }
            alreadyUsed.add(no);
        }


        public synchronized void increment(String message) {
            //Log.e("msg is", message+" "+myport);
            int val = countMessageNumbers.get(message);
            //Log.e("val is", msg1 +" "+ val);
            countMessageNumbers.put(message, val + 1);
        }

        public synchronized int getVal(String message) {
            return countMessageNumbers.get(message);
        }

        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
            String strReceived = strings[0].trim();
            String sequenceNumberFinal = strings[1];
            /*
             * The following code creates a file in the AVD's internal storage and stores a file.
             *
             * For more information on file I/O on Android, please take a look at
             * http://developer.android.com/training/basics/data-storage/files.html
             */
            TextView textView1 = (TextView) findViewById(R.id.textView1);
            textView1.append(strReceived + "\t\n");
            textView1.append("\n");

            try {
                cv.put("key", sequenceNumberFinal);
                cv.put("value", strReceived);
                getContentResolver().insert(cpUri, cv);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("Some Error Occurred", "File write failed");
            }
            return;
        }
    }


    public void readDummyMessage(Socket socket) throws IOException {
        ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
        String line = input.readUTF();
    }

    public void cleanUpQueue(String port) {
        Log.e("cleaning up", res.size()+" ");
        for (int i=0;i< res.size();i++) {
            MessagePacket m3 = res.get(i);
            String original_port = ownerMessages.get(m3.getMsg());
            if (original_port.equals(port) && !m3.isDelivered) {
                res.remove(i);
            }
        }
        Log.e("cleaning done", res.size()+" ");
    }

    public void informAVDs() {}

    /***
     * ClientTask is an AsyncTask that should send a string over the network.
     * It is created by ClientTask.executeOnExecutor() call whenever OnKeyListener.onKey() detects
     * an enter key press event.
     *
     * @author stevko
     *
     */
    private class ClientTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... msgs) {
            //String remotePort1 = "";
            String remotePort = "";
            try {
                if (msgs[2].equals("false")) {
                    for (int i = 0; i < 5; i++) {
                        remotePort = remotePorts[i];
                        //Log.e("sending mesage "+msgs[0]+" to", remotePort);
                        String msg = msgs[0] + ":" + myport + ":" + remotePort + ":" + NEW;
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(remotePort));
                        //socket.setSoTimeout(6000);
                        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                        out.println(msg);
                        readDummyMessage(socket);
                    }
                } else if (msgs[2].equals("true")) {
                    remotePort = msgs[1];
                    //Log.e("inside proposed "+myport, remotePort1);
                    String msg = msgs[0] + ":" + myport + ":" + remotePort + ":" + PROPOSED + ":" + msgs[4];
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(remotePort));
                    //socket.setSoTimeout(6000);
                    PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                    out.println(msg);
                    readDummyMessage(socket);
                } else {
                    for (int i = 0; i < 5; i++) {
                        remotePort = remotePorts[i];
                        String msg = msgs[0] + ":" + myport + ":" + remotePort + ":" + FINAL + ":" + msgs[4] + ":" + msgs[3];
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(remotePort));
                        //socket.setSoTimeout(6000);
                        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                        out.println(msg);
                        readDummyMessage(socket);
                    }
                }
                /*
                 * TODO: Fill in your client code that sends out a message.
                 */
                //socket.close();
            } catch (UnknownHostException e) {
                failedClient = remotePort;
                cleanUpQueue(remotePort);
                //failedNodeCount++;
                Log.e(remotePort, "ClientTask UnknownHostException");
            } catch (SocketTimeoutException e) {
                failedClient = remotePort;
                cleanUpQueue(remotePort);
                //failedNodeCount++;
                e.printStackTrace();
                Log.e(remotePort, "ClientTask SocketTimeout Exception");
            } catch (EOFException e) {
                failedClient = remotePort;
                cleanUpQueue(remotePort);
                //failedNodeCount++;
                e.printStackTrace();
                Log.e(remotePort, "ClientTask EOF Exception");
            } catch (IOException e) {
                failedClient = remotePort;
                cleanUpQueue(remotePort);
                //failedNodeCount++;
                e.printStackTrace();
                Log.e(remotePort, "IOException normal");
            }
            return null;
        }
    }
}
