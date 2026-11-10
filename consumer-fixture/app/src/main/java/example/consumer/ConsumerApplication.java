package example.consumer;

import android.app.Application;
import com.webviewinspector.WebViewInspector;

public final class ConsumerApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        WebViewInspector.install(this);
    }
}
