package com.virtixstudio.kruxai/history;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class ChatHistoryManager {

    private static final String PREF_NAME = "krux_chat_history";
    private static final String KEY_CHATS = "chats_list";

    public static class ChatItem {
        public String id;
        public String title;
        public boolean isPinned;
        public boolean isDeleted;
        public long timestamp;

        public ChatItem(String id, String title, boolean isPinned, boolean isDeleted, long timestamp) {
            this.id = id;
            this.title = title;
            this.isPinned = isPinned;
            this.isDeleted = isDeleted;
            this.timestamp = timestamp;
        }

        public ChatItem(String id, String title, boolean isPinned, long timestamp) {
            this(id, title, isPinned, false, timestamp);
        }
    }

    public static void saveChat(Context context, ChatItem item) {
        List<ChatItem> list = getAllChatsRaw(context);
        boolean exists = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(item.id)) {
                list.set(i, item);
                exists = true;
                break;
            }
        }
        if (!exists) list.add(0, item);
        saveList(context, list);
    }

    public static List<ChatItem> getActiveChats(Context context) {
        List<ChatItem> activeList = new ArrayList<>();
        for (ChatItem item : getAllChatsRaw(context)) {
            if (!item.isDeleted) {
                activeList.add(item);
            }
        }
        return activeList;
    }

    public static List<ChatItem> getDeletedChats(Context context) {
        List<ChatItem> deletedList = new ArrayList<>();
        for (ChatItem item : getAllChatsRaw(context)) {
            if (item.isDeleted) {
                deletedList.add(item);
            }
        }
        return deletedList;
    }

    public static void moveToTrash(Context context, String id) {
        List<ChatItem> list = getAllChatsRaw(context);
        for (ChatItem item : list) {
            if (item.id.equals(id)) {
                item.isDeleted = true;
                break;
            }
        }
        saveList(context, list);
    }

    public static void restoreChat(Context context, String id) {
        List<ChatItem> list = getAllChatsRaw(context);
        for (ChatItem item : list) {
            if (item.id.equals(id)) {
                item.isDeleted = false;
                break;
            }
        }
        saveList(context, list);
    }

    public static void deletePermanently(Context context, String id) {
        List<ChatItem> list = getAllChatsRaw(context);
        list.removeIf(item -> item.id.equals(id));
        saveList(context, list);
    }

    private static List<ChatItem> getAllChatsRaw(Context context) {
        List<ChatItem> list = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String jsonStr = prefs.getString(KEY_CHATS, "[]");
        try {
            JSONArray array = new JSONArray(jsonStr);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                list.add(new ChatItem(
                    obj.getString("id"),
                    obj.getString("title"),
                    obj.optBoolean("isPinned", false),
                    obj.optBoolean("isDeleted", false),
                    obj.optLong("timestamp", System.currentTimeMillis())
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private static void saveList(Context context, List<ChatItem> list) {
        JSONArray array = new JSONArray();
        try {
            for (ChatItem item : list) {
                JSONObject obj = new JSONObject();
                obj.put("id", item.id);
                obj.put("title", item.title);
                obj.put("isPinned", item.isPinned);
                obj.put("isDeleted", item.isDeleted);
                obj.put("timestamp", item.timestamp);
                array.put(obj);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
               .edit().putString(KEY_CHATS, array.toString()).apply();
    }
}
