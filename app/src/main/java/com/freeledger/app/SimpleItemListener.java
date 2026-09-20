package com.freeledger.app;

import android.view.View;
import android.widget.AdapterView;

public class SimpleItemListener implements AdapterView.OnItemSelectedListener {
    public interface Callback {
        void onSelected(int position);
    }

    private final Callback callback;

    public SimpleItemListener(Callback callback) {
        this.callback = callback;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        callback.onSelected(position);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {}
}
