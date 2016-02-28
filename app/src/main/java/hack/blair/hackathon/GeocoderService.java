package hack.blair.hackathon;

import android.os.Handler;
import android.os.Looper;
import android.util.JsonReader;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class GeocoderService {
    private static final String TAG = "GeocoderService";
    private static ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

    public interface Callback {
        public void gotResult(String locationString);
    }

    public static void reverse(final double latitude, final double longitude, final Callback callback, final Handler handler) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                String location = "(" + latitude + ", " + longitude + ")";
                try {
                    URL url = new URL("http://nominatim.openstreetmap.org/reverse?format=json&lat=" + latitude + "&lon=" + longitude + "&email=matwilliams@hotmail.com");
                    Log.e(TAG, "Connecting to " + url);
                    HttpURLConnection connection = (HttpURLConnection)url.openConnection();
                    connection.connect();
                    String response = connection.getResponseMessage();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line = null;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line + "\n");
                    }
                    Log.e(TAG, "Got response " + sb);
                    JSONObject obj = new JSONObject(sb.toString());
                    location = obj.getString("display_name");
                } catch (IOException e) {
                    Log.e(TAG, "Caught IOException while reverse geocoding", e);
                } catch (JSONException e) {
                    Log.e(TAG, "Caught JSONException while reverse geocoding", e);
                }
                final String finalLocation = location;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.gotResult(finalLocation);
                    }
                });
            }
        });
    }
}
