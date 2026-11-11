package dev.monkmod.data;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * Persistent monk progress of one player. Stored as a Fabric data attachment on the player,
 * copied on death and saved with the player data.
 */
public final class MonkData {
	public static final Codec<MonkData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("level", -1).forGetter(d -> d.level),
			Codec.INT.optionalFieldOf("progress", 0).forGetter(d -> d.progress),
			UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("petted", List.of()).forGetter(d -> List.copyOf(d.petted))
	).apply(instance, MonkData::new));

	private int level;
	private int progress;
	private int maxProgress = -1;
	private final Set<UUID> petted;

	/** Set when level/abilities must be (re)sent to the client. */
	public transient boolean stateDirty = true;
	/** Set when progress must be (re)sent to the client. */
	public transient boolean progressDirty = true;

	public MonkData() {
		this(-1, 0, List.of());
	}

	private MonkData(int level, int progress, List<UUID> petted) {
		this.level = level;
		this.progress = progress;
		this.petted = new HashSet<>(petted);
	}

	public int level() {
		return level;
	}

	public void setLevel(int level) {
		this.level = level;
		this.progress = 0;
		this.maxProgress = -1;
		this.petted.clear();
		this.stateDirty = true;
		this.progressDirty = true;
	}

	public int progress() {
		return progress;
	}

	public int maxProgress() {
		return maxProgress;
	}

	public void setProgress(int progress, int max) {
		if (progress != this.progress || max != this.maxProgress) {
			this.progressDirty = true;
		}
		this.progress = progress;
		this.maxProgress = max;
	}

	/** Adds {@code amount} progress towards {@code threshold}; returns true once the threshold is reached. */
	public boolean increase(int amount, int threshold) {
		setProgress(progress + amount, threshold);
		return progress >= threshold;
	}

	public void resetProgress() {
		if (progress != 0) {
			progressDirty = true;
		}
		progress = 0;
	}

	public Set<UUID> petted() {
		return petted;
	}
}
