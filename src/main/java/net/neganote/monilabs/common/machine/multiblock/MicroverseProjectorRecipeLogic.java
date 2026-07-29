package net.neganote.monilabs.common.machine.multiblock;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.modifier.ParallelLogic;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.ForgeRegistries;
import net.neganote.monilabs.config.MoniConfig;

import java.util.Collections;

import static net.neganote.monilabs.common.machine.multiblock.MicroverseProjectorMachine.FLUX_REPAIR_AMOUNT;
import static net.neganote.monilabs.common.machine.multiblock.MicroverseProjectorMachine.MICROVERSE_MAX_INTEGRITY;

public class MicroverseProjectorRecipeLogic extends RecipeLogic {

    public MicroverseProjectorRecipeLogic(MicroverseProjectorMachine microverseProjectorMachine) {
        super(microverseProjectorMachine);
    }

    @Override
    public void handleRecipeWorking() {
        super.handleRecipeWorking();
        MicroverseProjectorMachine projector = (MicroverseProjectorMachine) machine;

        var activeRecipe = getLastRecipe();

        if (activeRecipe != null && activeRecipe.data.contains("damage_rate")) {
            int decayRate = activeRecipe.data.getInt("damage_rate");
            decayRate *= activeRecipe.parallels;

            var originalDuration = activeRecipe.data.getInt("duration");

            var durationDifference = originalDuration / activeRecipe.duration;
            decayRate *= durationDifference;

            projector.microverseIntegrity = Math.min(Math.max(projector.microverseIntegrity - decayRate, 0),
                    MICROVERSE_MAX_INTEGRITY);
            if (projector.microverseIntegrity == 0 && projector.microverse != Microverse.NONE) {
                if (MoniConfig.INSTANCE.values.microminerReturnedOnZeroIntegrity) {
                    var microMinerItem = ((Ingredient) activeRecipe.getInputContents(ItemRecipeCapability.CAP).get(0)
                            .getContent()).getItems()[0];

                    var microMinerRecipe = GTRecipeBuilder.ofRaw().outputItems(microMinerItem).buildRawRecipe();

                    RecipeHelper.handleRecipe(projector, microMinerRecipe, IO.OUT, microMinerRecipe.outputs,
                            Collections.emptyMap(), false, false);
                }

                if (projector.microverse == Microverse.SHATTERED) {
                    projector.microverseIntegrity = MICROVERSE_MAX_INTEGRITY >> 1; // start at half integrity
                    projector.microverse = Microverse.CORRUPTED;
                } else {
                    projector.microverseIntegrity = 0;
                    projector.microverse = Microverse.NONE;
                }
                resetRecipeLogic();
            }
        }
    }

    @Override
    public void serverTick() {
        super.serverTick();

        MicroverseProjectorMachine projector = (MicroverseProjectorMachine) machine;

        var quantumFluxItem = ForgeRegistries.ITEMS.getValue(ResourceLocation.bySeparator("kubejs:quantum_flux", ':'));

        assert quantumFluxItem != null;
        var quantumFluxRecipe = GTRecipeBuilder.ofRaw().inputItems(quantumFluxItem).buildRawRecipe();

        if (projector.microverse.isRepairable) {
            var missingHealth = MICROVERSE_MAX_INTEGRITY - projector.microverseIntegrity;
            var fluxToFullHeal = missingHealth / FLUX_REPAIR_AMOUNT;
            var fluxAvailable = ParallelLogic.getMaxByInput(projector, quantumFluxRecipe, Integer.MAX_VALUE,
                    Collections.emptyList());

            var fluxToConsume = projector.microverse.isHungry ? fluxAvailable : Math.min(fluxToFullHeal, fluxAvailable);

            if (fluxToConsume > 0) {
                var scaledRecipe = quantumFluxRecipe.copy(new ContentModifier(fluxToConsume, 0.0));

                RecipeHelper.handleRecipe(projector, scaledRecipe, IO.IN, scaledRecipe.inputs, Collections.emptyMap(),
                        false, false);

                var usedToHeal = Math.min(fluxToFullHeal, fluxToConsume);
                projector.microverseIntegrity += usedToHeal * FLUX_REPAIR_AMOUNT;

                if (projector.microverse.isHungry && fluxToConsume > usedToHeal) {
                    int rollbackCount = fluxToConsume - usedToHeal;
                    if (getLastRecipe() != null && getProgress() > 1) {
                        setProgress(Math.max(1, getProgress() - (20 * rollbackCount)));
                    }
                }
            }
        }
        if (projector.microverse.decayRate != 0) {
            int decayRate = projector.microverse.decayRate;
            projector.microverseIntegrity -= decayRate;
            if (projector.microverseIntegrity <= 0) {
                projector.updateMicroverse(0, false);
            }
        }
    }
}
