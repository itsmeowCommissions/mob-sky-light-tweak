package dev.itsmeow.modskylighttweak;

import io.github.fablabsmc.fablabs.api.fiber.v1.builder.ConfigTreeBuilder;
import io.github.fablabsmc.fablabs.api.fiber.v1.exception.ValueDeserializationException;
import io.github.fablabsmc.fablabs.api.fiber.v1.schema.type.derived.ConfigTypes;
import io.github.fablabsmc.fablabs.api.fiber.v1.schema.type.derived.NumberConfigType;
import io.github.fablabsmc.fablabs.api.fiber.v1.serialization.FiberSerialization;
import io.github.fablabsmc.fablabs.api.fiber.v1.serialization.JanksonValueSerializer;
import io.github.fablabsmc.fablabs.api.fiber.v1.tree.ConfigBranch;
import io.github.fablabsmc.fablabs.api.fiber.v1.tree.ConfigTree;
import io.github.fablabsmc.fablabs.api.fiber.v1.tree.PropertyMirror;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

public class ModSkyLightTweak implements ModInitializer {

	public static final String MODID = "mod-sky-light-tweak";
	public static SkyLightConfiguration CONFIG_WRAPPER = null;

	@Override
	public void onInitialize() {
		CONFIG_WRAPPER = new SkyLightConfiguration();
	}

	public static final class SkyLightConfiguration {

		public PropertyMirror<Boolean> use_sky_light;
		public PropertyMirror<Boolean> use_block_light_with_sky;
		public PropertyMirror<Integer> override_block_light_level;
		public PropertyMirror<Integer> use_sky_y_above;
		public PropertyMirror<Integer> override_sky_light_level;
		public final FabricCommonConfig config;

		public SkyLightConfiguration() {
			config = new FabricCommonConfig(builder -> {
				use_sky_light = PropertyMirror.create(ConfigTypes.BOOLEAN);
				use_block_light_with_sky = PropertyMirror.create(ConfigTypes.BOOLEAN);
				NumberConfigType<Integer> rangeType_sky_y = ConfigTypes.INTEGER.withMinimum(-64).withMaximum(320);
				use_sky_y_above = PropertyMirror.create(rangeType_sky_y);
				NumberConfigType<Integer> rangeType_sky = ConfigTypes.INTEGER.withMinimum(0).withMaximum(15);
				override_sky_light_level = PropertyMirror.create(rangeType_sky);
				NumberConfigType<Integer> rangeType = ConfigTypes.INTEGER.withMinimum(-1).withMaximum(15);
				override_block_light_level = PropertyMirror.create(rangeType);
				builder = builder.beginValue("use_sky_light", ConfigTypes.BOOLEAN, true).withComment("If sky light should be used above the configured y value, or use block light (default) always").finishValue(use_sky_light::mirror);
				builder = builder.beginValue("use_block_light_with_sky", ConfigTypes.BOOLEAN, true).withComment("If block light should also be used to block spawns above the configured y. This is important for allowing lit, yet cavernous (ex: house) areas to not spawn stuff.").finishValue(use_block_light_with_sky::mirror);
				builder = builder.beginValue("use_sky_y_above", rangeType_sky_y, 50).withComment("Any y coordinate greater than or equal to this number will use sky lighting (if enabled) instead of block lighting to determine spawn conditions. This is useful for allowing caves to be lit.").finishValue(use_sky_y_above::mirror);
				builder = builder.beginValue("override_sky_light_level", rangeType_sky, 12).withComment("If using sky light, what level to override the spawn maximum at. Monsters will only spawn below this value.").finishValue(override_sky_light_level::mirror);
				builder = builder.beginValue("override_block_light_level", rangeType, -1).withComment("If using block light (default lighting), what level to override the spawn maximum at. Monsters will only spawn below this value. -1 will use the dimension's default.").finishValue(override_block_light_level::mirror);
				return builder;
			});
		}
	}

	public static class FabricCommonConfig {

		public static final Set<FabricCommonConfig> INSTANCES = new HashSet<>();
		protected static final Logger LOGGER = LogManager.getLogger();
		protected final JanksonValueSerializer janksonSerializer = new JanksonValueSerializer(false);
		protected final Function<ConfigTreeBuilder, ConfigTreeBuilder> init;
		private ConfigBranch builtConfig;
		private boolean initialized = false;
		protected String name;

		private ConfigTreeBuilder builder = ConfigTree.builder();

		public FabricCommonConfig(Function<ConfigTreeBuilder, ConfigTreeBuilder> init) {
			this.name = ModSkyLightTweak.MODID + "-common";
			this.init = init;
			builder = builder.withName(name);
			ServerLifecycleEvents.SERVER_STARTING.register(server -> {
				this.createOrLoad();
			});
			INSTANCES.add(this);
		}

		public ConfigBranch getBranch() {
			if(builtConfig == null) {
				return this.init();
			}
			return builtConfig;
		}

		protected ConfigBranch init() {
			if(!initialized) {
				this.initialized = true;
				builder = init.apply(builder);
				this.builtConfig = builder.build();
			}
			return this.builtConfig;
		}

		public String getConfigName() {
			return name;
		}

		public File getConfigFile() {
			return new File(FabricLoader.getInstance().getConfigDir().toFile(), getConfigName() + ".json5");
		}

		public void createOrLoad() {
			setupConfigFile(this.getConfigFile(), this.init(), janksonSerializer);
		}

		public void saveBranch(File configFile, ConfigBranch branch) {
			try {
				FiberSerialization.serialize(branch, Files.newOutputStream(configFile.toPath()), janksonSerializer);
				LOGGER.info("Successfully wrote menu edits to config file '{}'", configFile.toString());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		private void setupConfigFile(File configFile, ConfigBranch configNode, JanksonValueSerializer serializer) {
			boolean recreate = false;
			while (true) {
				try {
					if (!configFile.exists() || recreate) {
						FiberSerialization.serialize(configNode, Files.newOutputStream(configFile.toPath()), serializer);
						LOGGER.info("Successfully created the config file in '{}'", configFile.toString());
						break;
					} else {
						try {
							FiberSerialization.deserialize(configNode, Files.newInputStream(configFile.toPath()), serializer);
							FiberSerialization.serialize(configNode, Files.newOutputStream(configFile.toPath()), serializer);
							LOGGER.info("Successfully loaded '{}'", configFile.toString());
							break;
						} catch (ValueDeserializationException e) {
							String fileName = (getConfigName() + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss")) + ".json5");
							LOGGER.error("Found a syntax error in the config!");
							if (configFile.renameTo(new File(configFile.getParent(), fileName))) {
								LOGGER.info("Config file successfully renamed to '{}'.", fileName);
							}
							recreate = true;
							e.printStackTrace();
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
					break;
				}
			}
		}
	}
}