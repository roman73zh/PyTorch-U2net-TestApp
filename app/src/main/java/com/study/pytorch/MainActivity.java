package com.study.pytorch;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    public static final int FILE_REQUEST = 1;
    ImageView imageView;
    Module module = null;
    Bitmap bitmap = null;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imageView = findViewById(R.id.image);

        if ((ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                || (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        ) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }

        try {
            module = LiteModuleLoader.load(netUtils.assetFilePath(this, "model.ptl"));
        } catch (IOException e) {
            Log.e("PytorchLoader", "Error reading assets", e);
            finish();
        }
    }


    public void onActivityResult(int reqCode, int resultCode, Intent data){
        super.onActivityResult(resultCode, resultCode, data);
        if (reqCode == FILE_REQUEST && resultCode == Activity.RESULT_OK){
            if (data == null)
                return;
            Uri uri = data.getData();
            Toast.makeText(getApplicationContext(), "Preparing", Toast.LENGTH_SHORT).show();

            Runnable runnable = new Runnable() {
                public void run() {
                    try {
                        InputStream inputStream = getContentResolver().openInputStream(uri);
                        bitmap = BitmapFactory.decodeStream(inputStream);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }

                    if (bitmap == null)
                        return;

                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            imageView.setImageBitmap(bitmap);
                        }
                    });

                    int width = bitmap.getWidth();
                    int height = bitmap.getHeight();


                    final Tensor inTensor = Tensor.fromBlob(netUtils.bitmapToFloatArray(
                            Bitmap.createScaledBitmap(bitmap, 320, 320, true),320,320),
                            new long[]{1, 3, 320, 320});


                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "Starting AI", Toast.LENGTH_SHORT).show();
                        }
                    });

                    IValue[] outputs = module.forward(IValue.from(inTensor)).toTuple();
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "AI Finished, processing result", Toast.LENGTH_SHORT).show();
                        }
                    });
                    Tensor maskTensor = outputs[0].toTensor();
                    final float[] output = maskTensor.getDataAsFloatArray();

                    Bitmap scaledMask = Bitmap.createScaledBitmap(netUtils.convertArrayToBitmap(output, 320, 320), width, height, true);
                    Bitmap transparentImage = netUtils.composeBitmaps(bitmap, scaledMask, width, height);


                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(getApplicationContext(), "Ready", Toast.LENGTH_SHORT).show();
                            imageView.setImageBitmap(transparentImage);
                        }
                    });



                }
            };

            Thread thread = new Thread(runnable);
            thread.start();

//            Bitmap bitmap = null;
//            try {
//                InputStream inputStream = getContentResolver().openInputStream(uri);
//                bitmap = BitmapFactory.decodeStream(inputStream);
//            } catch (FileNotFoundException e) {
//                e.printStackTrace();
//            }
//
//            if (bitmap == null)
//                return;
//
//            int width = bitmap.getWidth();
//            int height = bitmap.getHeight();
//
//
//            final Tensor inTensor = Tensor.fromBlob(netUtils.bitmapToFloatArray(
//                    Bitmap.createScaledBitmap(bitmap, 320, 320, true),320,320),
//                    new long[]{1, 3, 320, 320});
//
//
//            Toast.makeText(getApplicationContext(), "Starting AI", Toast.LENGTH_SHORT).show();
//            IValue[] outputs = module.forward(IValue.from(inTensor)).toTuple();
//            Tensor maskTensor = outputs[0].toTensor();
//            final float[] output = maskTensor.getDataAsFloatArray();
//
//            Bitmap scaledMask = Bitmap.createScaledBitmap(netUtils.convertArrayToBitmap(output, 320, 320), width, height, true);
//            Bitmap transparentImage = netUtils.composeBitmaps(bitmap, scaledMask, width, height);
//
//            imageView.setImageBitmap(transparentImage);

        }
    }

    public void openFileChooser(View view){
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, FILE_REQUEST);
    }

}