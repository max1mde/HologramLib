package com.maximde.hologramlib.hologram;

import com.github.retrooper.packetevents.manager.player.PlayerManager;
import com.github.retrooper.packetevents.protocol.entity.type.EntityType;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import com.maximde.hologramlib.HologramLib;
import com.maximde.hologramlib.utils.BukkitTasks;
import com.maximde.hologramlib.utils.TaskHandle;
import com.maximde.hologramlib.utils.Vector3F;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import lombok.Getter;
import lombok.experimental.Accessors;
import me.tofaa.entitylib.meta.EntityMeta;
import me.tofaa.entitylib.meta.display.BlockDisplayMeta;
import me.tofaa.entitylib.meta.display.ItemDisplayMeta;
import me.tofaa.entitylib.meta.display.TextDisplayMeta;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

public abstract class Hologram<T extends Hologram<T>> {

    @SuppressWarnings("unchecked")
    protected T self() {
        return (T) this;
    }

    @Getter
    protected final List<Player> viewers = new CopyOnWriteArrayList<>();

    @Getter
    protected Location location;

    @Getter
    protected boolean dead = true;

    @Getter @Accessors(chain = true)
    protected long updateTaskPeriod = 20L * 3;

    @Getter @Accessors(chain = true)
    protected double nearbyEntityScanningDistance = 30.0;

    @Getter @Accessors(chain = true)
    protected Display.Billboard billboard = Display.Billboard.CENTER;

    @Getter @Accessors(chain = true)
    protected int teleportDuration = 10;

    @Getter @Accessors(chain = true)
    protected int interpolationDurationTransformation = 10;

    @Getter @Accessors(chain = true)
    protected double viewRange = 1.0;

    @Getter
    protected final String id;

    @Getter @Accessors(chain = true)
    protected int entityID;

    protected Vector3f scale = new Vector3f(1, 1, 1);
    protected Vector3f translation = new Vector3f(0, 0F, 0);

    protected Quaternion4f rightRotation = new Quaternion4f(0, 0, 0, 1);
    protected Quaternion4f leftRotation = new Quaternion4f(0, 0, 0, 1);

    /**
     * The render mode determines which players can see the hologram:
     * - NEARBY: Only players within viewing distance
     * - ALL: All players on the server
     * - VIEWER_LIST: Only specific players added as viewers
     * - NONE: Hologram is not visible to any players
     */
    @Getter
    protected final RenderMode renderMode;

    @Getter
    protected final EntityType entityType;

    @Getter
    protected TaskHandle task;

    @Getter
    /**
     * Do not use this if you don't know what you are doing!
     * this interface for accessing specific setters is only for internal methods.
     */
    private Internal internalAccess;

    private final MetaSender metaSender;

    public interface Internal {
        /**
         * Use Hologram#telport(Location) if you want to move the hologram instead!
         * @param location
         */
        Hologram setLocation(Location location);
        Hologram setDead(boolean dead);
        Hologram setEntityId(int entityId);
        Hologram sendPacket(PacketWrapper<?> packet);
        Hologram updateAffectedPlayers();
    }

    protected Hologram(String id, EntityType entityType) {
        this(id, RenderMode.NEARBY, entityType);
    }

    protected Hologram(String id, RenderMode renderMode, EntityType entityType) {
        this(id, renderMode, entityType, new BaseMetaSender(){});
    }

    protected Hologram(String id, EntityType entityType, MetaSender metaSender) {
        this(id, RenderMode.NEARBY, entityType, metaSender);
    }

    protected Hologram(String id, RenderMode renderMode, EntityType entityType, MetaSender metaSender) {
        this.entityType = entityType;
        validateId(id);
        this.id = id.toLowerCase();
        this.renderMode = renderMode;
        this.metaSender = metaSender;
        this.internalAccess = new InternalSetters();
        startRunnable();
    }

    private void startRunnable() {
        if (task != null) return;
        task = BukkitTasks.runTaskTimer(this::updateAffectedPlayers, 60L, updateTaskPeriod);
    }

