package com.example.photoviewer;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String IMAGE_FILE_NAME = "Lion cubs looking at the camera, Moremi Game Reserve, Okavango Delta, Botswana.jpg";
    private static final String TOKEN = "c392a0cbfa79a18be05242ae23ccc9a862725d92";
    private static final String SITE_URL_DEFAULT = "http://10.0.2.2:8000";
    private static final String SITE_URL_ALTERNATE = "https://jylim.pythonanywhere.com";

    TextView textView;
    String site_url = SITE_URL_DEFAULT;
    JSONObject post_json;
    String imageUrl = null;
    Bitmap bmImg = null;

    CloadImage taskDownload;
    PutPost taskUpload;
    CheckLocalServerTask taskServer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.textView);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
        }

        taskServer = new CheckLocalServerTask();

        taskServer.execute(site_url + "/api_root/Post/");
    }

    public void onClickDownload(View v) {
        if (taskDownload != null && taskDownload.getStatus() == AsyncTask.Status.RUNNING) {
            taskDownload.cancel(true);
        }

        taskDownload = new CloadImage();

        taskDownload.execute(site_url + "/api_root/Post/");
        Toast.makeText(getApplicationContext(), "Download", Toast.LENGTH_LONG).show();
    }

    public void onClickUpload(View v) {
        if (taskUpload != null && taskUpload.getStatus() == AsyncTask.Status.RUNNING) {
            taskUpload.cancel(true);
        }

        Uri imageUri = getImageUri(getApplicationContext());

        if (imageUri != null) {
            bmImg = loadBitmapFromUri(getApplicationContext(), imageUri);
            if (bmImg != null) {
                taskUpload = new PutPost();
                taskUpload.execute(site_url + "/api_root/Post/");
                Toast.makeText(getApplicationContext(), "Upload", Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(getApplicationContext(), "Image not found", Toast.LENGTH_LONG).show();
        }
    }

    private Uri getImageUri(Context context) {
        Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = { MediaStore.Images.Media._ID };
        String selection = MediaStore.Images.Media.DISPLAY_NAME + "=?";
        String[] selectionArgs = new String[]{ IMAGE_FILE_NAME };

        try (Cursor cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID));

                return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
            }
        }

        return null;
    }

    private Bitmap loadBitmapFromUri(Context context, Uri uri) {
        try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {

            return BitmapFactory.decodeStream(inputStream);
        } catch (IOException e) {
            e.printStackTrace();

            return null;
        }
    }

    private class CloadImage extends AsyncTask<String, Integer, List<Bitmap>> {
        @Override
        protected List<Bitmap> doInBackground(String... urls) {
            List<Bitmap> bitmapList = new ArrayList<>();

            try {
                String apiUrl = urls[0];
                URL urlAPI = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) urlAPI.openConnection();
                conn.setRequestProperty("Authorization", "Token " + TOKEN);
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream is = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    StringBuilder result = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        result.append(line);
                    }
                    is.close();

                    String strJson = result.toString();
                    JSONArray aryJson = new JSONArray(strJson);

                    for (int i = 0; i < aryJson.length(); i++) {
                        post_json = (JSONObject) aryJson.get(i);
                        imageUrl = post_json.getString("image");

                        if (!imageUrl.equals("")) {
                            URL myImageUrl = new URL(imageUrl);
                            conn = (HttpURLConnection) myImageUrl.openConnection();
                            InputStream imgStream = conn.getInputStream();

                            Bitmap imageBitmap = BitmapFactory.decodeStream(imgStream);
                            bitmapList.add(imageBitmap);
                            imgStream.close();
                        }
                    }
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }

            return bitmapList;
        }

        @Override
        protected void onPostExecute(List<Bitmap> images) {
            if (images.isEmpty()) {
                textView.setText("불러올 이미지가 없습니다.");
            } else {
                textView.setText("이미지 로드 성공!");
                RecyclerView recyclerView = findViewById(R.id.recyclerView);
                ImageAdapter adapter = new ImageAdapter(images);
                recyclerView.setLayoutManager(new LinearLayoutManager(MainActivity.this));
                recyclerView.setAdapter(adapter);
            }
        }
    }

    private class PutPost extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... urls) {
            try {
                String apiUrl = urls[0];
                String boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW";
                URL urlAPI = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) urlAPI.openConnection();
                conn.setRequestProperty("Authorization", "JWT " + TOKEN);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);

                try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    bmImg.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
                    byte[] imageBytes = byteArrayOutputStream.toByteArray();

                    dos.writeBytes("--" + boundary + "\r\n");
                    dos.writeBytes("Content-Disposition: form-data; name=\"author\"\r\n\r\n1\r\n");
                    dos.writeBytes("--" + boundary + "\r\n");
                    dos.writeBytes("Content-Disposition: form-data; name=\"title\"\r\n\r\nBotswana\r\n");
                    dos.writeBytes("--" + boundary + "\r\n");
                    dos.writeBytes("Content-Disposition: form-data; name=\"text\"\r\n\r\nLion cubs looking at the camera, Moremi Game Reserve, Okavango Delta, Botswana\r\n");
                    dos.writeBytes("--" + boundary + "\r\n");
                    dos.writeBytes("Content-Disposition: form-data; name=\"created_date\"\r\n\r\n2024-11-05T14:30:00+09:00\r\n");
                    dos.writeBytes("--" + boundary + "\r\n");
                    dos.writeBytes("Content-Disposition: form-data; name=\"published_date\"\r\n\r\n2024-11-05T14:30:00+09:00\r\n");
                    dos.writeBytes("--" + boundary + "\r\n");
                    dos.writeBytes("Content-Disposition: form-data; name=\"image\"; filename=\"" + IMAGE_FILE_NAME + "\"\r\n");
                    dos.writeBytes("Content-Type: image/jpeg\r\n\r\n");
                    dos.write(imageBytes);
                    dos.writeBytes("\r\n--" + boundary + "--\r\n");
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_CREATED) {
                    Log.d("PutPost", "이미지 업로드 성공!");
                } else {
                    Log.e("PutPost", "이미지 업로드에 실패했습니다.");
                    try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                        StringBuilder errorMessage = new StringBuilder();
                        String errorLine;
                        while ((errorLine = errorReader.readLine()) != null) {
                            errorMessage.append(errorLine);
                        }
                        Log.e("PutPost", errorMessage.toString());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }
    }

    private class CheckLocalServerTask extends AsyncTask<String, Void, Void> {
        @Override
        protected Void doInBackground(String... urls) {
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("HEAD");
                conn.setConnectTimeout(3000);
                conn.connect();

                if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    site_url = SITE_URL_ALTERNATE;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }
    }
}