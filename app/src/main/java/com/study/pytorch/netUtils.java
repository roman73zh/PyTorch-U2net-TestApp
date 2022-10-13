package com.study.pytorch;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class netUtils {
    public static float[] bitmapToFloatArray(Bitmap bitmap, int width, int height){
        float[][][][] arr = new float[1][width][height][3];
        float maxValue = 0;
        int[] intValues = new int[width * height];
        bitmap.getPixels(intValues, 0, width, 0, 0, width, height);
        for (int i = 0; i < width; i++){
            for (int j = 0; j < height; j++){
                int pixelValue = intValues[i * width + j];
                arr[0][i][j][0] = (float) Color.red(pixelValue);
                arr[0][i][j][1] = (float)Color.green(pixelValue);
                arr[0][i][j][2] = (float)Color.blue(pixelValue);
                float maxTmp = (Math.max(arr[0][i][j][0], arr[0][i][j][1]));
                maxTmp = (Math.max(maxTmp, arr[0][i][j][2]));
                if (maxTmp > maxValue)
                    maxValue = maxTmp;
            }
        }

        float[] result = new float[3 * 320 * 320];
        for (int i = 0; i < width; i++){
            for (int j = 0; j < height; j++) {
                int pixelValue = intValues[i * width + j];
                int index = i * 320 + j;
                result[index] = ((((float)Color.red(pixelValue)) / maxValue) - 0.485f) / 0.229f;
                result[102400 + index] = ((((float)Color.green(pixelValue)) / maxValue) - 0.456f) / 0.224f;
                result[204800 + index] = ((((float)Color.blue(pixelValue)) / maxValue) - 0.406f) / 0.225f;
            }
        }
        return result;
    }

    public static Bitmap convertArrayToBitmap(float[] arr, int width, int height){
        Bitmap grayToneImage = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int i = 0; i < width; i++){
            for (int j = 0; j < height; j++){
                grayToneImage.setPixel(j, i, (int)(arr[i * height + j] * 255f) << 24);
            }
        }
        return grayToneImage;
    }

    private static void bitmapComposeWorker(int [] imageValues, int[] maskValues, int[] outputValues, int start, int stop, int threshold){
        for (int i = start; i < stop; i++){
            int alpha = maskValues[i];
            outputValues[i] = imageValues[i] & 0xFFFFFF | (alpha >>> 24 > threshold ? alpha : 0);
        }
    }

    public static Bitmap composeBitmaps(Bitmap image, Bitmap mask, int width, int height, int threshold, int threads) throws ExecutionException, InterruptedException {
        ExecutorService bitmapComposeThreadPool = Executors.newFixedThreadPool(threads);
        int[] imageValues = new int[width * height];
        int[] maskValues = new int[width * height];
        int[] resultValues = new int[width * height];
        image.getPixels(imageValues, 0, width, 0, 0, width, height);
        mask.getPixels(maskValues, 0, width, 0, 0, width, height);
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        List<Future<Boolean>> futures = new ArrayList<>();
        int step = imageValues.length / 50;
        for (int i = 0; i < imageValues.length; i += step){
            int finalI = i;
            futures.add(bitmapComposeThreadPool.submit(() -> {
                bitmapComposeWorker(imageValues, maskValues, resultValues, finalI, Math.min(finalI + step, imageValues.length), threshold);
                return true;
            }));
        }
        for (Future<Boolean> i : futures){
            i.get();
        }
        bitmapComposeThreadPool.shutdown();
        result.setPixels(resultValues, 0, width, 0, 0, width, height);
        return result;
    }

    public static String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() > 0) {
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }
}
