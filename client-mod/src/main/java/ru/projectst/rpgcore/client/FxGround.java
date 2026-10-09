package ru.projectst.rpgcore.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Высота земли под точкой границы.
 *
 * <p>Кольцо на высоте центра уходит в склон с одной стороны и висит в воздухе
 * с другой, и радиус по такому кольцу не прочитать. Поэтому каждая точка
 * границы прижимается к своей земле — тем же правилом, что ванильное кольцо на
 * сервере: верх твёрдого блока на три вниз и два вверх от центра, а дальше —
 * уже обрыв или крыша, и точка остаётся на высоте центра.
 *
 * <p>Ответы запоминаются на секунду: граница перерисовывается каждый кадр, а
 * земля под ней не меняется, и опрос блоков на каждую вершину каждого кадра
 * стоил бы больше самого рисунка.
 */
final class FxGround {

    /** Сколько тиков помнить ответы: земля меняется редко, а копать каждый кадр дорого. */
    private static final int FORGET_EVERY = 20;

    private static final Map<Long, Float> CACHE = new HashMap<>();
    private static final BlockPos.MutableBlockPos POS = new BlockPos.MutableBlockPos();
    private static int age;

    private FxGround() {
    }

    /** Раз в тик: старые ответы забываются. */
    static void tick() {
        if (++age >= FORGET_EVERY) {
            age = 0;
            CACHE.clear();
        }
    }

    static void clear() {
        CACHE.clear();
    }

    /**
     * Высота поверхности под точкой.
     *
     * @param refY высота центра области: от неё ищется земля
     */
    static float top(Level level, double x, double z, double refY) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int by = (int) Math.floor(refY);
        long key = ((long) (bx & 0x3FFFFFF) << 38) | ((long) (bz & 0x3FFFFFF) << 12)
                | (by & 0xFFF);
        Float known = CACHE.get(key);
        if (known != null) {
            return known;
        }
        float found = (float) refY + 0.05f;
        int top = by + 2;
        for (int y = top; y >= top - 5; y--) {
            VoxelShape shape = shapeAt(level, bx, y, bz);
            if (!shape.isEmpty() && shapeAt(level, bx, y + 1, bz).isEmpty()) {
                found = (float) (y + shape.max(Direction.Axis.Y));
                break;
            }
        }
        CACHE.put(key, found);
        return found;
    }

    private static VoxelShape shapeAt(Level level, int x, int y, int z) {
        POS.set(x, y, z);
        BlockState state = level.getBlockState(POS);
        return state.getCollisionShape(level, POS);
    }
}
