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
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {
  private static final int FILE_CHOOSE_CODE = 1;
  private static final int MAXIMUM_MESSAGE_SIZE = 4096;

  EditText addressText;
  EditText portText;
  EditText shiftText;
  EditText outputText;
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
  Socket socket = null;
  boolean isDisrupted = false;
  int connectionCnt = 0;
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    addressText = (EditText)findViewById(R.id.address); //click done
    portText = (EditText)findViewById(R.id.port); //click done
    outputText = (EditText)findViewById(R.id.output); // click done
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
    outputText.setText("result.txt");
    shiftText.setText("1");
  }

  /* Referenced message creating in http://stackoverflow.com/questions/33074658/application-layer-protocol-header-fields-in-java */
  public void makeMessage(int offset, int op, int shift) {
    if (op >= 2 ||  op < 0) {
      Log.e("exit system", op+"");
      System.exit(-1);
    }

    int length = targetBytes.length - offset + 8;
    if (length > MAXIMUM_MESSAGE_SIZE) {
      length = MAXIMUM_MESSAGE_SIZE;
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

  View.OnClickListener connectButtonOnClickListener =
      new View.OnClickListener(){

        @Override
        public void onClick(View arg0) {

          if (addressText.getText().toString().length() == 0) {
            Toast.makeText(MainActivity.this, "Address should be written before connecition.",
                Toast.LENGTH_LONG).show();
          } else if (portText.getText().toString().length() == 0) {
            Toast.makeText(MainActivity.this, "Port should be written before connecition.",
                Toast.LENGTH_LONG).show();
          } else if (shiftText.getText().toString().length() == 0) {
            Toast.makeText(MainActivity.this, "Shift value should be written before connecition.",
                Toast.LENGTH_LONG).show();
          } else if (targetBytes == null) {
            Toast.makeText(MainActivity.this, "File should be chosen before connection.",
                Toast.LENGTH_LONG).show();
          } else if (Integer.parseInt(shiftText.getText().toString()) < 0) {
            Toast.makeText(MainActivity.this, "Shift value should be non-negative value.",
                Toast.LENGTH_LONG).show();
          } else if (outputText.getText().toString().length() == 0) {
            Toast.makeText(MainActivity.this, "Output FileName should be written before connecition.",
                Toast.LENGTH_LONG).show();
          } else {
            MyClientTask myClientTask = new MyClientTask(
                addressText.getText().toString(),
                Integer.parseInt(portText.getText().toString()), MainActivity.this, isEncrypt,
                Integer.parseInt(shiftText.getText().toString()));
            myClientTask.execute(targetBytes.length);
          }
        }};

/* Referenced client side socket programming in http://android-er.blogspot.kr/2014/02/android-sercerclient-example-client.html */
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
      connectionCnt = 0;
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
      int maxByteCnt = params[0];
      publishProgress("max", Integer.toString(maxByteCnt));

      //connection part
      int invaildCnt = 0;
      while (offset != maxByteCnt) {
        offset = sendMessage(dstAddress, dstPort, offset, op, shift);
        //Log.e("offset", offset+"");
        String opString = "";
        if (isEncrypt) {
          opString = "Encrypting now, ";
        } else {
          opString = "Decrypting now, ";
        }
        publishProgress("progress", Integer.toString(offset),
            opString + "current byte: " + Integer.toString(offset) + "B");
        if (offset < -100) {
          maxByteCnt = -1;
          break;
        }
        if (offset == 0) {
          invaildCnt++;
          if (invaildCnt > 100) {
            maxByteCnt = -2;
            break;
          }
        }
      }
      Log.e("connection_cnt: ", connectionCnt+"");

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
      connectionCnt = 0;
      createFile(response);
      responseText.setText("Response from server is done");
      try {
        if (socket != null) {
          socket.close();
        }
        socket = null;
        isDisrupted = false;
      } catch (IOException e) {
        e.printStackTrace();
      }


      progressDialog.dismiss();
      if (result >= 0) {
        Toast.makeText(context, "Received " + Integer.toString(result) + "B, saved to " + outputText.getText().toString(),
            Toast.LENGTH_LONG).show();
      } else {
        Toast.makeText(context, "Invalid address and port number to connect.",
            Toast.LENGTH_LONG).show();
      }
      super.onPostExecute(result);
    }

  }

  public int sendMessage(String dstAddress, int dstPort, int offset, int op, int shift){
    OutputStream outputStream = null;
    InputStream inputStream = null;
    ByteArrayOutputStream byteArrayOutputStream = null;
    int bytesRead = 0;
    int accReadLen = 0;
    int targetLen = 0;

    // create and connect to socket

    try {
      if (socket == null || isDisrupted == true) {
        Log.e("assign socket", "0");
        connectionCnt++;
        socket = new Socket(dstAddress, dstPort);
        socket.setSoTimeout(5000);
        isDisrupted = false;
      }
      Log.e("isDisrupted: ", isDisrupted +"");
      //make message from offset 0
      makeMessage(offset, op, shift);
      //Log.e("messageBytes: ", messageBytes +"");

      //write messageBytes to outputstream
      outputStream = socket.getOutputStream();
      outputStream.write(messageBytes);
      outputStream.flush();
      inputStream = socket.getInputStream();
      Log.e("message", message.array().toString());

      //prepare buffer for reading.
      byteArrayOutputStream =
          new ByteArrayOutputStream(MAXIMUM_MESSAGE_SIZE);
      byte[] buffer = new byte[MAXIMUM_MESSAGE_SIZE];
      //Log.e("startWhile", "0");

      //read Header bytes.
      while (true) {
        Log.e("header read start", "0");
        bytesRead = inputStream.read(buffer, 0, 8);
        Log.e("header read done", bytesRead+"");
        if (bytesRead == -1) {
          isDisrupted = true;
          inputStream.close();
          outputStream.close();
          socket.close();
          byteArrayOutputStream.close();
          //Log.e("bytesRead", "negative");
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
      }

      //read Contents.
      while (true) {
        Log.e("data read start", "0");
        bytesRead = inputStream.read(buffer);
        Log.e("data read done", bytesRead+"");
        if (bytesRead == -1) {
          isDisrupted = true;
          inputStream.close();
          outputStream.close();
          socket.close();
          byteArrayOutputStream.close();
          return offset + accReadLen - 8;
        }

        accReadLen += bytesRead;
        //Log.e("bytesRead: ", bytesRead + "");
        byteArrayOutputStream.write(buffer, 0, bytesRead);
        byteArrayOutputStream.flush();
        //Log.e("byteArrayOutputStream: ", byteArrayOutputStream.toString());
        //Log.e("acc len: ", accReadLen+"");
        /*
        String a = byteArrayOutputStream.toString("UTF-8");
        if (a.length() != bytesRead) {
          Log.e("streamWriteSize: ", a.length()+" bytes Read: " + bytesRead);
        }
        */
        response += byteArrayOutputStream.toString("UTF-8");
        byteArrayOutputStream.reset();
        if (targetLen == accReadLen) {
          //Log.e("acc response: ", response);
          Log.e("accReadLen = targetLen", accReadLen+"");
          break;
        }
      }

      Log.e("OutOfWhile", "1");

    } catch (UnknownHostException e) {
      // TODO Auto-generated catch block
      isDisrupted = true;
      e.printStackTrace();
      Log.e("Exception", "UnknownHostException: " + e.toString());
      //return offset + accReadLen - 8;
    } catch (SocketTimeoutException e) {
      isDisrupted = true;
      e.printStackTrace();
      Log.e("Exception", "SocketTimeoutException");
    } catch (IOException e) {
      // TODO Auto-generated catch block
      isDisrupted = true;
      e.printStackTrace();
      Log.e("Exception", "IOException: " + e.toString());
      //return offset + accReadLen - 8;
    } finally{
      if(socket != null){
        try {
          if (byteArrayOutputStream != null) {
            byteArrayOutputStream.close();
          }
        } catch (IOException e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
      }
    }
    if (accReadLen < 8) {
      return offset;
    }
    return offset + accReadLen - 8;
  }

  public void createFile(String contentString) {
    String dirname = Environment.getExternalStorageDirectory().getAbsolutePath();
    Log.e("dir", dirname);
    File dir = new File(dirname);
    final File file = new File(dir, outputText.getText().toString());
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
      currFileStream.flush();
      Log.e("FileLength1_open: ", targetBytes.length + "");
      Log.e("FileLength2_create: ", contentString.length() + "");
      currFileStream.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }

    MediaScannerConnection.scanFile(getApplicationContext(), new String[]{Environment.getExternalStorageDirectory()
        .getAbsolutePath()+"/" + outputText.getText().toString()}, null, null);

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
      //Log.e("resultFileResult: ", text.toString());
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /*
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
      currFileStream.flush();
      currFileStream.close();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }

    MediaScannerConnection.scanFile(getApplicationContext(), new String[]{Environment.getExternalStorageDirectory()
        .getAbsolutePath()+"/sample.txt"}, null, null);

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
  */

  /* referenced http://stackoverflow.com/questions/10039672/android-how-to-read-file-in-bytes for file input reading */
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
        Log.e("File Size: ", size + "");
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

