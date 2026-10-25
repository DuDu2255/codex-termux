package com.dudu.codex;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class Json {
    static JSONObject obj(Object... pairs) {
        JSONObject result = new JSONObject();
        for (int i = 0; i < pairs.length; i += 2) put(result, pairs[i].toString(), pairs[i + 1]);
        return result;
    }
    static void put(JSONObject object, String key, Object value) {
        try { object.put(key, value == null ? JSONObject.NULL : value); }
        catch (JSONException e) { throw new IllegalArgumentException(e); }
    }
    static JSONObject parse(String text) {
        try { return new JSONObject(text); }
        catch (JSONException e) { throw new IllegalArgumentException("Invalid JSON", e); }
    }
    static JSONObject object(JSONObject parent, String key) {
        JSONObject value = parent.optJSONObject(key);
        return value == null ? new JSONObject() : value;
    }
    static JSONArray array(JSONObject parent, String key) {
        JSONArray value = parent.optJSONArray(key);
        return value == null ? new JSONArray() : value;
    }
}
