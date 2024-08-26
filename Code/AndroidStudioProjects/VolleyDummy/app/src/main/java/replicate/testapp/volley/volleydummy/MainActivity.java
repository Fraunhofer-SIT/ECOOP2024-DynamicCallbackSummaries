package replicate.testapp.volley.volleydummy;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try {
            doVolley();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    void doVolley() throws InterruptedException {
        run("https://www.google.com");
        Thread.sleep(2000);
        run("http://www.google.com");


    }

    private void run(String url) {
        RequestQueue q = Volley.newRequestQueue(this);
        System.err.println("Registering callback in " + Thread.currentThread().toString());
        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Display the first 500 characters of the response string.
                        System.err.println("Callback called in " + Thread.currentThread().toString());
                        Thread.dumpStack();
                        Log.d("Volley","Response is: "+ response.substring(0,500));
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.d("Volley","That didn't work!");
            }
        });
        // Add the request to the RequestQueue.
        q.add(stringRequest);
    }
}
