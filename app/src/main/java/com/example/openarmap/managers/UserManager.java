package com.example.openarmap.managers;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.openarmap.models.User;

public class UserManager {
    private static final String PREF_NAME = "UserPrefs";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";

    private static UserManager instance;
    private SharedPreferences preferences;
    private User currentUser;

    private UserManager(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadUserFromPrefs();
    }

    public static synchronized UserManager getInstance(Context context) {
        if (instance == null) {
            instance = new UserManager(context.getApplicationContext());
        }
        return instance;
    }

    private void loadUserFromPrefs() {
        if (preferences.getBoolean(KEY_IS_LOGGED_IN, false)) {
            String username = preferences.getString(KEY_USERNAME, "");
            String email = preferences.getString(KEY_EMAIL, "");
            currentUser = new User(username, email);
        }
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean login(String username, String email) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }

        currentUser = new User(username, email);
        saveUserToPrefs();
        return true;
    }

    public void logout() {
        currentUser = null;
        preferences.edit().clear().apply();
    }

    private void saveUserToPrefs() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_USERNAME, currentUser.getUsername());
        editor.putString(KEY_EMAIL, currentUser.getEmail());
        editor.putBoolean(KEY_IS_LOGGED_IN, true);
        editor.apply();
    }
} 