    public abstract static class BaseMetaSender implements MetaSender {
        @Override
        public BlockDisplayMeta blockDisplay(Player player, BlockDisplayMeta blockDisplayMeta) {
            return blockDisplayMeta;
        }

        @Override
        public ItemDisplayMeta itemDisplay(Player player, ItemDisplayMeta itemDisplayMeta) {
            return itemDisplayMeta;
        }

        @Override
        public TextDisplayMeta textDisplay(Player player, TextDisplayMeta textDisplayMeta) {
            return textDisplayMeta;
        }
    }

    private class InternalSetters implements Internal {
        @Override
        public Hologram<?> setLocation(Location location) {
            if (location == null) {
                throw new IllegalArgumentException("Location cannot be null");
            }
            Hologram.this.location = location;
            return Hologram.this;
        }

        @Override
        public Hologram<?> setDead(boolean dead) {
            Hologram.this.dead = dead;
            return Hologram.this;
        }

        @Override
        public Hologram<?> setEntityId(int entityId) {
            Hologram.this.entityID = entityId;
            return Hologram.this;
        }

        @Override
        public Hologram<?> sendPacket(PacketWrapper<?> packet) {
            Hologram.this.sendPacket(packet);
            return Hologram.this;
        }

        @Override
        public Hologram<?> updateAffectedPlayers() {
            Hologram.this.updateAffectedPlayers();
            return Hologram.this;
        }
    }


    /**
     * Sends update packets to all viewers.
     * Should be called after making any changes to the hologram object.
     */
    public T update() {
        if(location == null) return self();
        BukkitTasks.runTask( () -> {
            updateAffectedPlayers();
            sendPacket(createMeta());
        });
        return self();
    }

    protected void validateId(String id) {
        if (id.contains(" ")) {
            throw new IllegalArgumentException("The hologram ID cannot contain spaces! (" + id + ")");
        }
    }

    protected com.github.retrooper.packetevents.util.Vector3f toVector3f(Vector3f vector) {
        return new com.github.retrooper.packetevents.util.Vector3f(vector.x, vector.y, vector.z);
    }

    /**
     * Use HologramManager#remove(Hologram.class); instead!
     * Only if you want to manage the holograms yourself and don't want to use the animation system use this
     */
    public void kill() {
        WrapperPlayServerDestroyEntities packet = new WrapperPlayServerDestroyEntities(this.entityID);
        sendPacket(packet);
        this.dead = true;
    }

    public T teleport(Location location) {
        WrapperPlayServerEntityTeleport packet = new WrapperPlayServerEntityTeleport(this.entityID, SpigotConversionUtil.fromBukkitLocation(location), false);
        this.location = location;
        sendPacket(packet);
        return self();
    }

    protected abstract EntityMeta createMeta();

    public Vector3F getTranslation() {
        return new Vector3F(this.translation.x, this.translation.y, this.translation.z);
    }


    public Vector3F getScale() {
        return new Vector3F(this.scale.x, this.scale.y, this.scale.z);
    }


