package logisticspipes.request.debug;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;

import javax.swing.*;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;

import org.lwjgl.input.Keyboard;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.packets.debug.CraftingRequestDebugRequest;
import logisticspipes.proxy.MainProxy;

import static javax.swing.JComponent.*;

public final class CraftingRequestDebugClient {

    private static boolean requestKeyWasDown = false;

    private CraftingRequestDebugClient() {}

    /**
     * Polls the client-side debug key binding and asks the server for the current crafting request snapshot.
     */
    public static void clientTick(ClientTickEvent event) {
        if (event.phase != Phase.END || !Keyboard.isCreated()) {
            return;
        }
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        boolean control = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
        boolean requestKeyDown = shift && control && Keyboard.isKeyDown(Keyboard.KEY_T);
        if (requestKeyDown && !requestKeyWasDown) {
            if (FMLClientHandler.instance().getClient().theWorld == null) {
                requestKeyWasDown = requestKeyDown;
                return;
            }
            MainProxy.sendPacketToServer(PacketHandler.getPacket(CraftingRequestDebugRequest.class));
        }
        requestKeyWasDown = requestKeyDown;
    }

    /**
     * Opens a Swing window containing the server-built crafting request debug snapshot.
     */
    public static void openWindow(String title, String payload) {
        SwingUtilities.invokeLater(() -> {
            JTextArea text = new JTextArea(payload == null ? "" : payload);
            text.setEditable(false);
            text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            text.setCaretPosition(0);

            JFrame frame = new JFrame(title == null || title.isEmpty() ? "Crafting Request Debug" : title);
            addMacCloseBinding(frame);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.getContentPane().add(new JScrollPane(text), BorderLayout.CENTER);
            frame.setPreferredSize(new Dimension(1100, 750));
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    private static void addMacCloseBinding(JFrame frame) {
        frame.getRootPane().getActionMap().put("close-window", new CloseAction(frame));
        frame.getRootPane().getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            .put(KeyStroke.getKeyStroke("control W"), "close-window");
        frame.getRootPane().getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
            .put(KeyStroke.getKeyStroke("meta W"), "close-window");
    }

    public static class CloseAction extends AbstractAction {

        private final Window window;
        public CloseAction(Window window) {
            this.window = window;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (window == null) return;
            window.dispatchEvent(new WindowEvent(
                window, WindowEvent.WINDOW_CLOSING));
        }

    }

    /**
     * Sends a small chat fallback when the server could not build a snapshot response.
     */
    public static void notifyUnavailable(EntityPlayer player) {
        if (player != null) {
            player.addChatMessage(new ChatComponentText("No crafting request debug snapshot available."));
        }
    }
}
