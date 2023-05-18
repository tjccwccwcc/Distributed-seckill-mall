package cn.wolfcode.ws;

import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/{token}")
@Component
public class OrderWSServer {
    private Session session;
    public static ConcurrentHashMap<String,Session> clients = new ConcurrentHashMap<>();
    @OnOpen
    public void onOpen(Session session, @PathParam( "token") String token){
        System.out.println("浏览器和服务器建立连接：" + token);
        //建立和浏览器会话的映射关系
        clients.put(token,session);
    }
    @OnClose
    public void onClose(@PathParam( "token") String token){
        System.out.println("浏览器和服务器断开连接：" + token);
        clients.remove(token);
    }
    @OnError
    public void onError(Throwable error) {
        error.printStackTrace();
    }
}
