package reproduce.callback.testapps.asynchttpclientcompletion;

import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import org.asynchttpclient.AsyncHandler;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.HttpResponseBodyPart;
import org.asynchttpclient.HttpResponseStatus;
import org.asynchttpclient.Request;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.util.HttpConstants;

import io.netty.handler.codec.http.HttpHeaders;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        new AsyncTask<Void, Void, Void>(){

            @Override
            protected Void doInBackground(Void... voids) {
                AsyncHttpClient client = Dsl.asyncHttpClient();

                Request request = new RequestBuilder(HttpConstants.Methods.GET)
                        .setUrl("https://www.baeldung.com")
                        .build();

                System.err.println("Registering callback in " + Thread.currentThread().toString());
                Thread.dumpStack();
                client.executeRequest(request, new AsyncHandler<Object>() {
                    @Override
                    public State onStatusReceived(HttpResponseStatus responseStatus)
                            throws Exception {
                        System.err.println("Callback called in " + Thread.currentThread().toString());
                        Thread.dumpStack();
                        return null;
                    }

                    @Override
                    public State onHeadersReceived(HttpHeaders headers)
                            throws Exception {
                        System.err.println("Callback called in " + Thread.currentThread().toString());
                        Thread.dumpStack();
                        return null;
                    }

                    @Override
                    public State onBodyPartReceived(HttpResponseBodyPart bodyPart)
                            throws Exception {
                        return null;
                    }

                    @Override
                    public void onThrowable(Throwable t) {
                        t.printStackTrace();
                    }

                    @Override
                    public Object onCompleted() throws Exception {
                        System.err.println("Callback called in " + Thread.currentThread().toString());
                        Thread.dumpStack();
                        return null;
                    }
                });
                return null;
            }
        }.execute();


    }
}
