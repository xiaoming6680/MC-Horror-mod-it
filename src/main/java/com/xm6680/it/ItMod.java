package com.xm6680.it;

import com.xm6680.it.analog.AnalogHorrorManager;
import com.xm6680.it.analog.MinorAnomalyAccumulator;
import com.xm6680.it.analog.ReceiverManager;
import com.xm6680.it.cavestalker.CaveStalkerManager;
import com.xm6680.it.chase.ChaseManager;
import com.xm6680.it.command.ItCommandRegistration;
import com.xm6680.it.config.ItConfigManager;
import com.xm6680.it.entity.ModEntities;
import com.xm6680.it.entity.TargetOnlyEntityVisibility;
import com.xm6680.it.event.ItServerEvents;
import com.xm6680.it.event.AnimalDisguiseManager;
import com.xm6680.it.event.MultiplayerDreadManager;
import com.xm6680.it.item.ModItems;
import com.xm6680.it.jumpscare.JumpscareManager;
import com.xm6680.it.network.ItNetwork;
import com.xm6680.it.progression.HorrorProgressionManager;
import com.xm6680.it.runtime.ItManagers;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the It mod.
 */
public class ItMod implements ModInitializer {
    public static final String MOD_ID = "it";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final ItManagers MANAGERS = new ItManagers();

    @Override
    public void onInitialize() {
        ModItems.register();
        ModEntities.register();
        TargetOnlyEntityVisibility.register();
        ItConfigManager.load();
        ItNetwork.registerCommon();
        ItServerEvents.register(MANAGERS);
        ItCommandRegistration.register(MANAGERS);

        LOGGER.info("It initialized.");
    }

    public static ItManagers getManagers() {
        return MANAGERS;
    }

    public static HorrorProgressionManager getHorrorProgressionManager() {
        return MANAGERS.progressionManager();
    }

    public static ReceiverManager getReceiverManager() {
        return MANAGERS.receiverManager();
    }

    public static AnalogHorrorManager getAnalogHorrorManager() {
        return MANAGERS.analogHorrorManager();
    }

    public static MinorAnomalyAccumulator getMinorAnomalyAccumulator() {
        return MANAGERS.minorAnomalyAccumulator();
    }

    public static JumpscareManager getJumpscareManager() {
        return MANAGERS.jumpscareManager();
    }

    public static ChaseManager getChaseManager() {
        return MANAGERS.chaseManager();
    }

    public static CaveStalkerManager getCaveStalkerManager() {
        return MANAGERS.caveStalkerManager();
    }

    public static MultiplayerDreadManager getMultiplayerDreadManager() {
        return MANAGERS.multiplayerDreadManager();
    }

    public static AnimalDisguiseManager getAnimalDisguiseManager() {
        return MANAGERS.animalDisguiseManager();
    }
}
