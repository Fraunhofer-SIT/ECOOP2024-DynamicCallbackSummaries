package replicate.callbackanalysis.testcases.googlehttpclient;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.jackson2.JacksonFactory;

import java.io.IOException;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.CUPCAKE) {
            new AsyncTask<Void,Void,Void>(){

                @Override
                protected Void doInBackground(Void... voids) {
                    HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
                    final JsonFactory JSON_FACTORY = new JacksonFactory();
                    System.err.println("Registering callback in " + Thread.currentThread().toString());
                    Thread.dumpStack();
                    HttpRequestFactory requestFactory =
                            HTTP_TRANSPORT.createRequestFactory(
                                    new HttpRequestInitializer() {
                                        @Override
                                        public void initialize(HttpRequest request) {
                                            System.err.println("Callback called in " + Thread.currentThread().toString());
                                            Thread.dumpStack();
                                            request.setParser(new JsonObjectParser(JSON_FACTORY));
                                            //request.setHeaders(new HttpHeaders().set("TestHeader","DummyValue"));
                                        }
                                    });


                    try {
                        HttpRequest request = requestFactory.buildGetRequest(new GenericUrl("http://ip-api.com/json"));
                        HttpResponse response = request.execute();
                        String data = response.parseAsString();
                        Log.d("Response object", data.toString());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            }.execute();
        }


    }
}
