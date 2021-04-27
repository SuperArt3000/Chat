package ru.dmitrii.chat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    static private Map<String, Connection> connectionMap = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(ConsoleHelper.readInt())) {
            ConsoleHelper.writeMessage("Сервер запущен");
            while (true) {
                new Handler(serverSocket.accept()).start();
            }
        } catch (IOException e) {
            ConsoleHelper.writeMessage(e.getMessage());
        }
    }

    public static void sendBroadcastMessage(Message message) {
        for (String clientName : connectionMap.keySet()) {
            try {
                connectionMap.get(clientName).send(message);
            } catch (IOException e) {
                ConsoleHelper.writeMessage("Не могу отправить сообщение клиенту с именем: " + clientName);
            }
        }
    }



    private static class Handler extends Thread {
        private final Socket socket;
        public Handler(Socket socket) {
            this.socket = socket;
        }

        private String serverHandshake(Connection connection) throws IOException, ClassNotFoundException  {
            boolean accepted = false;
            String name = null;
            while (!accepted) {
                connection.send(new Message(MessageType.NAME_REQUEST));
                Message message = connection.receive();
                if (message.getType() == MessageType.USER_NAME) {
                    name = message.getData();
                    if (!name.isEmpty() && connectionMap.get(name) == null) {
                        connectionMap.put(name, connection);
                        connection.send(new Message(MessageType.NAME_ACCEPTED));
                        accepted = true;
                    }
                }
            }
            return name;
        }

        private void notifyUsers(Connection connection, String userName) {
            for (String clientName : connectionMap.keySet()) {
                if (!clientName.equals(userName)) {
                    try {
                        connection.send(new Message(MessageType.USER_ADDED, clientName));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        private void serverMainLoop(Connection connection, String userName) throws IOException, ClassNotFoundException {
            while (!Thread.currentThread().isInterrupted()) {
                Message message = connection.receive();
                if (message.getType() == MessageType.TEXT) {
                    String messageText = userName + ": " + message.getData();
                    System.out.println(messageText);
                    sendBroadcastMessage(new Message(MessageType.TEXT, messageText));
                } else ConsoleHelper.writeMessage(
                        String.format("Ошибка! Недопустимый тип сообщения (MessageType.%s) от клиента: %s",
                                message.getType().toString() ,userName)
                );
            }

        }

        @Override
        public void run() {
            ConsoleHelper.writeMessage("Установлено соединение с удаленным клиентом с адресом: " +
                    socket.getRemoteSocketAddress());
            Connection connection = null;
            String clientName = null;
            try {
                connection = new Connection(socket);
                clientName = serverHandshake(connection);
                sendBroadcastMessage(new Message(MessageType.USER_ADDED, clientName));
                notifyUsers(connection, clientName);
                serverMainLoop(connection, clientName);
            } catch (IOException | ClassNotFoundException e) {
                ConsoleHelper.writeMessage(e.getMessage());
            }
            if (clientName != null) {
                connectionMap.remove(clientName);
                sendBroadcastMessage(new Message(MessageType.USER_REMOVED, clientName));
            }

            ConsoleHelper.writeMessage(String.format("Соединение с удаленным адресом (%s) закрыто.", socket.getRemoteSocketAddress()));
        }

    }
}
