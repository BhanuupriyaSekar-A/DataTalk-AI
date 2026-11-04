package com.datatalk.datatalkai.chat;

/** Exposes package-private ChatService helpers to tests in other packages. */
public final class ChatServiceTestAccess {
    private ChatServiceTestAccess() {
    }

    public static String dbProvider(String provider) {
        return ChatService.dbProvider(provider);
    }
}
