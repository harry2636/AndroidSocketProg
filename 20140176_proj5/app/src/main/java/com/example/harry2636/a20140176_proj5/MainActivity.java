package com.example.harry2636.a20140176_proj5;

import static com.example.harry2636.a20140176_proj5.R.id.shift;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {
  private static final int FILE_CHOOSE_CODE = 1;
  private static final String resultFileName = "result.txt";
  private static final int MAXIMUM_MESSAGE_SIZE = 4096;

  EditText addressText;
  EditText portText;
  EditText shiftText;
  Button connectButton;
  Button fileChooseButton;
  TextView responseText;
  RadioButton encryptRadio;
  RadioButton decryptRadio;
  ByteBuffer message;
  byte[] messageBytes = null;
  byte[] targetBytes = null;
  String response = "";
  boolean isEncrypt = true;
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    /*
    Button fileChooser = (Button)findViewById(R.id.file_choose_button);
    fileChooser.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        Intent fileChooserIntent = new Intent(MainActivity.this, DocumentsSample.class);
        startActivity(fileChooserIntent);
      }
    });
    */

    //createSampleFile();

    addressText = (EditText)findViewById(R.id.address); //click done
    portText = (EditText)findViewById(R.id.port); //click done
    connectButton = (Button)findViewById(R.id.connect_button); //click done
    fileChooseButton = (Button)findViewById(R.id.file_choose_button);
    responseText = (TextView)findViewById(R.id.response); //click done
    shiftText = (EditText)findViewById(shift); //click done
    encryptRadio = (RadioButton)findViewById(R.id.encrypt); //click done
    decryptRadio = (RadioButton)findViewById(R.id.decrypt); //click done
    encryptRadio.setChecked(true);
    isEncrypt = true;

    connectButton.setOnClickListener(connectButtonOnClickListener);
    fileChooseButton.setOnClickListener(fileChooseButtonOnClickListener);
    encryptRadio.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        encryptRadio.setChecked(true);
        isEncrypt = true;
      }
    });
    decryptRadio.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        decryptRadio.setChecked(true);
        isEncrypt = false;
      }
    });

    addressText.setText("143.248.56.16");
    portText.setText("4003");
    shiftText.setText("0");
  }

  /* Referenced message creating in http://stackoverflow.com/questions/33074658/application-layer-protocol-header-fields-in-java */
  public void makeMessage(int offset, int op, int shift) {
    int length = targetBytes.length - offset + 8;
    if (length > MAXIMUM_MESSAGE_SIZE) {
      length = MAXIMUM_MESSAGE_SIZE;
    }
    if (op >= 2 ||  op < 0) {
      Log.e("exit system", op+"");
      System.exit(-1);
    }
    //Log.e("op: ", op+"");
    message = ByteBuffer.allocate(length);
    //message = ByteBuffer.allocate(data.length() + 8);
    message.put((byte)op); //TODO: change op bit depending on the radio button checked.
    message.put((byte)shift); //TODO; change shift bit depending on the shift text.
    //Log.e("shift byte: ", (byte)shift +"");
    message.putShort((short)0); // Garbage value that is not used.
    message.putInt(length);
    //message.putInt(data.length() + 8 );
    message.put(targetBytes, offset, length - 8);
    message.flip();
    messageBytes = new byte[message.remaining()];
    message.get(messageBytes);
    //Log.e("byteBuffer: ", message.toString());
    //Log.e("b length: ", messageBytes.length+"");
    //Log.e("first message", new String(messageBytes));
  }

  View.OnClickListener fileChooseButtonOnClickListener =
      new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
          intent.setType("*/*");
          startActivityForResult(intent, FILE_CHOOSE_CODE);
        }
      };

  /* Referenced client side socket programming in http://android-er.blogspot.kr/2014/02/android-sercerclient-example-client.html */
  View.OnClickListener connectButtonOnClickListener =
      new View.OnClickListener(){

        @Override
        public void onClick(View arg0) {
          int shift = Integer.parseInt(shiftText.getText().toString());
          if (targetBytes == null) {
            Toast.makeText(MainActivity.this, "File should be chosen before connection.",
                Toast.LENGTH_LONG).show();
          } else if (shift < 0) {
            Toast.makeText(MainActivity.this, "Shift value should be non-negative value.",
                Toast.LENGTH_LONG).show();
          } else {
            MyClientTask myClientTask = new MyClientTask(
                addressText.getText().toString(),
                Integer.parseInt(portText.getText().toString()), MainActivity.this, isEncrypt,
                shift);
            myClientTask.execute(targetBytes.length);
          }
        }};


  public class MyClientTask extends AsyncTask<Integer, String, Integer> {

    private String dstAddress;
    private int dstPort;
    private ProgressDialog progressDialog;
    private Context context;
    private int op;
    private int shift;

    MyClientTask(String addr, int port, Context cxt, boolean isEncrypt, int sh){
      dstAddress = addr;
      dstPort = port;
      context = cxt;
      if (isEncrypt) {
        op = 0;
      } else {
        op = 1;
      }

      shift = sh;
      if (shift < 0) {
        System.exit(-1);
      } else if (shift >= 26) {
        shift %= 26;
      }

    }

    @Override
    protected void onPreExecute() {
      response = "";
      progressDialog = new ProgressDialog(context);
      progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
      progressDialog.setMessage("Start");
      progressDialog.setCancelable(false);
      //progressDialog.setCanceledOnTouchOutside(false);
      progressDialog.show();

      super.onPreExecute();
    }

    @Override
    protected Integer doInBackground(Integer... params) {
      int offset = 0;
      int connection_cnt  = 0;
      int maxByteCnt = params[0];
      publishProgress("max", Integer.toString(maxByteCnt));

      //connection part
      while (offset != maxByteCnt) {
        offset = sendMessage(dstAddress, dstPort, offset, op, shift);
        /*
        if (offset == -2) {
          maxByteCnt = -2;
          break;
        }
        */
        connection_cnt++;
        Log.e("offset", offset+"");
        String opString = "";
        if (isEncrypt) {
          opString = "Encrypting now, ";
        } else {
          opString = "Decrypting now, ";
        }
        publishProgress("progress", Integer.toString(offset),
            opString + "current byte: " + Integer.toString(offset) + "B");
      }
      Log.e("connection_cnt: ", connection_cnt+"");
      createFile(response);

      return maxByteCnt;
    }

    @Override
    protected void onProgressUpdate(String... progress) {
      if (progress[0].equals("progress")) {
        progressDialog.setProgress(Integer.parseInt(progress[1]));
        progressDialog.setMessage(progress[2]);
      } else if (progress[0].equals("max")) {
        progressDialog.setMax(Integer.parseInt(progress[1]));
      }
    }

    @Override
    protected void onPostExecute(Integer result) {
      responseText.setText(response);
      response = "";

      progressDialog.dismiss();
      if (result >= 0) {
        Toast.makeText(context, "Received " + Integer.toString(result) + "B, saved to " + resultFileName,
            Toast.LENGTH_LONG).show();
      } else {
        Toast.makeText(context, "Invalid address and port number to connect.",
            Toast.LENGTH_LONG).show();
      }
      super.onPostExecute(result);
    }

  }

  public int sendMessage(String dstAddress, int dstPort, int offset, int op, int shift){
    Socket socket = null;
    OutputStream outputStream = null;
    InputStream inputStream = null;
    ByteArrayOutputStream byteArrayOutputStream = null;
    int bytesRead = 0;
    int accReadLen = 0;
    int targetLen = 0;

    // create and connect to socket
    try {
      socket = new Socket(dstAddress, dstPort);
    } catch (IOException e) { //Invalid ip or port.
      e.printStackTrace();
      Log.e("Exception", "socket");
      return offset;
      //return -2;
    }
    try {
      //make message from offset 0
      makeMessage(offset, op, shift);
      Log.e("messageBytes: ", messageBytes +"");

      //write messageBytes to outputstream
      outputStream = socket.getOutputStream();
      outputStream.write(messageBytes);
      outputStream.flush();
      inputStream = socket.getInputStream();
      //Log.e("message", message.array().toString());

      //prepare buffer for reading.
      byteArrayOutputStream =
          new ByteArrayOutputStream(MAXIMUM_MESSAGE_SIZE);
      byte[] buffer = new byte[MAXIMUM_MESSAGE_SIZE];
      //Log.e("startWhile", "0");


      //read Header bytes.
      int header_cnt = 0;
      while (true) {
        int avail = 0;
        avail = inputStream.available();
        //Log.e("header avail", avail +"");
        if (avail >= 8) {
          bytesRead = inputStream.read(buffer, 0, 8);
          if (bytesRead == -1) {
            inputStream.close();
            outputStream.close();
            socket.close();
            byteArrayOutputStream.close();
            return offset;
          }
          byte[] lenBytes = new byte[4];
          for (int i = 0; i < 4; i++) {
            lenBytes[i] = buffer[i+4];
          }
          targetLen = ByteBuffer.wrap(lenBytes).getInt();
          Log.e("received len", targetLen +"");
          accReadLen += 8;
          break;
        } else {
          if (header_cnt > 10) {
            inputStream.close();
            outputStream.close();
            socket.close();
            byteArrayOutputStream.close();
            return offset;
            //Log.e("wrote header again", "0");
            //header_cnt = 0;
          }
        }
        header_cnt++;
      }

      //read Contents.
      int cnt = 0;
      while (true) {
        //Log.e("looping: ", cnt+"");
        int avail;
        avail = inputStream.available();
        if (avail > 0) {
          Log.e("avaliable: ", avail + "");
          bytesRead = inputStream.read(buffer);
          if (bytesRead == -1) {
            socket.close();
            inputStream.close();
            outputStream.close();
            byteArrayOutputStream.close();
            return offset + accReadLen - 8;
          }

          accReadLen += bytesRead;
          Log.e("bytesRead: ", bytesRead + "");
          byteArrayOutputStream.write(buffer, 0, bytesRead);
          //Log.e("byteArrayOutputStream: ", byteArrayOutputStream.toString());
          Log.e("acc len: ", accReadLen+"");
          if (targetLen == accReadLen) {
            response += byteArrayOutputStream.toString("UTF-8");
            Log.e("acc response: ", response);
            break;
          }
        } else {
          if (cnt > 10) {
            response += byteArrayOutputStream.toString("UTF-8");
            socket.close();
            inputStream.close();
            outputStream.close();
            byteArrayOutputStream.close();
            return (offset + accReadLen - 8);
          }
        }

        cnt++;
      }

      Log.e("OutOfWhile", "1");

    } catch (UnknownHostException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      Log.e("Exception", "UnknownHostException: " + e.toString());
      //return offset + accReadLen - 8;
    } catch (IOException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
      Log.e("Exception", "IOException: " + e.toString());
      //return offset + accReadLen - 8;
    }finally{
      if(socket != null){
        try {
          inputStream.close();
          outputStream.close();
          byteArrayOutputStream.close();
          socket.close();

        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    }
    return offset + accReadLen - 8;
  }

  public void createFile(String contentString) {
    String dirname = Environment.getExternalStorageDirectory().getAbsolutePath();
    Log.e("dir", dirname);
    File dir = new File(dirname);
    final File file = new File(dir, resultFileName);
    if (!file.exists()) {
      try {
        file.createNewFile();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    try {
      FileOutputStream currFileStream = new FileOutputStream(file);
      currFileStream.write(contentString.getBytes());
      currFileStream.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }

    MediaScannerConnection.scanFile(getApplicationContext(), new String[]{Environment.getExternalStorageDirectory()
        .getAbsolutePath()+"/" + resultFileName}, null, null);

    /* Referenced file reading from http://stackoverflow.com/questions/12421814/how-can-i-read-a-text-file-in-android */
    try {
      BufferedReader br = new BufferedReader(new FileReader(file));
      String line;
      StringBuilder text = new StringBuilder();
      while ((line = br.readLine()) != null) {
        text.append(line);
        text.append('\n');
      }
      br.close();
      Log.e("resultFileResult: ", text.toString());
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void createSampleFile() {
    String dirname = Environment.getExternalStorageDirectory().getAbsolutePath();
    Log.e("dir", dirname);
    File dir = new File(dirname);
    final File file = new File(dir, "sample.txt");
    if (!file.exists()) {
      try {
        file.createNewFile();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    try {
      FileOutputStream currFileStream = new FileOutputStream(file);
      currFileStream.write("1234567890\n".getBytes());
      currFileStream.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }

    MediaScannerConnection.scanFile(getApplicationContext(), new String[]{Environment.getExternalStorageDirectory()
        .getAbsolutePath()+"/sample.txt"}, null, null);

    /* Referenced file reading from http://stackoverflow.com/questions/12421814/how-can-i-read-a-text-file-in-android */
    try {
      BufferedReader br = new BufferedReader(new FileReader(file));
      String line;
      StringBuilder text = new StringBuilder();
      while ((line = br.readLine()) != null) {
        text.append(line);
        text.append('\n');
      }
      br.close();
      Log.e("readResult: ", text.toString());
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }

  }

  /* refernecec http://stackoverflow.com/questions/10039672/android-how-to-read-file-in-bytes for file input reading */
  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    Log.e("onActivityResult", "0");
    Log.e("resultCode", requestCode+"");
    if (requestCode == FILE_CHOOSE_CODE) {
      if (data == null) {
        return;
      }
      Uri fileUri = data.getData();
      Log.e("fileUri", fileUri.getPath());

      //File file = new File(fileUri.getPath());
      //int size = (int) file.length();

      try {

        InputStream fileInputStream = getContentResolver().openInputStream(fileUri);
        int size = fileInputStream.available();
        Log.e("file size: ", size + "");
        targetBytes = new byte[size];
        fileInputStream.read(targetBytes, 0, targetBytes.length);
        fileInputStream.close();
      } catch (FileNotFoundException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }

  }
}

