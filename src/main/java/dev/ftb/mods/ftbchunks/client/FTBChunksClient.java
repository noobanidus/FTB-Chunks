package dev.ftb.mods.ftbchunks.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector3f;
import com.mojang.util.UUIDTypeAdapter;
import dev.ftb.mods.ftbchunks.ColorMapLoader;
import dev.ftb.mods.ftbchunks.FTBChunks;
import dev.ftb.mods.ftbchunks.FTBChunksCommon;
import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftbchunks.client.map.MapManager;
import dev.ftb.mods.ftbchunks.client.map.MapRegion;
import dev.ftb.mods.ftbchunks.client.map.MapRegionData;
import dev.ftb.mods.ftbchunks.client.map.MapTask;
import dev.ftb.mods.ftbchunks.client.map.PlayerHeadTexture;
import dev.ftb.mods.ftbchunks.client.map.RegionSyncKey;
import dev.ftb.mods.ftbchunks.client.map.ReloadChunkTask;
import dev.ftb.mods.ftbchunks.client.map.UpdateChunkFromServerTask;
import dev.ftb.mods.ftbchunks.client.map.Waypoint;
import dev.ftb.mods.ftbchunks.client.map.WaypointType;
import dev.ftb.mods.ftbchunks.client.map.color.ColorUtils;
import dev.ftb.mods.ftbchunks.data.PlayerLocation;
import dev.ftb.mods.ftbchunks.data.XZ;
import dev.ftb.mods.ftbchunks.net.LoginDataPacket;
import dev.ftb.mods.ftbchunks.net.PartialPackets;
import dev.ftb.mods.ftbchunks.net.PlayerDeathPacket;
import dev.ftb.mods.ftbchunks.net.SendAllChunksPacket;
import dev.ftb.mods.ftbchunks.net.SendChunkPacket;
import dev.ftb.mods.ftbchunks.net.SendGeneralDataPacket;
import dev.ftb.mods.ftbchunks.net.SendVisiblePlayerListPacket;
import dev.ftb.mods.ftbguilibrary.icon.ImageIcon;
import dev.ftb.mods.ftbguilibrary.utils.ClientUtils;
import dev.ftb.mods.ftbguilibrary.utils.MathUtils;
import dev.ftb.mods.ftbguilibrary.widget.CustomClickEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

/**
 * @author LatvianModder
 */
public class FTBChunksClient extends FTBChunksCommon {
	private static final ResourceLocation BUTTON_ID = new ResourceLocation("ftbchunks:open_gui");
	public static final ResourceLocation CIRCLE_MASK = new ResourceLocation("ftbchunks:textures/circle_mask.png");
	public static final ResourceLocation CIRCLE_BORDER = new ResourceLocation("ftbchunks:textures/circle_border.png");
	public static final ResourceLocation PLAYER = new ResourceLocation("ftbchunks:textures/player.png");
	public static final ResourceLocation[] COMPASS = {
			new ResourceLocation("ftbchunks:textures/compass_e.png"),
			new ResourceLocation("ftbchunks:textures/compass_n.png"),
			new ResourceLocation("ftbchunks:textures/compass_w.png"),
			new ResourceLocation("ftbchunks:textures/compass_s.png"),
	};

	private static final List<Component> MINIMAP_TEXT_LIST = new ArrayList<>(3);

	private static final ArrayDeque<MapTask> taskQueue = new ArrayDeque<>();
	public static long taskQueueTicks = 0L;
	public static final HashSet<ChunkPos> rerenderCache = new HashSet<>();

	public static void queue(MapTask task) {
		taskQueue.addLast(task);
	}

	public static KeyMapping openMapKey;

	public static int minimapTextureId = -1;
	private static int currentPlayerChunkX, currentPlayerChunkZ;

	public static boolean updateMinimap = false;
	public static boolean alwaysRenderChunksOnMap = false;
	public static SendGeneralDataPacket generalData;
	private static long nextRegionSave = 0L;

