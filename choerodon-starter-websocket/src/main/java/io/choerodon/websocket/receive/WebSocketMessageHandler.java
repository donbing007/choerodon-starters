package io.choerodon.websocket.receive;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.choerodon.websocket.exception.MsgHandlerDuplicateMathTypeException;
import io.choerodon.websocket.helper.SocketHandlerRegistration;
import io.choerodon.websocket.relationship.RelationshipDefining;
import io.choerodon.websocket.send.MessageSender;
import io.choerodon.websocket.send.WebSocketSendPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketMessageHandler extends TextWebSocketHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebSocketMessageHandler.class);

    private final Map<String, HandlerInfo> typeClassMap = new HashMap<>(2 << 4);
    private final static ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final Map<String, SocketHandlerRegistration> registrationMap = new ConcurrentHashMap<>();
    private MessageSender messageSender;
    private RelationshipDefining relationshipDefining;

    public WebSocketMessageHandler(Optional<List<MessageHandler>> msgHandlers,
                                   RelationshipDefining relationshipDefining,
                                   MessageSender messageSender) {
        msgHandlers.orElseGet(Collections::emptyList).forEach(t -> {
            if (typeClassMap.get(t.matchType()) == null) {
                typeClassMap.put(t.matchType(), new HandlerInfo(t.payloadClass(), t));
            } else {
                throw new MsgHandlerDuplicateMathTypeException(t);
            }
        });
        this.messageSender = messageSender;
        this.relationshipDefining = relationshipDefining;
    }

    public void addMessageHandler(MessageHandler messageHandler, String type){
        if (typeClassMap.get(type) == null) {
            typeClassMap.put(type, new HandlerInfo(messageHandler.payloadClass(), messageHandler));
        } else {
            throw new MsgHandlerDuplicateMathTypeException(messageHandler);
        }
    }

    public void addSocketHandlerRegistration(SocketHandlerRegistration registration){
        if (registrationMap.putIfAbsent(registration.path(), registration) != null && LOGGER.isWarnEnabled()){
            LOGGER.warn("path {} connect processor duplicate.", registration.path());
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);
        if (session.getUri() != null){
            SocketHandlerRegistration registration = registrationMap.get(session.getUri().getPath());
            if (registration != null){
                registration.afterConnectionEstablished(session);
            }
        }
        messageSender.sendWebSocket(session, new WebSocketSendPayload<>(WebSocketSendPayload.MSG_TYPE_SESSION, null, session.getId()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
        if (session.getUri() != null){
            SocketHandlerRegistration registration = registrationMap.get(session.getUri().getPath());
            if (registration != null){
                registration.afterConnectionClosed(session, status);
            }
        }
        this.relationshipDefining.removeWebSocketSessionContact(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        super.handleTransportError(session, exception);
        LOGGER.error("error.webSocketMessageHandler.handleTransportError", exception);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        super.handleTextMessage(session, message);
        String receiveMsg = message.getPayload();
        try {
            JsonNode node = OBJECT_MAPPER.readTree(receiveMsg);
            String type = node.get("type").asText();
            if (type != null) {
                HandlerInfo handlerInfo = typeClassMap.get(type);
                if (handlerInfo != null) {
                    JavaType javaType = OBJECT_MAPPER.getTypeFactory().constructParametricType(WebSocketReceivePayload.class, handlerInfo.payloadType);
                    WebSocketReceivePayload<?> payload = OBJECT_MAPPER.readValue(receiveMsg, javaType);
                    handlerInfo.msgHandler.handle(session, payload.getType(), payload.getKey(), payload.getData());
                } else {
                    LOGGER.warn("abandon message that can not find msgHandler, message {}", receiveMsg);
                }
            } else {
                LOGGER.warn("abandon message that does't have 'type' field, message {}", receiveMsg);
            }
        } catch (Exception e) {
            LOGGER.warn("abandon message received from client, msgHandler error, message: {}", receiveMsg, e);
        }
    }

    final class HandlerInfo {
        final Class<?> payloadType;
        final MessageHandler msgHandler;

        HandlerInfo(Class<?> payloadType, MessageHandler msgHandler) {
            this.payloadType = payloadType;
            this.msgHandler = msgHandler;
        }
    }

}
