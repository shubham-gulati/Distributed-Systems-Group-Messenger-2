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
import java.math.BigDecimal;
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
import java.util.concurrent.Executor;

/**
 * GroupMessengerActivity is the main Activity for the assignment.
 *
 * @author stevko
 *
 */
public class GroupMessengerActivity extends Activity {

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
    static int serverSequenceNumber = 0;
    String myport=null;
    static String failedClient = null;
    int val = 0;
    final ContentValues cv = new ContentValues();
    private final Uri cpUri  = buildUri("content", "edu.buffalo.cse.cse486586.groupmessenger2.provider");
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
                ownerMessages.put(msg, myPort);
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
                    try {
                        serverSocket.setSoTimeout(4500);
                        Socket socket = null;
                        socket = serverSocket.accept();
                        BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                        String message = input.readLine();
                        String[] parts = message.split("\\:");

                        if (parts[0].equals("FAILED")) {
                            String portId = parts[1];
                            removeFromList(portId);
                            out.println("ack");
                            out.close();
                        } else if (parts[3].equals(NEW)) {
                            String msg1 = parts[0];
                            String sending_port = parts[1];
                            String port = parts[2];
                            serverSequenceNumber += 1;

                            ownerMessages.put(msg1, sending_port);
                            MessagePacket messagePacket5 = new MessagePacket();
                            messagePacket5.setPortMappedId(myport);
                            messagePacket5.setFinalSequenceNumber(serverSequenceNumber);
                            messagePacket5.setDelivered(false);
                            messagePacket5.setMsg(msg1);
                            mappingsPackets.put(msg1, messagePacket5);

                            res.add(messagePacket5);
                            Collections.sort(res, new CustomComparator());
                            String wr = msg1 + ":" + messagePacket5.getFinalSequenceNumber() + ":" + messagePacket5.getPortMappedId();
                            out.println(wr);
                        } else {
                            String msg1 = parts[0];
                            String sq = parts[1];

                            if (Integer.parseInt(sq) > serverSequenceNumber) {
                                serverSequenceNumber = Integer.parseInt(sq);
                            }

                            MessagePacket messagePacket6 = mappingsPackets.get(msg1);
                            Log.e("mapping port to final "+msg1, parts[2]);
                            messagePacket6.setFinalSequenceNumber(Integer.parseInt(sq));
                            messagePacket6.setPortMappedId(parts[2]);
                            messagePacket6.setDelivered(true);

                            //Log.e("msg is "+messagePacket6.getMsg(), messagePacket6.getFinalSequenceNumber()+ " " + messagePacket6.getPortMappedId());
                            //Log.e("msg " + msg1, messagePacket6.getFinalSequenceNumber() + " " + messagePacket6.getPortMappedId() + " status "
                                //    + messagePacket6.isDelivered);
                            Collections.sort(res, new CustomComparator());

                            //Log.e("q message " + res.get(0).getMsg(), " queue status " + res.get(0).isDelivered);
                            while (res.size() > 0 && res.get(0).isDelivered) {
                                MessagePacket m9 = res.remove(0);
                                Log.e("publish "+m9.getMsg(), m9.getFinalSequenceNumber()+ " " + m9.getPortMappedId());
                                publishProgress(m9.getMsg(), String.valueOf(index));
                                Collections.sort(res, new CustomComparator());
                                //Log.e("After publishing final", String.valueOf(res.size()));
                                //Log.e("q after publish "+res.get(0).getMsg(), " queue status "+ res.get(0).isDelivered +" size of queue "+ String.valueOf(res.size()));
                                index++;
                            }

                            out.println("Message Updated");
                            out.close();
                        }
                    } catch (SocketTimeoutException e) {
//                        publishWhenException();
                    } catch (EOFException e) {
//                        publishWhenException();
                    } catch (IOException e) {
//                        publishWhenException();
                    } catch (NullPointerException e) {
//                        publishWhenException();
                    }
                }
            } catch (Exception e) {
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

        public void removeFromList(String portId) {

            for (int i=0;i< res.size();i++) {
                MessagePacket m9 = res.get(i);
                String owner_port = ownerMessages.get(m9.getMsg());
                if (owner_port.equals(portId) && !m9.isDelivered) {
                    res.remove(i);
                }
            }
            Collections.sort(res, new CustomComparator());
        }

        public void publishWhenException() {
            while (res.size() > 0) {
                if (res.get(0).isDelivered) {
                    publishProgress(res.get(0).getMsg(), String.valueOf(index));
                    res.remove(0);
                    Log.e("After publishing size", String.valueOf(res.size()));
                    index++;
                } else {
                    res.remove(0);
                }
            }
            Collections.sort(res, new CustomComparator());
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
            textView1.append(strReceived + " " + sequenceNumberFinal + "\t\n");
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


    public class FailedClient extends  AsyncTask<String, Void, Void> {

        @Override
        protected Void doInBackground(String... strings) {
            String port = strings[0];
            String remotePort;
            for (int i = 0; i < 5; i++) {
                remotePort = remotePorts[i];
                String msg = "FAILED" + ":" + port;
                Socket socket = null;
                try {
                    socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                            Integer.parseInt(remotePort));
                    BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                    //sending mesage
                    out.println(msg);
                    input.readLine();
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //socket.setSoTimeout(6000);
            }
            return null;
        }
    }



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
            int max_number = Integer.MIN_VALUE;
            String[] parts = null;
            List<BigDecimal> proposalList = new ArrayList<BigDecimal>();
            try {
                for (int i = 0; i < 5; i++) {
                    try {
                        remotePort = remotePorts[i];
                        //Log.e("sending message "+msgs[0]+" to", remotePort);
                        String msg = msgs[0] + ":" + myport + ":" + remotePort + ":" + NEW;
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(remotePort));
                        socket.setSoTimeout(2000);
                        BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);

                        //sending mesage
                        out.println(msg);

                        //receiving equence num
                        String m = input.readLine();
                        //Log.e("m is ", m);
                        parts = m.split(":");
                        String cal = parts[1] + "." + parts[2];
                        Log.e("adding to list "+cal, new BigDecimal(cal).toPlainString());
                        proposalList.add(new BigDecimal(cal));

                        out.close();
                        socket.close();
                    } catch (UnknownHostException e) {
                        failedClient = remotePort;
                        GroupMessengerActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                new FailedClient().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, failedClient);
                            }
                        });
                        Log.e(remotePort, "ClientTask UnknownHostException");
                    } catch (SocketTimeoutException e) {
                        failedClient = remotePort;
                        GroupMessengerActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                new FailedClient().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, failedClient);
                            }
                        });
                        e.printStackTrace();
                        Log.e(remotePort, "ClientTask SocketTimeout Exception");
                    } catch (EOFException e) {
                        failedClient = remotePort;
                        GroupMessengerActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                new FailedClient().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, failedClient);
                            }
                        });
                        e.printStackTrace();
                        Log.e(remotePort, "ClientTask EOF Exception");
                    } catch (IOException e) {
                        failedClient = remotePort;
                        GroupMessengerActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                new FailedClient().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, failedClient);
                            }
                        });
                        e.printStackTrace();
                        Log.e(remotePort, "IOException normal");
                    } catch (NullPointerException e) {
                        failedClient = remotePort;
                        GroupMessengerActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                new FailedClient().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, failedClient);
                            }
                        });
                        e.printStackTrace();
                        Log.e(remotePort, "IOException normal");
                    }
                }

                BigDecimal max = BigDecimal.ZERO;
                for (int i=0; i<proposalList.size(); i++) {
                    if (proposalList.get(i).compareTo(max) > 0) {
                        max = proposalList.get(i);
                    }
                }

                Log.e("max", String.valueOf(max));
                String[] val = String.valueOf(max).split("\\.");
                MessagePacket messagePacket5 = mappingsPackets.get(msgs[0]);
                Log.e("mapping port to "+msgs[0], Integer.parseInt(val[0]) +" "+ val[1]);
                messagePacket5.setFinalSequenceNumber(Integer.parseInt(val[0]));
                messagePacket5.setPortMappedId(val[1]);

                for (int i = 0; i < 5; i++) {
                    try {
                        remotePort = remotePorts[i];
                        String multi = msgs[0] + ":" + messagePacket5.getFinalSequenceNumber() + ":" + messagePacket5.getPortMappedId() + ":" + FINAL;
                        Log.e("sending final", multi);
                        Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                                Integer.parseInt(remotePort));
                        socket.setSoTimeout(2000);
                        BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                        out.println(multi);
                        //Log.e("message "+msgs[0] + " " +messagePacket4.getFinalSequenceNumber(), "from "+myport + "to" + remotePort);
                        input.readLine();
                        out.close();
                        socket.close();
                    } catch (UnknownHostException e) {
                        failedClient = remotePort;
                        GroupMessengerActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                new FailedClient().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, failedClient);
                            }
                        });
                        Log.e(remotePort, "ClientTask UnknownHostException");
                    } catch (SocketTimeoutException e) {
                        failedClient = remotePort;
                        GroupMessengerActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                new FailedClient().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, failedClient);
                            }
                        });
                        e.printStackTrace();
                        Log.e(remotePort, "ClientTask SocketTimeout Exception");
                    } catch (EOFException e) {
                        failedClient = remotePort;
                        GroupMessengerActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                new FailedClient().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, failedClient);
                            }
                        });
                        e.printStackTrace();
                        Log.e(remotePort, "ClientTask EOF Exception");
                    } catch (IOException e) {
                        failedClient = remotePort;
                        GroupMessengerActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                new FailedClient().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, failedClient);
                            }
                        });
                        e.printStackTrace();
                        Log.e(remotePort, "IOException normal");
                    } catch (NullPointerException e) {
                        failedClient = remotePort;
                        GroupMessengerActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                new FailedClient().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, failedClient);
                            }
                        });
                        e.printStackTrace();
                        Log.e(remotePort, "IOException normal");
                    }
                }

                //Log.e("message "+parts[0], "delivered");
                /*
                 * TODO: Fill in your client code that sends out a message.
                 */
                //socket.close();
            } catch (Exception e) {
                failedClient = remotePort;
                //failedNodeCount++;
                Log.e(remotePort, "ClientTask UnknownHostException");
            }
            return null;
        }
    }
}
