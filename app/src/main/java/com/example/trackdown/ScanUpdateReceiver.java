package com.example.trackdown;

import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;

public class ScanUpdateReceiver extends ResultReceiver {
    private Receiver receiver;

    public ScanUpdateReceiver(Handler handler) {
        super(handler);
    }

    public interface Receiver {
        public void onReceiveResult(int resultCode, Bundle resultData);

    }

    public void setReceiver(Receiver receiver) {
        this.receiver = receiver;
    }

    @Override
    protected void onReceiveResult(int resultCode, Bundle resultData) {
        if (this.receiver != null) {
            this.receiver.onReceiveResult(resultCode, resultData);
        }
    }
}
