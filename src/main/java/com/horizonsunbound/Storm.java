package com.horizonsunbound;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * One storm. Plain data, not an entity: the server keeps and ticks storms in {@link StormManager} no matter which
 * chunks are loaded, and mirrors them to clients, so neither entity tracking ranges nor entity-culling mods can
 * make a storm vanish. The client copy moves along the same velocity between server updates.
 */
public final class Storm {
    public static final int MIN_DIAMETER = 100, MAX_DIAMETER = 200, DEFAULT_DIAMETER = 150;
    private static final double SPEED = .01;   // blocks per tick: about 12 blocks a minute, very slow on purpose
    static final int LIFE = 15 * 60 * 20; // every storm lasts 15 minutes
    static final int NO_GROUND = Integer.MIN_VALUE;
    public static final int WAVE_CHARGE = 60;    // ticks the column flares before a wave leaves it
    /** Ticks of a storm's birth (client: StormBirth's timeline); it does nothing to the world until then. */
    public static final int BIRTH = 400;
    /** Ticks of a storm's death at the end of its life (client: it comes apart, StormRenderer). */
    public static final int DEATH = 400;

    /** Client side: the storms of the client's dimension (ClientStorms keeps it); common code reads it on the client only. */
    public static Collection<Storm> CLIENT = List.of();
    /** Client side: how much of the vanilla rain shows where the camera is (dry pockets under Overworld storms). */
    public static float clientRainFactor = 1;

    /** Something at a block position that clients have to see: a seam (pairs, a = pair index) or a coming strike (a = age due). */
    public record Mark(byte kind, int x, int y, int z, int a) {
        public static final byte SEAM = 0, STRIKE = 1, PULSE = 2; // PULSE: a sculk storm heard a noise here (a = age)
    }

    public final UUID id;
    public final StormType type;
    public final int diameter;
    public double x, y, z;
    public double xo, yo, zo;     // last tick's position, for smooth drawing between ticks
    public float vx, vy, vz;      // movement per tick
    public int age;               // ticks since it rose; drives the animation
    public float[] rimCache;      // client only: the cloud rim laid out once (StormRenderer)
    public final List<Mark> marks = new ArrayList<>(); // rebuilt by the server, not saved
    public int structures;        // brought so far
    public StormType mix;         // a merged storm's second kind (its bands alternate colors), else null
    public transient boolean broken; // server: its effects threw; they stay off (StormManager), the storm stays in the sky
    public transient float cost;     // server: nanoseconds its effects took a tick, smoothed (/storm debug)
    public boolean grand;         // the great storm: all four kinds at once (StormCutscenes: the finale)
    public int omen;              // ticks until it rises; while > 0 only its omens show (dark sky, wind, nervous animals)
    UUID target;                  // server: the storm it is heading for, to merge with
    int cue = -1;                 // server: ticks until it rises with its cutscene (StormCutscenes), -1 = not yet seen
    int held;                     // server: ticks it has waited, omens up, for someone to come and see it
    private float heading;        // server only from here on
    private int life = LIFE;
    private double ground;

    public Storm(UUID id, StormType type, int diameter, double x, double y, double z) {
        this.id = id;
        this.type = type;
        this.diameter = Mth.clamp(diameter, MIN_DIAMETER, MAX_DIAMETER);
        this.x = xo = x;
        this.y = yo = y;
        this.z = zo = z;
        this.ground = y;
    }

    public Storm copy() {
        Storm s = new Storm(id, type, diameter, x, y, z);
        s.vx = vx;
        s.vy = vy;
        s.vz = vz;
        s.age = age;
        s.mix = mix;
        s.omen = omen;
        s.life = life;
        s.grand = grand;
        s.marks.addAll(marks);
        return s;
    }

    public float radius() { return diameter / 2f; }
    /** Radius of the area its weather effects reach. */
    public float zone() { return diameter * 1.5f; }

    /** True while it is still being born. */
    public boolean newborn() { return age < BIRTH; }

    /** True inside the column (within its wall, between its foot and the cap; the column is 3 radii tall). */
    public boolean inside(double px, double py, double pz) {
        return age >= BIRTH / 2 && dist(px, pz) < radius() * .97f && py > y - 8 && py < y + radius() * 3;
    }

