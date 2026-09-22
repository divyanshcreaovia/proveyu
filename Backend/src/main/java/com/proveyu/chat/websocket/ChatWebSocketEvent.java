package com.proveyu.chat.websocket;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatWebSocketEvent<T> {
    private String event;
    private T data;
}