	public void init() {
		MinecraftForge.EVENT_BUS.register(this);
		FTBChunksClientConfig.init();
		openMapKey = new KeyMapping("key.ftbchunks.map", KeyConflictContext.IN_GAME, KeyModifier.NONE, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, "key.categories.ui");
		ClientRegistry.registerKeyBinding(openMapKey);
		((ReloadableResourceManager) Minecraft.getInstance().getResourceManager()).registerReloadListener(new EntityIcons());
		((ReloadableResourceManager) Minecraft.getInstance().getResourceManager()).registerReloadListener(new ColorMapLoader());
	}

	public static void openGui() {
		new LargeMapScreen().openGui();
	}

	public static void saveAllRegions() {
		if (MapManager.inst == null) {
			return;
		}

		for (MapDimension dimension : MapManager.inst.getDimensions().values()) {
			for (MapRegion region : dimension.getLoadedRegions()) {
				if (region.saveData) {
					queue(region);
					region.saveData = false;
				}
			}

			if (dimension.saveData) {
				queue(dimension);
				dimension.saveData = false;
			}
		}

		if (MapManager.inst.saveData) {
			queue(MapManager.inst);
			MapManager.inst.saveData = false;
		}
	}

	@Override
	public void login(LoginDataPacket loginData) {
		Path dir = FMLPaths.GAMEDIR.get().resolve("local/ftbchunks/data/" + loginData.serverId);

		if (Files.notExists(dir)) {
			try {
				Files.createDirectories(dir);
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}

		MapManager.inst = new MapManager(loginData.serverId, dir);
		updateMinimap = true;
	}

	@SubscribeEvent
	public void loggedOut(ClientPlayerNetworkEvent.LoggedOutEvent event) {
		if (MapManager.inst != null) {
			saveAllRegions();

			MapTask t;

			while ((t = taskQueue.pollFirst()) != null) {
				t.runMapTask();
			}

			MapDimension.updateCurrent();
			MapManager.inst.release();
			MapManager.inst = null;
		}
	}

	@Override
	public void updateGeneralData(SendGeneralDataPacket packet) {
		generalData = packet;
	}

	@Override
	public void updateChunk(SendChunkPacket packet) {
		MapDimension dimension = MapManager.inst.getDimension(packet.dimension);
		Date now = new Date();
		queue(new UpdateChunkFromServerTask(dimension, packet.chunk, now));
	}

	@Override
	public void updateAllChunks(SendAllChunksPacket packet) {
		MapDimension dimension = MapManager.inst.getDimension(packet.dimension);
		Date now = new Date();

		for (SendChunkPacket.SingleChunk c : packet.chunks) {
			queue(new UpdateChunkFromServerTask(dimension, c, now));
		}
	}

	@Override
	public void updateVisiblePlayerList(SendVisiblePlayerListPacket packet) {
		PlayerLocation.CLIENT_LIST.clear();
		PlayerLocation.currentDimension = packet.dim;
		PlayerLocation.CLIENT_LIST.addAll(packet.players);
	}

	@Override
	public void syncRegion(RegionSyncKey key, int offset, int total, byte[] data) {
		PartialPackets.REGION.read(key, offset, total, data);
	}

	@Override
	public void playerDeath(PlayerDeathPacket packet) {
		if (FTBChunksClientConfig.deathWaypoints) {
			MapDimension dimension = MapManager.inst.getDimension(packet.dimension);
			Waypoint w = new Waypoint(dimension);
			w.name = "Death #" + packet.number;
			w.x = packet.x;
			w.y = packet.y;
			w.z = packet.z;
			w.type = WaypointType.DEATH;
			dimension.getWaypoints().add(w);
			dimension.saveData = true;
		}
	}

	@Override
	public int blockColor() {
		Minecraft mc = Minecraft.getInstance();
		mc.submit(() -> {
			if (mc.hitResult instanceof BlockHitResult && mc.options.hideGui) {
				ResourceLocation id = Registry.BLOCK.getKey(mc.level.getBlockState(((BlockHitResult) mc.hitResult).getBlockPos()).getBlock());
				NativeImage image = Screenshot.takeScreenshot(mc.getWindow().getWidth(), mc.getWindow().getHeight(), mc.getMainRenderTarget());
				int col = image.getPixelRGBA(image.getWidth() / 2, image.getHeight() / 2);
				String s = String.format("\"%s\": \"#%06X\"", id.getPath(), ColorUtils.convertFromNative(col) & 0xFFFFFF);
				mc.player.sendMessage(new TextComponent(id.getNamespace() + " - " + s).withStyle(Style.EMPTY.applyFormat(ChatFormatting.GOLD).withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, s)).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponent("Click to copy")))), Util.NIL_UUID);
			} else {
				mc.player.sendMessage(new TextComponent("You must be looking at a block in F1 mode!"), Util.NIL_UUID);
			}
		});

		return 1;
	}

	@SubscribeEvent
	public void customClick(CustomClickEvent event) {
		if (event.getId().equals(BUTTON_ID)) {
			openGui();
		}
	}

	@SubscribeEvent
	public void keyEvent(InputEvent.KeyInputEvent event) {
		if (openMapKey.isDown()) {
			if (FTBChunksClientConfig.debugInfo && Screen.hasControlDown()) {
				FTBChunks.LOGGER.info("=== Task Queue: " + taskQueue.size());

				for (MapTask task : taskQueue) {
					FTBChunks.LOGGER.info(task.toString());
				}

				FTBChunks.LOGGER.info("===");
			} else {
				openGui();
			}
		}
	}

	@SubscribeEvent
	public void guiKeyEvent(GuiScreenEvent.KeyboardKeyEvent.KeyboardKeyPressedEvent.Pre event) {
		if (openMapKey.isDown()) {
			LargeMapScreen gui = ClientUtils.getCurrentGuiAs(LargeMapScreen.class);

			if (gui != null) {
				gui.closeGui(false);
			}
		}
	}

	@SubscribeEvent
	public void renderGameOverlay(RenderGameOverlayEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.player == null || mc.level == null || MapManager.inst == null || event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
			return;
		}

		MapDimension dim = MapDimension.getCurrent();

		if (dim.dimension != mc.level.dimension()) {
			MapDimension.updateCurrent();
			dim = MapDimension.getCurrent();
		}

		long now = System.currentTimeMillis();

		if (nextRegionSave == 0L || now >= nextRegionSave) {
			nextRegionSave = now + 60000L;
			saveAllRegions();
		}

		if (minimapTextureId == -1) {
			minimapTextureId = TextureUtil.generateTextureId();
			TextureUtil.prepareImage(minimapTextureId, FTBChunks.MINIMAP_SIZE, FTBChunks.MINIMAP_SIZE);
			updateMinimap = true;
		}

		RenderSystem.enableTexture();
		RenderSystem.bindTexture(minimapTextureId);

		if (FTBChunksClientConfig.minimapBlur) {
			RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		} else {
			RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
			RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		}

		int cx = mc.player.xChunk;
		int cz = mc.player.zChunk;

		if (cx != currentPlayerChunkX || cz != currentPlayerChunkZ) {
			updateMinimap = true;
		}

		if (updateMinimap) {
			updateMinimap = false;

			// TODO: More math here to upload from (up to) 4 regions instead of all chunks inside them, to speed things up

			for (int mz = 0; mz < FTBChunks.TILES; mz++) {
				for (int mx = 0; mx < FTBChunks.TILES; mx++) {
					int ox = cx + mx - FTBChunks.TILE_OFFSET;
					int oz = cz + mz - FTBChunks.TILE_OFFSET;

					MapRegion region = dim.getRegion(XZ.regionFromChunk(ox, oz));
					region.getRenderedMapImage().upload(0, mx * 16, mz * 16, (ox & 31) * 16, (oz & 31) * 16, 16, 16, FTBChunksClientConfig.minimapBlur, false, false, false);
				}
			}

			currentPlayerChunkX = cx;
			currentPlayerChunkZ = cz;
		}

		if (mc.options.renderDebug || FTBChunksClientConfig.minimap == MinimapPosition.DISABLED || FTBChunksClientConfig.minimapVisibility == 0) {
			return;
		}

		float scale = (float) (FTBChunksClientConfig.minimapScale * 4D / mc.getWindow().getGuiScale());
		float minimapRotation = (FTBChunksClientConfig.minimapLockedNorth ? 180F : -mc.player.yRot) % 360F;

		int s = (int) (64D * scale);
		int x = FTBChunksClientConfig.minimap.getX(mc.getWindow().getGuiScaledWidth(), s);
		int y = FTBChunksClientConfig.minimap.getY(mc.getWindow().getGuiScaledHeight(), s);
		int z = 0;

		float border = 0F;
		int alpha = FTBChunksClientConfig.minimapVisibility;

		Tesselator tessellator = Tesselator.getInstance();
		BufferBuilder buffer = tessellator.getBuilder();

		float f0 = 1F / (float) FTBChunks.TILES;
		float f1 = 1F - f0;

		float offX = (float) ((mc.player.getX() / 16D - currentPlayerChunkX - 0.5D) / (double) FTBChunks.TILES);
		float offY = (float) ((mc.player.getZ() / 16D - currentPlayerChunkZ - 0.5D) / (double) FTBChunks.TILES);

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableCull();
		RenderSystem.enableTexture();
		RenderSystem.enableDepthTest();
		RenderSystem.enableAlphaTest();

		PoseStack matrixStack = event.getMatrixStack();
		matrixStack.pushPose();
		matrixStack.translate(x + s / 2D, y + s / 2D, -10);

		matrixStack.translate(0, 0, 950);

		Matrix4f m = matrixStack.last().pose();

		// See AdvancementTabGui
		RenderSystem.colorMask(false, false, false, false);
		mc.getTextureManager().bind(CIRCLE_MASK);
		buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
		buffer.vertex(m, -s / 2F + border, -s / 2F + border, z).color(255, 255, 255, 255).uv(0F, 0F).endVertex();
		buffer.vertex(m, -s / 2F + border, s / 2F - border, z).color(255, 255, 255, 255).uv(0F, 1F).endVertex();
		buffer.vertex(m, s / 2F - border, s / 2F - border, z).color(255, 255, 255, 255).uv(1F, 1F).endVertex();
		buffer.vertex(m, s / 2F - border, -s / 2F + border, z).color(255, 255, 255, 255).uv(1F, 0F).endVertex();
		tessellator.end();
		RenderSystem.colorMask(true, true, true, true);

		matrixStack.translate(0, 0, -950);
		matrixStack.mulPose(Vector3f.ZP.rotationDegrees(minimapRotation + 180F));

		RenderSystem.depthFunc(GL11.GL_GEQUAL);
		RenderSystem.bindTexture(minimapTextureId);

		m = matrixStack.last().pose();

		buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
		buffer.vertex(m, -s / 2F + border, -s / 2F + border, z).color(255, 255, 255, alpha).uv(f0 + offX, f0 + offY).endVertex();
		buffer.vertex(m, -s / 2F + border, s / 2F - border, z).color(255, 255, 255, alpha).uv(f0 + offX, f1 + offY).endVertex();
		buffer.vertex(m, s / 2F - border, s / 2F - border, z).color(255, 255, 255, alpha).uv(f1 + offX, f1 + offY).endVertex();
		buffer.vertex(m, s / 2F - border, -s / 2F + border, z).color(255, 255, 255, alpha).uv(f1 + offX, f0 + offY).endVertex();
		tessellator.end();

		matrixStack.popPose();

		RenderSystem.disableDepthTest();
		RenderSystem.depthFunc(GL11.GL_LEQUAL);
		RenderSystem.defaultBlendFunc();

		m = matrixStack.last().pose();

		mc.getTextureManager().bind(CIRCLE_BORDER);
		buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
		buffer.vertex(m, x, y, z).color(255, 255, 255, alpha).uv(0F, 0F).endVertex();
		buffer.vertex(m, x, y + s, z).color(255, 255, 255, alpha).uv(0F, 1F).endVertex();
		buffer.vertex(m, x + s, y + s, z).color(255, 255, 255, alpha).uv(1F, 1F).endVertex();
		buffer.vertex(m, x + s, y, z).color(255, 255, 255, alpha).uv(1F, 0F).endVertex();
		tessellator.end();

		RenderSystem.disableTexture();
		buffer.begin(GL11.GL_LINES, DefaultVertexFormat.POSITION_COLOR);
		buffer.vertex(m, x + s / 2F, y + 0, z).color(0, 0, 0, 30).endVertex();
		buffer.vertex(m, x + s / 2F, y + s, z).color(0, 0, 0, 30).endVertex();
		buffer.vertex(m, x + 0, y + s / 2F, z).color(0, 0, 0, 30).endVertex();
		buffer.vertex(m, x + s, y + s / 2F, z).color(0, 0, 0, 30).endVertex();
		tessellator.end();

		RenderSystem.enableTexture();

		if (FTBChunksClientConfig.minimapCompass) {
			for (int face = 0; face < 4; face++) {
				double d = s / 2.2D;

				double angle = (minimapRotation + 180D - face * 90D) * Math.PI / 180D;

				float wx = (float) (x + s / 2D + Math.cos(angle) * d);
				float wy = (float) (y + s / 2D + Math.sin(angle) * d);
				float ws = s / 32F;

				m = matrixStack.last().pose();

				mc.textureManager.bind(COMPASS[face]);
				buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
				buffer.vertex(m, wx - ws, wy - ws, z).color(255, 255, 255, 255).uv(0F, 0F).endVertex();
				buffer.vertex(m, wx - ws, wy + ws, z).color(255, 255, 255, 255).uv(0F, 1F).endVertex();
				buffer.vertex(m, wx + ws, wy + ws, z).color(255, 255, 255, 255).uv(1F, 1F).endVertex();
				buffer.vertex(m, wx + ws, wy - ws, z).color(255, 255, 255, 255).uv(1F, 0F).endVertex();
				tessellator.end();
			}
		}

		double magicNumber = 3.2D;

		if (FTBChunksClientConfig.minimapWaypoints && !dim.getWaypoints().isEmpty()) {
			for (Waypoint waypoint : dim.getWaypoints()) {
				if (waypoint.hidden) {
					continue;
				}

				double distance = MathUtils.dist(mc.player.getX(), mc.player.getZ(), waypoint.x + 0.5D, waypoint.z + 0.5D);

				if (distance > waypoint.minimapDistance) {
					continue;
				}

				double d = distance / magicNumber * scale;

				if (d > s / 2D) {
					d = s / 2D;
				}

				double angle = Math.atan2(mc.player.getZ() - waypoint.z - 0.5D, mc.player.getX() - waypoint.x - 0.5D) + minimapRotation * Math.PI / 180D;

				float wx = (float) (x + s / 2D + Math.cos(angle) * d);
				float wy = (float) (y + s / 2D + Math.sin(angle) * d);
				float ws = s / 32F;

				int r = (waypoint.color >> 16) & 0xFF;
				int g = (waypoint.color >> 8) & 0xFF;
				int b = (waypoint.color >> 0) & 0xFF;

				m = matrixStack.last().pose();

				mc.getTextureManager().bind(waypoint.type.texture);
				buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
				buffer.vertex(m, wx - ws, wy - ws, z).color(r, g, b, 255).uv(0F, 0F).endVertex();
				buffer.vertex(m, wx - ws, wy + ws, z).color(r, g, b, 255).uv(0F, 1F).endVertex();
				buffer.vertex(m, wx + ws, wy + ws, z).color(r, g, b, 255).uv(1F, 1F).endVertex();
				buffer.vertex(m, wx + ws, wy - ws, z).color(r, g, b, 255).uv(1F, 0F).endVertex();
				tessellator.end();
			}
		}

		if (FTBChunksClientConfig.minimapEntities) {
			for (Entity entity : mc.level.entitiesForRendering()) {
				if (entity instanceof AbstractClientPlayer || entity.getType().getCategory() == MobCategory.MISC || entity.getY() < entity.level.getHeight(Heightmap.Types.WORLD_SURFACE, Mth.floor(entity.getX()), Mth.floor(entity.getZ())) - 10) {
					continue;
				}

				double d = MathUtils.dist(mc.player.getX(), mc.player.getZ(), entity.getX(), entity.getZ()) / magicNumber * scale;

				if (d > s / 2D) {
					continue;
				}

				ResourceLocation texture = EntityIcons.ENTITY_ICONS.get(entity.getType());

				if (texture == EntityIcons.INVISIBLE) {
					continue;
				} else if (texture == null || !FTBChunksClientConfig.minimapEntityHeads) {
					if (entity instanceof Enemy) {
						texture = EntityIcons.HOSTILE;
					} else {
						texture = EntityIcons.NORMAL;
					}
				}

				double angle = Math.atan2(mc.player.getZ() - entity.getZ(), mc.player.getX() - entity.getX()) + minimapRotation * Math.PI / 180D;

				float wx = (float) (x + s / 2D + Math.cos(angle) * d);
				float wy = (float) (y + s / 2D + Math.sin(angle) * d);
				float ws = s / (FTBChunksClientConfig.minimapLargeEntities ? 32F : 48F);

				m = matrixStack.last().pose();

				mc.getTextureManager().bind(texture);
				RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
				RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

				buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
				buffer.vertex(m, wx - ws, wy - ws, z).color(255, 255, 255, 255).uv(0F, 0F).endVertex();
				buffer.vertex(m, wx - ws, wy + ws, z).color(255, 255, 255, 255).uv(0F, 1F).endVertex();
				buffer.vertex(m, wx + ws, wy + ws, z).color(255, 255, 255, 255).uv(1F, 1F).endVertex();
				buffer.vertex(m, wx + ws, wy - ws, z).color(255, 255, 255, 255).uv(1F, 0F).endVertex();
				tessellator.end();
			}
		}

		if (FTBChunksClientConfig.minimapPlayerHeads && mc.level.players().size() > 1) {
			for (AbstractClientPlayer player : mc.level.players()) {
				if (player == mc.player) {
					continue;
				}

				double d = MathUtils.dist(mc.player.getX(), mc.player.getZ(), player.getX(), player.getZ()) / magicNumber * scale;

				if (d > s / 2D) {
					d = s / 2D;
				}

				double angle = Math.atan2(mc.player.getZ() - player.getZ(), mc.player.getX() - player.getX()) + minimapRotation * Math.PI / 180D;

				float wx = (float) (x + s / 2D + Math.cos(angle) * d);
				float wy = (float) (y + s / 2D + Math.sin(angle) * d);
				float ws = s / 32F;

				String uuid = UUIDTypeAdapter.fromUUID(player.getUUID());
				ResourceLocation texture = new ResourceLocation("head", uuid);

				TextureManager texturemanager = mc.getTextureManager();
				AbstractTexture t = texturemanager.getTexture(texture);

				if (t == null) {
					t = new PlayerHeadTexture("https://minotar.net/avatar/" + uuid + "/16", ImageIcon.MISSING_IMAGE);
					texturemanager.register(texture, t);
				}

				m = matrixStack.last().pose();

				RenderSystem.bindTexture(t.getId());
				buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
				buffer.vertex(m, wx - ws, wy - ws, z).color(255, 255, 255, 255).uv(0F, 0F).endVertex();
				buffer.vertex(m, wx - ws, wy + ws, z).color(255, 255, 255, 255).uv(0F, 1F).endVertex();
				buffer.vertex(m, wx + ws, wy + ws, z).color(255, 255, 255, 255).uv(1F, 1F).endVertex();
				buffer.vertex(m, wx + ws, wy - ws, z).color(255, 255, 255, 255).uv(1F, 0F).endVertex();
				tessellator.end();
			}
		}

		if (FTBChunksClientConfig.minimapLockedNorth) {
			mc.getTextureManager().bind(PLAYER);
			matrixStack.pushPose();
			matrixStack.translate(x + s / 2D, y + s / 2D, z);
			matrixStack.mulPose(Vector3f.ZP.rotationDegrees(mc.player.yRot + 180F));
			matrixStack.scale(s / 16F, s / 16F, 1F);
			m = matrixStack.last().pose();

			buffer.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
			buffer.vertex(m, -1, -1, 0).color(255, 255, 255, 200).uv(0F, 0F).endVertex();
			buffer.vertex(m, -1, 1, 0).color(255, 255, 255, 200).uv(0F, 1F).endVertex();
			buffer.vertex(m, 1, 1, 0).color(255, 255, 255, 200).uv(1F, 1F).endVertex();
			buffer.vertex(m, 1, -1, 0).color(255, 255, 255, 200).uv(1F, 0F).endVertex();
			tessellator.end();

			matrixStack.popPose();
		}

		MINIMAP_TEXT_LIST.clear();

		if (FTBChunksClientConfig.minimapZone) {
			MapRegionData data = dim.getRegion(XZ.regionFromChunk(currentPlayerChunkX, currentPlayerChunkZ)).getData();

			if (data != null) {
				Component currentChunkOwner = data.getChunk(XZ.of(currentPlayerChunkX, currentPlayerChunkZ)).owner;

				if (currentChunkOwner != TextComponent.EMPTY) {
					MINIMAP_TEXT_LIST.add(currentChunkOwner);
				}
			}
		}

		if (FTBChunksClientConfig.minimapXYZ) {
			MINIMAP_TEXT_LIST.add(new TextComponent(Mth.floor(mc.player.getX()) + " " + Mth.floor(mc.player.getY()) + " " + Mth.floor(mc.player.getZ())));
		}

		if (FTBChunksClientConfig.minimapBiome) {
			ResourceKey<Biome> biome = mc.level.getBiomeName(mc.player.blockPosition()).orElse(null);

			if (biome != null) {
				MINIMAP_TEXT_LIST.add(new TranslatableComponent("biome." + biome.location().getNamespace() + "." + biome.location().getPath()));
			}
		}

		if (FTBChunksClientConfig.debugInfo) {
			XZ r = XZ.regionFromChunk(currentPlayerChunkX, currentPlayerChunkZ);
			MINIMAP_TEXT_LIST.add(new TextComponent("Queued tasks: " + taskQueue.size()));
			MINIMAP_TEXT_LIST.add(new TextComponent(r.toRegionString()));
		}

		if (!MINIMAP_TEXT_LIST.isEmpty()) {
			matrixStack.pushPose();
			matrixStack.translate(x + s / 2D, y + s + 3D, 0D);
			matrixStack.scale((float) (0.5D * scale), (float) (0.5D * scale), 1F);

			for (int i = 0; i < MINIMAP_TEXT_LIST.size(); i++) {
				FormattedCharSequence bs = MINIMAP_TEXT_LIST.get(i).getVisualOrderText();
				int bsw = mc.font.width(bs);
				mc.font.drawShadow(matrixStack, bs, -bsw / 2F, i * 11, 0xFFFFFFFF);
			}

			matrixStack.popPose();
		}

		RenderSystem.enableDepthTest();
	}

	@SubscribeEvent
	public void renderWorldLast(RenderWorldLastEvent event) {
		Minecraft mc = Minecraft.getInstance();

		if (mc.options.hideGui || !FTBChunksClientConfig.inWorldWaypoints || MapManager.inst == null || mc.level == null || mc.player == null) {
			return;
		}

		MapDimension dim = MapDimension.getCurrent();

		if (dim.getWaypoints().isEmpty()) {
			return;
		}

		List<Waypoint> visibleWaypoints = new ArrayList<>();

		for (Waypoint waypoint : dim.getWaypoints()) {
			if (waypoint.hidden) {
				continue;
			}

			waypoint.distance = MathUtils.dist(mc.player.getX(), mc.player.getZ(), waypoint.x + 0.5D, waypoint.z + 0.5D);

			if (waypoint.distance <= 8D || waypoint.distance > waypoint.inWorldDistance) {
				continue;
			}

			waypoint.alpha = 150;

			if (waypoint.distance < 12D) {
				waypoint.alpha = (int) (waypoint.alpha * ((waypoint.distance - 8D) / 4D));
			}

			if (waypoint.alpha <= 0) {
				continue;
			}

			visibleWaypoints.add(waypoint);
		}

		if (visibleWaypoints.isEmpty()) {
			return;
		}

		if (visibleWaypoints.size() >= 2) {
			visibleWaypoints.sort(Comparator.comparingDouble(value -> -value.distance));
		}

		Camera activeRenderInfo = mc.getEntityRenderDispatcher().camera;
		Vec3 projectedView = activeRenderInfo.getPosition();
		PoseStack ms = event.getMatrixStack();
		ms.pushPose();
		ms.translate(-projectedView.x, -projectedView.y, -projectedView.z);

		VertexConsumer depthBuffer = mc.renderBuffers().bufferSource().getBuffer(FTBChunksRenderTypes.WAYPOINTS_DEPTH);

		float h = (float) (projectedView.y + 30D);
		float h2 = h + 70F;

		for (Waypoint waypoint : visibleWaypoints) {
			double angle = Math.atan2(projectedView.z - waypoint.z - 0.5D, projectedView.x - waypoint.x - 0.5D) * 180D / Math.PI;

			int r = (waypoint.color >> 16) & 0xFF;
			int g = (waypoint.color >> 8) & 0xFF;
			int b = (waypoint.color >> 0) & 0xFF;

			ms.pushPose();
			ms.translate(waypoint.x + 0.5D, 0, waypoint.z + 0.5D);
			ms.mulPose(Vector3f.YP.rotationDegrees((float) (-angle - 135D)));

			float s = 0.6F;

			Matrix4f m = ms.last().pose();

			depthBuffer.vertex(m, -s, 0, s).color(r, g, b, waypoint.alpha).uv(0F, 1F).endVertex();
			depthBuffer.vertex(m, -s, h, s).color(r, g, b, waypoint.alpha).uv(0F, 0F).endVertex();
			depthBuffer.vertex(m, s, h, -s).color(r, g, b, waypoint.alpha).uv(1F, 0F).endVertex();
			depthBuffer.vertex(m, s, 0, -s).color(r, g, b, waypoint.alpha).uv(1F, 1F).endVertex();

			depthBuffer.vertex(m, -s, h, s).color(r, g, b, waypoint.alpha).uv(0F, 1F).endVertex();
			depthBuffer.vertex(m, -s, h2, s).color(r, g, b, 0).uv(0F, 0F).endVertex();
			depthBuffer.vertex(m, s, h2, -s).color(r, g, b, 0).uv(1F, 0F).endVertex();
			depthBuffer.vertex(m, s, h, -s).color(r, g, b, waypoint.alpha).uv(1F, 1F).endVertex();

			ms.popPose();
		}

		ms.popPose();

		mc.renderBuffers().bufferSource().endBatch(FTBChunksRenderTypes.WAYPOINTS_DEPTH);
	}

	@SubscribeEvent
	public void screenOpened(GuiScreenEvent.InitGuiEvent.Pre event) {
		if (event.getGui() instanceof PauseScreen) {
			nextRegionSave = System.currentTimeMillis() + 60000L;
			saveAllRegions();
		}
	}

	@SubscribeEvent
	public void clientTick(TickEvent.ClientTickEvent event) {
		if (event.phase == TickEvent.Phase.START && MapManager.inst != null && Minecraft.getInstance().level != null) {
			if (taskQueueTicks % FTBChunksClientConfig.rerenderQueueTicks == 0L) {
				if (!rerenderCache.isEmpty()) {
					Level world = Minecraft.getInstance().level;

					for (ChunkPos pos : rerenderCache) {
						queue(new ReloadChunkTask(world, pos));
					}

					rerenderCache.clear();
				}
			}

			if (taskQueueTicks % FTBChunksClientConfig.taskQueueTicks == 0L) {
				int s = Math.min(taskQueue.size(), FTBChunksClientConfig.taskQueueMax);

				if (s > 0) {
					MapTask[] tasks = new MapTask[s];

					for (int i = 0; i < s; i++) {
						tasks[i] = taskQueue.pollFirst();

						if (tasks[i] == null || tasks[i].cancelOtherTasks()) {
							break;
						}
					}

					Util.backgroundExecutor().execute(() -> {
						for (MapTask task : tasks) {
							if (task != null) {
								task.runMapTask();
							}
						}
					});
				}
			}

			taskQueueTicks++;
		}
	}
}