package work.utakatanet.utazonplugin.util;

import com.github.kuripasanda.economyutilsapi.api.EconomyUtilsApi;
import org.bukkit.configuration.file.FileConfiguration;
import work.utakatanet.utazonplugin.UtazonPlugin;
import com.google.gson.Gson;

import java.io.*;
import java.net.*;
import java.util.UUID;

public class SocketServer implements Runnable {

    private static final UtazonPlugin utazonPlugin = UtazonPlugin.plugin;
    private static final Gson gson = UtazonPlugin.gson;
    private static final EconomyUtilsApi ecoApi = UtazonPlugin.ecoApi;

    private ServerSocket serverSocket;
    public boolean isRunning = false;
    private int port;

    public void start() {
        new Thread(this).start();
        utazonPlugin.getLogger().info("socketサーバーを起動しました");
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            isRunning = true;

            while (isRunning) {
                Socket clientSocket = serverSocket.accept();
                handleClient(clientSocket);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stopServer() {
        try {
            isRunning = false;
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleClient(Socket clientSocket) throws IOException {

        InputStream inputStream = null;
        OutputStream outputStream = null;

        try {
            inputStream = clientSocket.getInputStream();
            outputStream = clientSocket.getOutputStream();

            byte[] buffer = new byte[1024];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                String receivedData = new String(buffer, 0, bytesRead);

                String[] receivedJson = gson.fromJson(receivedData, String[].class);

                // UUID取得
                UUID uuid = null;
                try{
                    uuid = UUID.fromString(receivedJson[1]);
                } catch (IllegalArgumentException e){
                    outputStream.write("Invalid UUID".getBytes());
                    outputStream.flush();
                }

                if (receivedJson[0].equalsIgnoreCase("getBalance")){
                    // Balance取得
                    double PlayerBalance = ecoApi.getBalance(uuid);

                    // Balanceを返す
                    String responseData = String.valueOf(PlayerBalance);
                    outputStream.write(responseData.getBytes());
                    outputStream.flush();

                }else if ((receivedJson[0].equalsIgnoreCase("withdrawPlayer"))){
                    UUID finalUuid = uuid;
                    utazonPlugin.getServer().getScheduler().callSyncMethod(utazonPlugin, () -> {
                        ecoApi.withdrawPlayer(finalUuid, Double.parseDouble(receivedJson[2]), "ウェブショップ『Utazon』で購入", receivedJson[3]);
                        return null;
                    });
                    outputStream.write("Success".getBytes());
                    outputStream.flush();
                }else if ((receivedJson[0].equalsIgnoreCase("depositPlayer"))){
                    UUID finalUuid = uuid;
                    utazonPlugin.getServer().getScheduler().callSyncMethod(utazonPlugin, () -> {
                        ecoApi.depositPlayer(finalUuid, Double.parseDouble(receivedJson[2]), "ウェブショップ『Utazon』からの返金", receivedJson[3]);
                        return null;
                    });
                    outputStream.write("Success".getBytes());
                    outputStream.flush();
                }
            }

        } catch (Exception e){
            e.printStackTrace();
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
            if (clientSocket != null) {
                clientSocket.close();
            }
        }
    }

    public void loadSocketSettings(){
        FileConfiguration section = utazonPlugin.getConfig();
        this.port = section.getInt("socket.port");
    }
}