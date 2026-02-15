package core;

import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

public class Session {

    private final HttpSession httpSession;

    public Session(HttpSession session) {
        this.httpSession = session;
    }

    public Object get(String key) {
        return httpSession.getAttribute(key);
    }

    public void set(String key, Object value) {
        httpSession.setAttribute(key, value);
    }

    public void remove(String key) {
        httpSession.removeAttribute(key);
    }

    public boolean contains(String key) {
        return httpSession.getAttribute(key) != null;
    }

    public Map<String, Object> getAll() {
        Map<String, Object> map = new HashMap<>();
        var names = httpSession.getAttributeNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            map.put(name, httpSession.getAttribute(name));
        }
        return map;
    }

    public void invalidate() {
        httpSession.invalidate();
    }

    public String getId() {
        return httpSession.getId();
    }
}