    /** Horizontal distance from the storm's axis. */
    public double dist(double px, double pz) { return Math.sqrt((px - x) * (px - x) + (pz - z) * (pz - z)); }

    /** Ticks between waves (Nether heat, End gravity); 0 = no waves. */
    public int wavePeriod() { return has(StormType.NETHER) ? 400 : has(StormType.END) ? 1500 : 0; } // a merged storm by either kind: its effects run for both

    /** 0..1 how far its omens have grown (1 = about to rise). */
    public float omenProgress() { return 1 - omen / (float) StormManager.OMEN_TICKS; }

    /**
     * 0..1: the column swells up before a wave (the warning) and dies down smoothly within a second once the wave
     * is out; 0 the rest of the time. Not cut off at the wave start: that looked like a glitch.
     */
    public float charge(float age) {
        int p = wavePeriod();
        if (p == 0 || age < BIRTH) return 0;
        float ph = age % p, x = ph < WAVE_CHARGE ? ph / WAVE_CHARGE : 1 - (ph - WAVE_CHARGE) / 20;
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    /** Radius of the running wave's front at this age, or -1 when no wave is out. */
    public float waveFront(float age) {
        int p = wavePeriod();
        if (p == 0 || age < BIRTH) return -1;
        float ph = age % p - WAVE_CHARGE, f = ph * (has(StormType.NETHER) ? 1.5f : 1f);
        return ph < 0 || f > zone() ? -1 : f;
    }

    /** Overworld storms: 0 (dry pocket) .. 1 (rain) at a spot under the storm; the pockets turn slowly. */
    public float wetness(double px, double pz) {
        double dx = px - x, dz = pz - z;
        float ph = (seed() & 1023) / 163f;
        float v = Mth.sin((float) Math.atan2(dz, dx) * 2 + ph + age * .0004f) + .5f * Mth.sin((float) Math.sqrt(dx * dx + dz * dz) / 45 - age * .0015f + ph);
        float w = Mth.clamp((v + .45f) / .3f, 0, 1);
        return w * w * (3 - 2 * w);
    }

    public long seed() { return id.getLeastSignificantBits(); }
    void setHeading(float radians) { heading = radians; }
    public int life() { return life; }
    public void life(int ticks) { life = ticks; }

    /** 0..1 how far through its death it is (0 for most of its life). */
    public float dying() { return life >= DEATH ? 0 : Mth.clamp(1 - life / (float) DEATH, 0, 1); }

    /** Client tick: it moves and ages, and its life runs down (the server's copy does that in tick()). */
    public void clientStep() {
        step();
        if (life > 0) life--;
    }
    /** True if this storm is (or, merged, partly is) of the given kind. */
    public boolean has(StormType t) { return grand || type == t || mix == t; }

    /** The color of band k: a merged storm alternates its two kinds' colors. */
    public StormType bandType(int k) { return grand ? StormType.values()[k % 4] : mix != null && (k & 1) == 1 ? mix : type; }

    /** One tick of movement along the current velocity; runs on both sides. */
    public void step() {
        xo = x;
        yo = y;
        zo = z;
        x += vx;
        y += vy;
        z += vz;
        age++;
    }

    /** Server tick: wander slowly, follow the ground, age. Returns false once the storm has died down. */
    boolean tick(ServerLevel level) {
        if (--life <= 0) return false;
        if (target == null) heading += (level.random.nextFloat() - .5f) * .02f;
        if (age % 20 == 0) { // once a second is plenty at this speed
            int g = groundUnder(level, x, z, radius(), false);
            if (g != NO_GROUND) ground = g;
        }
        vx = (float) (Mth.cos(heading) * SPEED);
        vz = (float) (Mth.sin(heading) * SPEED);
        vy = (float) Mth.clamp(ground - y, -.05, .05); // follow the terrain without jumps
        step();
        return true;
    }

    /**
     * Lowest ground under the storm's whole foot: the center plus two rings out to its edge. Standing on the
     * center alone, a storm on a hilltop hangs in the air over the valley next to it; this way the column always
     * reaches the ground and simply sinks into higher terrain. Unloaded chunks are skipped rather than loaded
     * (storms far from players still tick); NO_GROUND when nothing under it is loaded and loadCenter is false.
     */
    static int groundUnder(ServerLevel level, double x, double z, float radius, boolean loadCenter) {
        int low = NO_GROUND;
        for (int i = -1; i < 20; i++) {
            float ang = i * Mth.TWO_PI / (i < 8 ? 8 : 12), dist = i < 0 ? 0 : radius * (i < 8 ? .55f : 1);
            int bx = Mth.floor(x + Mth.cos(ang) * dist), bz = Mth.floor(z + Mth.sin(ang) * dist);
            if (!(i < 0 && loadCenter) && !level.hasChunk(bx >> 4, bz >> 4)) continue;
            int h = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            low = low == NO_GROUND ? h : Math.min(low, h);
        }
        return low;
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Type", type.id);
        tag.putInt("Diameter", diameter);
        tag.putDouble("X", x);
        tag.putDouble("Y", y);
        tag.putDouble("Z", z);
        tag.putFloat("Heading", heading);
        tag.putInt("Life", life);
        tag.putInt("Age", age);
        tag.putInt("Structures", structures);
        if (mix != null) tag.putString("Mix", mix.id);
        tag.putInt("Omen", omen);
        tag.putBoolean("Grand", grand);
        if (target != null) tag.putUUID("Target", target);
        return tag;
    }

    static Storm load(CompoundTag tag) {
        Storm s = new Storm(tag.getUUID("Id"), StormType.byId(tag.getString("Type")), tag.getInt("Diameter"),
                tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
        s.heading = tag.getFloat("Heading");
        s.life = tag.getInt("Life");
        s.age = tag.getInt("Age");
        s.structures = tag.getInt("Structures");
        if (tag.contains("Mix")) s.mix = StormType.byId(tag.getString("Mix"));
        s.omen = tag.getInt("Omen");
        s.grand = tag.getBoolean("Grand");
        if (tag.hasUUID("Target")) s.target = tag.getUUID("Target");
        return s;
    }

    /** What clients need to draw it: identity, look, position, velocity and age. */
    public static final StreamCodec<FriendlyByteBuf, Storm> STREAM_CODEC = StreamCodec.of((buf, s) -> {
        buf.writeUUID(s.id);
        buf.writeByte(s.type.ordinal());
        buf.writeShort(s.diameter);
        buf.writeDouble(s.x);
        buf.writeDouble(s.y);
        buf.writeDouble(s.z);
        buf.writeFloat(s.vx);
        buf.writeFloat(s.vy);
        buf.writeFloat(s.vz);
        buf.writeVarInt(s.age);
        buf.writeByte(s.mix == null ? -1 : s.mix.ordinal());
        buf.writeVarInt(s.omen);
        buf.writeVarInt(s.life);
        buf.writeBoolean(s.grand);
        buf.writeVarInt(s.marks.size());
        for (Mark m : s.marks) {
            buf.writeByte(m.kind());
            buf.writeInt(m.x());
            buf.writeInt(m.y());
            buf.writeInt(m.z());
            buf.writeVarInt(m.a());
        }
    }, buf -> {
        UUID id = buf.readUUID();
        StormType[] types = StormType.values();
        Storm s = new Storm(id, types[Mth.clamp(buf.readByte(), 0, types.length - 1)], buf.readShort(),
                buf.readDouble(), buf.readDouble(), buf.readDouble());
        s.vx = buf.readFloat();
        s.vy = buf.readFloat();
        s.vz = buf.readFloat();
        s.age = buf.readVarInt();
        int mix = buf.readByte();
        s.mix = mix < 0 ? null : types[Mth.clamp(mix, 0, types.length - 1)];
        s.omen = buf.readVarInt();
        s.life = buf.readVarInt();
        s.grand = buf.readBoolean();
        for (int i = Math.min(buf.readVarInt(), 256); i > 0; i--)
            s.marks.add(new Mark(buf.readByte(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readVarInt()));
        return s;
    });
}
