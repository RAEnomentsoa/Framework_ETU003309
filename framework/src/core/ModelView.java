package core;

import java.util.HashMap;
import java.util.Map;

public class ModelView {
    // sprint 4 bis

    private String view; // JSP path
    private Map<String, Object> data; // Variables to send to JSP

    public ModelView(String view) {
        this.view = view;
        this.data = new HashMap<>();
    }

    public String getView() {
        return view;
    }

    public void addItem(String key, Object value) {
        data.put(key, value);
    }

    public Map<String, Object> getData() {
        return data;
    }
}
