package reproduce.automaticcallbacktesting.callbackcodegeneration;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class MainActivity extends AppCompatActivity {
    static VirtualEdgesSummaries sum = new VirtualEdgesSummaries();
    static AtomicInteger x = new AtomicInteger();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        new Thread(new Runnable() {

            @Override
            public void run() {

                while (true) {
                    try {
                        StringBuilder sb = new StringBuilder("Stacktraces-dump\n");
                        for (Map.Entry<Thread, StackTraceElement[]> i : Thread.getAllStackTraces().entrySet()) {
                            sb.append(i.getKey().getName()).append(":\n");
                            for (StackTraceElement c : i.getValue()) {
                                sb.append("\t" + c.getClassName() + ": " + c.getMethodName() + "|" + c.getLineNumber()).append("\n");
                            }
                            sb.append("\n");
                        }
                        Log.i("Stacktraces-Dump", sb.toString());
                        Thread.sleep(2000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
        new Thread(new Runnable() {

            @Override
            public void run() {

                main();
                try {
                    Transformer transformer = TransformerFactory.newInstance().newTransformer();
                    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
                    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
// initialize StreamResult with File object to save to file
                    StreamResult result = new StreamResult(new StringWriter());
                    DOMSource source = new DOMSource(sum.toXMLDocument());
                    transformer.transform(source, result);
                    String xmlString = result.getWriter().toString();

                    Log.i("CI-Sum-Finished", xmlString);
                    File s = getExternalFilesDir(null);
                    File f=new File( s, "/callbacks-runtime/" + getMyPackageName() +".xml");
                    f.getParentFile().mkdirs();
                    f.createNewFile();
                    FileOutputStream fout=new FileOutputStream(f);
                    fout.write(xmlString.getBytes("UTF-8"));
                    fout.close();

                    final Handler UIHandler = new Handler(Looper.getMainLooper());
                    UIHandler .post(new Runnable() {

                        @Override
                        public void run() {
                            TextView v = findViewById(R.id.status);
                            Log.i("CI-Sum", "Written to " + f.getAbsolutePath());
                            v.setText("Written " + x + " results to " + f.getAbsolutePath());
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private String getMyPackageName() {
        return "";
    }

    /*

    public VirtualEdge(Kind edgeType, VirtualEdgeSource source, VirtualEdgeTarget target) {
      this(edgeType, source, new ArrayList<>(Collections.singletonList(target)));
    }

    public VirtualEdge(Kind edgeType, VirtualEdgeSource source, Collection<VirtualEdgeTarget> targets) {
      this.edgeType = edgeType;
     */

    public static void addFoundCallback(String ssource, String directTarget, int paramIdx, int cb) {
        VirtualEdgesSummaries.VirtualEdgeSource source = new VirtualEdgesSummaries.InstanceinvokeSource(ssource);
        MethodSubSignature dt = new MethodSubSignature(directTarget);
        VirtualEdgesSummaries.VirtualEdgeTarget target = new VirtualEdgesSummaries.DirectTarget(dt, paramIdx);
        sum.addInstanceInvoke(new VirtualEdgesSummaries.VirtualEdge(Kind.GENERIC_FAKE, source, target), new MethodSubSignature(ssource));
        Log.i("CI-Sum", "Runtime callback reached: " + source + " -> " + directTarget + ", " + paramIdx + ", Class: Callback" + cb);
        x.incrementAndGet();
    }

    private void main() {
        int i = 0;
        while (true) {
            try {
                Method m = Class.forName("Callback" + i).getDeclaredMethod("callback" + i);
                m.invoke(null);
                Log.i("CI", "Invoked successfully: " + i);
                i++;
            } catch (NoSuchMethodException e) {
                Log.i("CI-Sum", "Tested " + i + " callbacks");
                break;
            } catch (Throwable e) {
                //Log.e("CI", "Could not invoke callback " + i, e);
                i++;
            }
        }
    }
}