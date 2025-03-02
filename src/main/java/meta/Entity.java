package meta;

import java.util.HashMap;
import java.util.Map;

public class Entity {
    public final Map<Object, Object> entries = new HashMap<>();
    private Entity metaentity = null;

    public Object get(Object key) {
        // 1) Check local entries
        if (entries.containsKey(key)) {
            return entries.get(key);
        }
        // 2) If not found, check parent
        if (metaentity != null) {
            return metaentity.get(key);
        }
        // 3) Not found at all
        return null;
    }

    public void set(Object key, Object value) {
        entries.put(key, value);
    }

    public Entity getMetaentity() {
        return metaentity;
    }

    public void setMetaentity(Entity parent) {
        this.metaentity = parent;
    }

    public int size() {
        return entries.size();
    }

    @Override
    public String toString() {
        return "<entity " + entries.toString() + ">";
    }
}
