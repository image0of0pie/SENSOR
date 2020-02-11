package ca.sarvajit.sensorrecord;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.google.firebase.FirebaseApp;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.IOException;

public class uploader extends AppCompatActivity implements View.OnClickListener{
    private Button selecter,uploader;
    private Integer PICK_CSV_REQUEST=1415;
    private Uri filepath;
    private ImageView imageview;
    FirebaseStorage firebasestorage;
    StorageReference storagereference;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.uploader);
        selecter=(Button)findViewById(R.id.selector);
        uploader=(Button)findViewById(R.id.uploader);
        imageview=(ImageView)findViewById(R.id.imageView);
        storagereference= FirebaseStorage.getInstance().getReference();
        selecter.setOnClickListener(this);
        uploader.setOnClickListener(this);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        Intent intent=new Intent(uploader.this,LoginActivity.class);
        startActivity(intent);
    }

    private void showfileselector()
    {
        Intent intent=new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent,"Select a csv file"),PICK_CSV_REQUEST);
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_CSV_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            filepath = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), filepath);
                imageview.setImageBitmap(bitmap);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    private void upload_file()
    {


    }
    @Override
    public void onClick(View view) {
        if(view==uploader)
        {
        }
        else if(view==selecter)
        { ;
            showfileselector();
        }
    }
}
