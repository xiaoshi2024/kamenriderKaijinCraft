package com.xiaoshi2022.kamenrider_kaijin_craft.item;

import com.xiaoshi2022.kamenrider_kaijin_craft.client.render.StoriousBookModel;
import com.xiaoshi2022.kamenrider_kaijin_craft.inventory.FusionSlotContainer;
import com.xiaoshi2022.kamenrider_kaijin_craft.race.BookKaijinRace;
import com.xiaoshi2022.kamenrider_kaijin_craft.registry.ModAttachments;
import com.xiaoshi2022.kamenrider_kaijin_craft.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 斯特利乌斯·类驾驭书（Alter Ride Book）。
 * 翻开书页即变异成斯特利乌斯怪人；此后按 G 键可以拟态回人类，再按一次变回怪物。
 * <p>
 * 每本书在第一次翻开时唯一绑定一位玩家：别人拿到也用不了，同一位玩家也不能同时绑定第二本，
 * 避免靠复制道具无限制造变异来源。右键会把书收进背包里的「怪人融合槽」并完成融合。
 */
public class AlterRideBookItem extends Item implements GeoItem, IKaijinMutationItem {
    private static final RawAnimation BOOK_IDLE = RawAnimation.begin().thenLoop("origin");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public AlterRideBookItem(Properties properties) {
        super(properties);
        GeoItem.registerSyncedAnimatable(this);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        UUID owner = stack.get(ModDataComponents.BOOK_OWNER.get());

        // 已认主的书只认它唯一的主人
        if (owner != null && !owner.equals(player.getUUID())) {
            if (!level.isClientSide()) {
                String ownerName = stack.getOrDefault(ModDataComponents.BOOK_OWNER_NAME.get(), "");
                player.displayClientMessage(Component.translatable(
                        "message.kamenrider_kaijin_craft.book_bound_other", ownerName), true);
            }
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            ItemStack fused = serverPlayer.getData(ModAttachments.FUSION_ITEM);
            if (!fused.isEmpty()) {
                boolean sameBook = fused.is(this)
                        && serverPlayer.getUUID().equals(fused.get(ModDataComponents.BOOK_OWNER.get()));
                serverPlayer.displayClientMessage(Component.translatable(sameBook
                        ? "message.kamenrider_kaijin_craft.book_already_bound"
                        : "message.kamenrider_kaijin_craft.fusion_occupied"), true);
                return InteractionResultHolder.fail(stack);
            }
            if (hasOtherBoundBook(serverPlayer, stack)) {
                serverPlayer.displayClientMessage(Component.translatable(
                        "message.kamenrider_kaijin_craft.book_already_bound"), true);
                return InteractionResultHolder.fail(stack);
            }

            // 认主 → 收进融合槽；FusionSlotContainer.setItem 会触发 onFused
            stack.set(ModDataComponents.BOOK_OWNER.get(), serverPlayer.getUUID());
            stack.set(ModDataComponents.BOOK_OWNER_NAME.get(), serverPlayer.getGameProfile().getName());
            ItemStack toFuse = stack.copyWithCount(1);
            serverPlayer.setItemInHand(hand, ItemStack.EMPTY);
            new FusionSlotContainer(serverPlayer).setItem(0, toFuse);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public boolean canFuse(Player player, ItemStack stack) {
        UUID owner = stack.get(ModDataComponents.BOOK_OWNER.get());
        return owner == null || owner.equals(player.getUUID());
    }

    @Override
    public boolean sustainsRace(Player player, ItemStack stack) {
        // 只有「认主给这位玩家」的那本书才算心脏；随手复制的未认主副本不算
        return player.getUUID().equals(stack.get(ModDataComponents.BOOK_OWNER.get()));
    }

    @Override
    public void onFused(ServerPlayer player, ItemStack stack) {
        if (stack.get(ModDataComponents.BOOK_OWNER.get()) == null) {
            stack.set(ModDataComponents.BOOK_OWNER.get(), player.getUUID());
            stack.set(ModDataComponents.BOOK_OWNER_NAME.get(), player.getGameProfile().getName());
        }
        BookKaijinRace.openBook(player);
    }

    private static boolean hasOtherBoundBook(Player player, ItemStack current) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack other = inventory.getItem(slot);
            if (other == current || other.isEmpty() || !other.is(current.getItem())) {
                continue;
            }
            if (player.getUUID().equals(other.get(ModDataComponents.BOOK_OWNER.get()))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.kamenrider_kaijin_craft.storious_book.tooltip"));
        UUID owner = stack.get(ModDataComponents.BOOK_OWNER.get());
        if (owner == null) {
            tooltip.add(Component.translatable("item.kamenrider_kaijin_craft.storious_book.unbound")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            String ownerName = stack.getOrDefault(ModDataComponents.BOOK_OWNER_NAME.get(), "");
            tooltip.add(Component.translatable("item.kamenrider_kaijin_craft.storious_book.bound", ownerName)
                    .withStyle(ChatFormatting.DARK_RED));
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "book", 0, state -> state.setAndContinue(BOOK_IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GeoItemRenderer<AlterRideBookItem> renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                if (renderer == null) {
                    renderer = new GeoItemRenderer<>(new StoriousBookModel());
                }
                return renderer;
            }
        });
    }
}
