package com.ktb.chatapp.websocket.socketio;

/**
 * Socket User Record
 * @param id user id
 * @param name user name
 * @param socketId user websocket session id
 */
public record SocketUser(String id, String name, String socketId) {
}
