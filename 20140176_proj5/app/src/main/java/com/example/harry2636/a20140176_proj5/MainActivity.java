package com.example.harry2636.a20140176_proj5;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    Button fileChooser = (Button)findViewById(R.id.file_choose_button);
    fileChooser.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        Intent fileChooserIntent = new Intent(MainActivity.this, DocumentsSample.class);
        startActivity(fileChooserIntent);
      }
    });
  }
}
