package logisticspipes.crafting;

import java.io.IOException;
import java.util.Locale;

import logisticspipes.network.LPDataInputStream;
import logisticspipes.network.LPDataOutputStream;

public class PatternSatelliteInfo {

    private final int id;
    private final int x;
    private final int y;
    private final int z;
    private final int dimension;
    private final int distance;
    private final boolean favorite;

    public PatternSatelliteInfo(int id, int x, int y, int z, int dimension, int distance, boolean favorite) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
        this.distance = distance;
        this.favorite = favorite;
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

    public String getSearchText() {
        return ("#" + id + " s" + id + " satellite " + id + " d" + dimension + " dim " + dimension
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
    }

    public static PatternSatelliteInfo readData(LPDataInputStream data) throws IOException {
        return new PatternSatelliteInfo(
                data.readInt(),
                data.readInt(),
                data.readInt(),
                data.readInt(),
                data.readInt(),
                data.readInt(),
                data.readBoolean());
    }
}