    /**
     * Updates which players should be able to see this hologram based on the render mode.
     * For NEARBY mode, checks player distance and world.
     * For ALL mode, adds all online players.
     * For VIEWER_LIST mode, only uses manually added viewers.
     * Removes viewers who are too far away or in different worlds.
     */
    private void updateAffectedPlayers() {
        if (this.renderMode == RenderMode.VIEWER_LIST) return;

        if(this.location == null) {
            Bukkit.getLogger().log(Level.WARNING, "Tried to update hologram with ID " + this.id + " entity type " + this.entityType.getName().getKey() + ". But the location is not set!");
            return;
        }
        List<Player> newPlayers = new ArrayList<>();
        List<Player> toRemove = viewers.stream()
                .filter(player -> player.isOnline() && (player.getWorld() != this.location.getWorld() || player.getLocation().distance(this.location) > 20))
                .peek(player -> {
                    WrapperPlayServerDestroyEntities packet = new WrapperPlayServerDestroyEntities(this.entityID);
                    HologramLib.getPlayerManager().sendPacket(player, packet);
                })
                .toList();
        viewers.removeAll(toRemove);


        if (this.renderMode == RenderMode.ALL) {
            newPlayers.addAll(new ArrayList<>(Bukkit.getOnlinePlayers()));
        } else if (this.renderMode == RenderMode.NEARBY) {
            newPlayers.addAll(new ArrayList<>(Bukkit.getOnlinePlayers()));
            //TODO better implementation
            /*
             this.location.getWorld().getNearbyEntities(this.location, nearbyEntityScanningDistance, nearbyEntityScanningDistance, nearbyEntityScanningDistance)
             .stream()
             .filter(entity -> entity instanceof Player)
             .forEach(entity -> newPlayers.add((Player) entity));
              */
        }
        newPlayers.removeAll(this.viewers);
        if(!dead && entityID != 0) {
            WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(
                    this.entityID, Optional.of(UUID.randomUUID()), this.entityType,
                    new Vector3d(location.getX(), location.getY(), location.getZ()), 0f, 0f, 0f, 0, Optional.empty()
            );
            this.sendPacket(packet, newPlayers);
            this.sendPacket(createMeta(), newPlayers);
        }
        this.viewers.addAll(newPlayers);
    }

    /**
     * Attaches this hologram to another entity, making it ride the target entity.
     *
     * @param entityID The entity ID to attach the hologram to
     * @param persistent If the hologram should be re-attached automatically or not TODO
     */
    public void attach(int entityID, boolean persistent) {
        int[] hologramToArray = { this.entityID };
        WrapperPlayServerSetPassengers attachPacket = new WrapperPlayServerSetPassengers(entityID, hologramToArray);
        BukkitTasks.runTask(() -> {
            sendPacket(attachPacket);
        });
    }

    protected void sendPacket(EntityMeta meta) {
        sendPacket(meta, this.viewers);
    }

    protected void sendPacket(EntityMeta meta, List<Player> players) {
        if (this.renderMode == RenderMode.NONE || players.isEmpty()) {
            return;
        }
        Map<Player, PacketWrapper<?>> playerPackets = new HashMap<>();
        for (Player player : players) {
            EntityMeta modifiedMeta;
            if (meta instanceof TextDisplayMeta) {
                modifiedMeta = metaSender.textDisplay(player, (TextDisplayMeta) meta);
            } else if (meta instanceof BlockDisplayMeta) {
                modifiedMeta = metaSender.blockDisplay(player, (BlockDisplayMeta) meta);
            } else if (meta instanceof ItemDisplayMeta) {
                modifiedMeta = metaSender.itemDisplay(player, (ItemDisplayMeta) meta);
            } else {
                throw new IllegalArgumentException("Unsupported meta type: " + meta.getClass());
            }
            playerPackets.put(player, modifiedMeta.createPacket());
        }

        sendBatchPackets(playerPackets);
    }
    public interface MetaSender {
        BlockDisplayMeta blockDisplay(Player player, BlockDisplayMeta blockDisplayMeta);
        ItemDisplayMeta itemDisplay(Player player, ItemDisplayMeta itemDisplayMeta);
        TextDisplayMeta textDisplay(Player player, TextDisplayMeta textDisplayMeta);
    }

    protected void sendBatchPackets(Map<Player, PacketWrapper<?>> playerPackets) {
        if (this.renderMode == RenderMode.NONE) return;
        PlayerManager playerManager = HologramLib.getPlayerManager();
        playerPackets.forEach(playerManager::sendPacket);
    }

    protected void sendPacket(PacketWrapper<?> packet) {
        if (this.renderMode == RenderMode.NONE) return;
        viewers.forEach(player -> HologramLib.getPlayerManager().sendPacket(player, packet));
    }

    protected void sendPacket(PacketWrapper<?> packet, List<Player> players) {
        if (this.renderMode == RenderMode.NONE) return;
        players.forEach(player -> HologramLib.getPlayerManager().sendPacket(player, packet));
    }

