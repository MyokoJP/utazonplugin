package work.utakatanet.utazonplugin.util;

import com.github.kuripasanda.economyutilsapi.api.EconomyUtilsApi;
import com.google.gson.Gson;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import work.utakatanet.utazonplugin.UtazonPlugin;
import work.utakatanet.utazonplugin.data.ProductItem;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.UUID;

import static work.utakatanet.utazonplugin.post.detectOrder.allowBlocks;

public class SocketServer implements Runnable {

    private static final UtazonPlugin plugin = UtazonPlugin.plugin;
    private static final Gson gson = UtazonPlugin.gson;
    private static final EconomyUtilsApi ecoApi = UtazonPlugin.ecoApi;

    private ServerSocket serverSocket;
    public boolean isRunning = false;
    private int port;

    public SocketServer() {
        loadSettings();
    }

    public void start() {
        new Thread(this).start();
        plugin.getLogger().info("Socketサーバーを起動しました");
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
        } catch (SocketException e){
            if (!e.getMessage().contains("Socket closed")){
                e.printStackTrace();
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
        plugin.getLogger().info("Socketサーバーが停止されました");
    }

    private void handleClient(Socket clientSocket) throws IOException {

        try (InputStream inputStream = clientSocket.getInputStream(); OutputStream outputStream = clientSocket.getOutputStream()) {

            byte[] buffer = new byte[1024];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                String receivedData = new String(buffer, 0, bytesRead);

                String[] receivedJson = gson.fromJson(receivedData, String[].class);


                if (receivedJson[0].equalsIgnoreCase("getBalance")) {
                    // UUID取得
                    UUID uuid = null;
                    try {
                        uuid = UUID.fromString(receivedJson[1]);
                    } catch (IllegalArgumentException e) {
                        outputStream.write("Invalid UUID".getBytes());
                        outputStream.flush();
                    }
                    // Balance取得
                    double PlayerBalance = ecoApi.getBalance(uuid);

                    // Balanceを返す
                    String responseData = String.valueOf(PlayerBalance);
                    outputStream.write(responseData.getBytes());
                    outputStream.flush();

                } else if (receivedJson[0].equalsIgnoreCase("withdrawPlayer")) {
                    // UUID取得
                    UUID uuid = null;
                    try {
                        uuid = UUID.fromString(receivedJson[1]);
                    } catch (IllegalArgumentException e) {
                        outputStream.write("Invalid UUID".getBytes());
                        outputStream.flush();
                    }

                    UUID finalUUID = uuid;
                    plugin.getServer().getScheduler().callSyncMethod(plugin, () -> {
                        ecoApi.withdrawPlayer(finalUUID, Double.parseDouble(receivedJson[2]), receivedJson[3], receivedJson[4]);
                        return null;
                    });
                    outputStream.write("Success".getBytes());
                    outputStream.flush();

                } else if (receivedJson[0].equalsIgnoreCase("depositPlayer")) {
                    // UUID取得
                    UUID uuid = null;
                    try {
                        uuid = UUID.fromString(receivedJson[1]);
                    } catch (IllegalArgumentException e) {
                        outputStream.write("Invalid UUID".getBytes());
                        outputStream.flush();
                    }

                    UUID finalUUID = uuid;
                    plugin.getServer().getScheduler().callSyncMethod(plugin, () -> {
                        ecoApi.depositPlayer(finalUUID, Double.parseDouble(receivedJson[2]), receivedJson[3], receivedJson[4]);
                        return null;
                    });
                    outputStream.write("Success".getBytes());
                    outputStream.flush();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (clientSocket != null) {
                clientSocket.close();
            }
        }
    }

    public void loadSettings() {
        FileConfiguration section = plugin.getConfig();
        this.port = section.getInt("socket.port");
    }
}
