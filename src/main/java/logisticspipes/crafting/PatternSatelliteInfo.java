package logisticspipes.crafting;

import java.io.IOException;
import java.util.Locale;

import com.github.bsideup.jabel.Desugar;

import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;

@Desugar
public record PatternSatelliteInfo(int id, int x, int y, int z, int dimension, int distance, boolean favorite,
        String uuid, String displayName) {

    public PatternSatelliteInfo {
        uuid = uuid == null ? "" : uuid;
        displayName = displayName == null || displayName.trim().isEmpty() ? Integer.toString(id) : displayName.trim();
    }

    public int getId() {
        return id;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public int getDimension() {
        return dimension;
    }

    public int getDistance() {
        return distance;
    }

    public boolean isFavorite() {
        return favorite;
    }

    public String getUuid() {
        return uuid;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSearchText() {
        return (displayName + " #" + id + " s" + id + " satellite " + id + " " + uuid + " d" + dimension
            + " dim " + dimension
            + " " + x + " " + y + " " + z + " " + x + "," + y + "," + z
            + (favorite ? " favorite chip memory" : "")).toLowerCase(Locale.ROOT);
    }

    public void writeData(LPDataOutputStream data) throws IOException {
        data.writeInt(id);
        data.writeInt(x);
        data.writeInt(y);
        data.writeInt(z);
        data.writeInt(dimension);
        data.writeInt(distance);
        data.writeBoolean(favorite);
        data.writeUTF(uuid);
        data.writeUTF(displayName);
    }

    public static PatternSatelliteInfo readData(LPDataInputStream data) throws IOException {
        return new PatternSatelliteInfo(
            data.readInt(),
            data.readInt(),
            data.readInt(),
            data.readInt(),
            data.readInt(),
            data.readInt(),
            data.readBoolean(),
            data.readUTF(),
            data.readUTF());
    }
}
