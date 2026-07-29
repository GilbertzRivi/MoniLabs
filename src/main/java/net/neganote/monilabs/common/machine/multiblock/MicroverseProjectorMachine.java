package net.neganote.monilabs.common.machine.multiblock;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.annotation.RequireRerender;
import com.lowdragmc.lowdraglib.syncdata.annotation.UpdateListener;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.neganote.monilabs.common.machine.trait.NotifiableMicroverseContainer;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@SuppressWarnings("unused")
public class MicroverseProjectorMachine extends WorkableElectricMultiblockMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            MicroverseProjectorMachine.class, WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    @Getter
    @Persisted
    private final Set<BlockPos> fluidBlockOffsets = new HashSet<>();

    // Used for microverse projector tier
    @Getter
    private final int projectorTier;

    // Microverse type currently active
    @Persisted
    @DescSynced
    @Setter
    @Getter
    @RequireRerender
    @UpdateListener(methodName = "onMicroverseChange")
    Microverse microverse;

    // Current microverse integrity/"health"
    @Persisted
    @DescSynced
    @Getter
    int microverseIntegrity;

    // Constant for max health. Takes 500s (8m20s) to decay at a rate of 1/tick
    public static final int MICROVERSE_MAX_INTEGRITY = 100_000;
    public static final int FLUX_REPAIR_AMOUNT = 1000;

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new MicroverseProjectorRecipeLogic(this);
    }

    @Persisted
    private final NotifiableMicroverseContainer microverseContainer;

    public MicroverseProjectorMachine(IMachineBlockEntity holder, int tier, Object... args) {
        super(holder, args);
        this.projectorTier = tier;
        updateMicroverse(0, false);
        this.microverseContainer = new NotifiableMicroverseContainer(this);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public void onStructureInvalid() {
        if (microverse.decayRate != 0) {
            updateMicroverse(0, false);
        }
        super.onStructureInvalid();
    }

    @Override
    public void onRotated(Direction oldFacing, Direction newFacing) {
        super.onRotated(oldFacing, newFacing);
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
    }

    @Override
    public boolean beforeWorking(@Nullable GTRecipe recipe) {
        if (recipe == null) return false;
        if (microverseIntegrity == 0 && microverse != Microverse.NONE) return false;
        if (recipe.data.contains("projector_tier") && recipe.data.getLong("projector_tier") > projectorTier) {
            RecipeLogic.putFailureReason(this, recipe,
                    Component.translatable("monilabs.failure_reason.insufficient_projector_tier"));
            return false;
        }
        return super.beforeWorking(recipe);
    }

    @Override
    public void afterWorking() {
        super.afterWorking();
        var activeRecipe = recipeLogic.getLastRecipe();
        if (activeRecipe != null && activeRecipe.data.contains("updated_microverse")) {
            int updatedMicroverse = activeRecipe.data.getInt("updated_microverse");
            updateMicroverse(updatedMicroverse, activeRecipe.data.getBoolean("keep_integrity"));
        }
    }

    void updateMicroverse(int pKey, boolean keepIntegrity) {
        microverse = Microverse.getMicroverseFromKey(pKey);
        if (microverse == Microverse.NONE) {
            microverseIntegrity = 0;
        } else {
            microverseIntegrity = (keepIntegrity ? microverseIntegrity : MICROVERSE_MAX_INTEGRITY);
        }
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        super.addDisplayText(textList);
        if (isFormed()) {
            textList.add(Component.translatable("microverse.monilabs.current_microverse",
                    Component.translatable(microverse.langKey)));
            if (microverse != Microverse.NONE) {
                textList.add(Component.translatable("microverse.monilabs.integrity",
                        (float) microverseIntegrity / FLUX_REPAIR_AMOUNT));
            }
        }
    }

    public void onMicroverseChange(Microverse oldMicroverse, Microverse newMicroverse) {
        scheduleRenderUpdate();
    }
}
