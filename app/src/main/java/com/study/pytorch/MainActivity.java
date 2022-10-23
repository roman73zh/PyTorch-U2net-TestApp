package com.study.pytorch;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    public static final String APP_PREFERENCES = "appSettings";
    public static final String APP_PREFERENCES_NET = "netType";
    public static final String APP_PREFERENCES_THRESHOLD = "maskThreshold";
    int netType;
    public static final int FILE_REQUEST = 1;
    private SharedPreferences mSettings;
    private ImageView imageView;
    private ImageView maskView;
    private TextView netTimeViev;
    private SeekBar maskSeekBar;
    private IValue[] outputs = null;
    private Module module = null;
    private Bitmap bitmap = null;
    private String imageName = null;
    private Bitmap scaledMask = null;
    private Bitmap transparentImage = null;
    private int width;
    private int height;
    private boolean isProcessing = false;
    private boolean isSaving = false;
    private boolean modelLoaded = false;
    private Uri imageUri = null;

    public void loadModel(String name){
        Runnable runnable = () -> {
            modelLoaded = false;
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(getApplicationContext(), "Загрузка модели " + name, Toast.LENGTH_SHORT).show());
            try {
                module = LiteModuleLoader.load(netUtils.assetFilePath(this, name));
            } catch (IOException e) {
                Log.e("PytorchLoader", "Error reading assets", e);
                new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(getApplicationContext(), "Ошибка при загрузке " + name, Toast.LENGTH_SHORT).show());
            }
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(getApplicationContext(), "Модель загружена", Toast.LENGTH_SHORT).show());
            modelLoaded = true;
        };
        Thread thread = new Thread(runnable);
        thread.start();
    }

    private String fileName(ContentResolver resolver, Uri uri) {
        Cursor returnCursor = resolver.query(uri, null, null, null, null);
        assert returnCursor != null;
        int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        returnCursor.moveToFirst();
        String name = returnCursor.getString(nameIndex);
        returnCursor.close();
        return name;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imageView = findViewById(R.id.image);
        maskView = findViewById(R.id.mask);
        netTimeViev = findViewById(R.id.netTimeViev);
        maskSeekBar = findViewById(R.id.maskSeekBar);
        maskSeekBar.setOnSeekBarChangeListener(seekBarChangeListener);
        mSettings = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);

        if ((ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                || (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        ) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }

        netType = mSettings.getInt(APP_PREFERENCES_NET, 0);

        loadModel(netType == 0 ? "model.ptl" : "model-h.ptl");
    }

    @Override
    protected void onResume(){
        super.onResume();
        if (netType != mSettings.getInt(APP_PREFERENCES_NET, 0)) {
            netType = mSettings.getInt(APP_PREFERENCES_NET, 0);
            loadModel(netType == 0 ? "model.ptl" : "model-h.ptl");
        }
    }

    private final SeekBar.OnSeekBarChangeListener seekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (outputs == null)
                return;
            Tensor maskTensor = outputs[progress].toTensor();
            final float[] output = maskTensor.getDataAsFloatArray();
            scaledMask = Bitmap.createScaledBitmap(netUtils.convertArrayToBitmap(output, 320, 320), width, height, true);
            maskView.setImageBitmap(scaledMask);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {}

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {}
    };

    public void processImage(Uri uri){
        if (uri == null || isProcessing || !modelLoaded)
            return;

        Toast.makeText(getApplicationContext(), "Preparing", Toast.LENGTH_SHORT).show();

        @SuppressLint("SetTextI18n") Runnable runnable = () -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                imageName = fileName(getContentResolver(), uri);
                bitmap = BitmapFactory.decodeStream(inputStream);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            if (bitmap == null)
                return;

            isProcessing = true;

            new Handler(Looper.getMainLooper()).post(() -> {
                maskView.setImageResource(android.R.color.transparent);
                imageView.setImageBitmap(bitmap);
                netTimeViev.setText("");
            });

            outputs = null;

            long startTime = System.currentTimeMillis();

            width = bitmap.getWidth();
            height = bitmap.getHeight();


            final Tensor inTensor = Tensor.fromBlob(netUtils.bitmapToFloatArray(
                    Bitmap.createScaledBitmap(bitmap, 320, 320, true),320,320),
                    new long[]{1, 3, 320, 320});


            long preparingTime = System.currentTimeMillis();

            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(getApplicationContext(), "Starting AI", Toast.LENGTH_SHORT).show());

            outputs = module.forward(IValue.from(inTensor)).toTuple();

            long aiTime = System.currentTimeMillis();

            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(getApplicationContext(), "AI Finished, processing result", Toast.LENGTH_SHORT).show());

            Tensor maskTensor = outputs[0].toTensor();
            final float[] output = maskTensor.getDataAsFloatArray();

            scaledMask = Bitmap.createScaledBitmap(netUtils.convertArrayToBitmap(output, 320, 320), width, height, true);

            new Handler(Looper.getMainLooper()).post(() -> {
                maskView.setImageBitmap(scaledMask);
                maskSeekBar.setProgress(0);
            });

            int threshold = mSettings.getInt(APP_PREFERENCES_THRESHOLD, 128);

            try {
                transparentImage = netUtils.composeBitmaps(bitmap, scaledMask, width, height, threshold, 4);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }

            long processingTime = System.currentTimeMillis();

            Bitmap finalTransparentImage = transparentImage;

            new Handler(Looper.getMainLooper()).post(() -> {
                Toast.makeText(getApplicationContext(), "Ready", Toast.LENGTH_SHORT).show();
                imageView.setImageBitmap(finalTransparentImage);
                netTimeViev.setText("Обработано за " +
                        (processingTime - startTime) + "ms - " +
                        (preparingTime - startTime) + "/" +
                        (aiTime - preparingTime) + "/" +
                        (processingTime - aiTime) + " (pre / AI / post)"
                );
            });

            isProcessing = false;

        };

        Thread thread = new Thread(runnable);
        thread.start();
    }


    public void onActivityResult(int reqCode, int resultCode, Intent data){
        super.onActivityResult(resultCode, resultCode, data);
        if (reqCode == FILE_REQUEST && resultCode == Activity.RESULT_OK){
            if (data == null)
                return;
            imageUri = data.getData();
            processImage(imageUri);
        }
    }

    public void openFileChooser(View view){
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        startActivityForResult(intent, FILE_REQUEST);
    }

    public void repeat(View view){
        processImage(imageUri);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void saveImage(View view){
        if (scaledMask == null || transparentImage == null || imageName == null || isProcessing){
            Toast.makeText(getApplicationContext(), "Сохранять нечего", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isSaving) {
            Toast.makeText(getApplicationContext(), "Сохранение уже запущено", Toast.LENGTH_SHORT).show();
            return;
        }
        isSaving = true;
        Toast.makeText(getApplicationContext(), "Сохранение, подождите", Toast.LENGTH_SHORT).show();
        Runnable runnable = () -> {
            String root = Environment.getExternalStorageDirectory().toString();
            File imageDir = new File(root + "/U2net saved images");
            imageDir.mkdirs();
            String namePrefix = imageName.substring(0, imageName.lastIndexOf('.'));
            File maskFile = new File (imageDir, namePrefix + "-mask.png");
            File imageFile = new File (imageDir, namePrefix + "-transparent.png");
            if (maskFile.exists())
                maskFile.delete();
            if (imageFile.exists())
                imageFile.delete();
            try {
                FileOutputStream out = new FileOutputStream(maskFile);
                scaledMask.compress(Bitmap.CompressFormat.PNG, 98, out);
                out.flush();
                out.close();
                out = new FileOutputStream(imageFile);
                transparentImage.compress(Bitmap.CompressFormat.PNG, 98, out);
                out.flush();
                out.close();
                new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(getApplicationContext(), "Изображения для " + imageName + " сохранены", Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(getApplicationContext(), "Ошибка при сохранении", Toast.LENGTH_SHORT).show());
            } finally {
                isSaving = false;
            }
        };
        Thread thread = new Thread(runnable);
        thread.start();
    }

    public void showConfig(View view) {
        Intent intent = new Intent(MainActivity.this, ConfigActivity.class);
        startActivity(intent);
    }
}