    /**
     * Period in ticks between updates of the hologram's viewer list.
     * Lower values mean more frequent updates but higher server load.
     * Default is 60 ticks (3 seconds).
     */
    public T setUpdateTaskPeriod(long updateTaskPeriod) {
        this.updateTaskPeriod = updateTaskPeriod;
        return self();
    }

    public T setNearbyEntityScanningDistance(double nearbyEntityScanningDistance) {
        this.nearbyEntityScanningDistance = nearbyEntityScanningDistance;
        return self();
    }

    public T setBillboard(Display.Billboard billboard) {
        this.billboard = billboard;
        return self();
    }

    @Deprecated(forRemoval = true)
    public int getInterpolationDurationRotation() {
        return this.teleportDuration;
    }

    @Deprecated(forRemoval = true)
    public T setInterpolationDurationRotation(int teleportDuration) {
        this.teleportDuration = teleportDuration;
        return self();
    }

    public T setTeleportDuration(int teleportDuration) {
        this.teleportDuration = teleportDuration;
        return self();
    }

    public T setInterpolationDurationTransformation(int interpolationDurationTransformation) {
        this.interpolationDurationTransformation = interpolationDurationTransformation;
        return self();
    }

    public T setViewRange(double viewRange) {
        this.viewRange = viewRange;
        return self();
    }

    public T setEntityID(int entityID) {
        this.entityID = entityID;
        return self();
    }

    public T setLeftRotation(float x, float y, float z, float w) {
        this.leftRotation = new Quaternion4f(x, y, z, w);
        return self();
    }

    public T setRightRotation(float x, float y, float z, float w) {
        this.rightRotation = new Quaternion4f(x, y, z, w);
        return self();
    }

    public T setTranslation(float x, float y, float z) {
        this.translation = new Vector3f(x, y, z);
        return self();
    }

    public T setTranslation(Vector3F translation) {
        this.translation = new Vector3f(translation.x, translation.y, translation.z);
        return self();
    }

    public T addViewer(Player player) {
        this.viewers.add(player);
        if(this.location == null) return self();
        WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(
                this.entityID, Optional.of(UUID.randomUUID()), this.entityType,
                new Vector3d(location.getX(), location.getY(), location.getZ()), 0f, 0f, 0f, 0, Optional.empty()
        );
        this.sendPacket(packet, List.of(player));
        this.sendPacket(createMeta(), List.of(player));
        return self();
    }

    public T removeViewer(Player player) {
        this.viewers.remove(player);
        if(this.location == null) return self();
        WrapperPlayServerDestroyEntities packet = new WrapperPlayServerDestroyEntities(this.entityID);
        HologramLib.getPlayerManager().sendPacket(player, packet);
        return self();
    }

    public T addAllViewers(List<Player> viewerList) {
        this.viewers.addAll(viewerList);
        if(this.location == null) return self();
        WrapperPlayServerSpawnEntity packet = new WrapperPlayServerSpawnEntity(
                this.entityID, Optional.of(UUID.randomUUID()), this.entityType,
                new Vector3d(location.getX(), location.getY(), location.getZ()), 0f, 0f, 0f, 0, Optional.empty()
        );
        this.sendPacket(packet, viewerList);
        return self();
    }

    public T removeAllViewers() {
        if(this.location == null) {
            this.viewers.clear();
            return self();
        }
        WrapperPlayServerDestroyEntities packet = new WrapperPlayServerDestroyEntities(this.entityID);
        this.sendPacket(packet, this.viewers);
        this.viewers.clear();
        return self();
    }

    public T setScale(float x, float y, float z) {
        this.scale = new Vector3f(x, y, z);
        return self();
    }

    public T setScale(Vector3F scale) {
        this.scale = new Vector3f(scale.x, scale.y, scale.z);
        return self();
    }

    protected abstract T copy();
    protected abstract T copy(String id);
}