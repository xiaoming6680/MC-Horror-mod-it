package com.xm6680.it.runtime;

import com.xm6680.it.analog.AnalogHorrorManager;
import com.xm6680.it.analog.MinorAnomalyAccumulator;
import com.xm6680.it.analog.ReceiverManager;
import com.xm6680.it.api.HorrorExtension;
import com.xm6680.it.api.HorrorExtensionContext;
import com.xm6680.it.cavestalker.CaveStalkerManager;
import com.xm6680.it.chase.ChaseManager;
import com.xm6680.it.director.AvoidanceManager;
import com.xm6680.it.director.GroupDreadManager;
import com.xm6680.it.director.HorrorDirectorManager;
import com.xm6680.it.director.PlayerContextDetector;
import com.xm6680.it.director.SkyborneHorrorManager;
import com.xm6680.it.effect.ClientDistortionManager;
import com.xm6680.it.event.AnimalDisguiseManager;
import com.xm6680.it.event.HorrorEventManager;
import com.xm6680.it.event.MultiplayerDreadManager;
import com.xm6680.it.event.NetherSignalManager;
import com.xm6680.it.event.WatcherSpawnManager;
import com.xm6680.it.event.WorldAnomalyManager;
import com.xm6680.it.jumpscare.JumpscareManager;
import com.xm6680.it.manifestation.ManifestationManager;
import com.xm6680.it.persistence.ItPersistentDataManager;
import com.xm6680.it.progression.HorrorProgressionManager;
import com.xm6680.it.watching.WatchingLevelManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Owns the server-side manager graph and exposes extension points.
 */
public final class ItManagers implements HorrorExtensionContext {
    private final WatchingLevelManager watchingLevelManager = new WatchingLevelManager();
    private final HorrorProgressionManager progressionManager = new HorrorProgressionManager(watchingLevelManager);
    private final ReceiverManager receiverManager = new ReceiverManager(progressionManager);
    private final AnalogHorrorManager analogHorrorManager = new AnalogHorrorManager(progressionManager, receiverManager);
    private final MinorAnomalyAccumulator minorAnomalyAccumulator = new MinorAnomalyAccumulator(progressionManager, receiverManager);
    private final ManifestationManager manifestationManager = new ManifestationManager(progressionManager, analogHorrorManager);
    private final JumpscareManager jumpscareManager = new JumpscareManager(progressionManager, analogHorrorManager, manifestationManager);
    private final ChaseManager chaseManager = new ChaseManager(progressionManager, receiverManager);
    private final CaveStalkerManager caveStalkerManager = new CaveStalkerManager(progressionManager, receiverManager, chaseManager);
    private final ClientDistortionManager clientDistortionManager = new ClientDistortionManager(progressionManager);
    private final HorrorEventManager horrorEventManager = new HorrorEventManager(watchingLevelManager, progressionManager, analogHorrorManager);
    private final WatcherSpawnManager watcherSpawnManager = new WatcherSpawnManager(watchingLevelManager, progressionManager, analogHorrorManager, chaseManager, caveStalkerManager);
    private final WorldAnomalyManager worldAnomalyManager = new WorldAnomalyManager(progressionManager, receiverManager);
    private final MultiplayerDreadManager multiplayerDreadManager = new MultiplayerDreadManager(progressionManager, receiverManager);
    private final AnimalDisguiseManager animalDisguiseManager = new AnimalDisguiseManager(progressionManager, receiverManager);
    private final NetherSignalManager netherSignalManager = new NetherSignalManager(progressionManager, receiverManager);
    private final PlayerContextDetector playerContextDetector = new PlayerContextDetector();
    private final AvoidanceManager avoidanceManager = new AvoidanceManager(progressionManager, playerContextDetector, receiverManager);
    private final GroupDreadManager groupDreadManager = new GroupDreadManager(progressionManager, playerContextDetector);
    private final SkyborneHorrorManager skyborneHorrorManager = new SkyborneHorrorManager(progressionManager, receiverManager, multiplayerDreadManager);
    private final HorrorDirectorManager horrorDirectorManager = new HorrorDirectorManager(
            progressionManager,
            receiverManager,
            analogHorrorManager,
            horrorEventManager,
            watcherSpawnManager,
            worldAnomalyManager,
            multiplayerDreadManager,
            animalDisguiseManager,
            netherSignalManager,
            manifestationManager,
            jumpscareManager,
            chaseManager,
            caveStalkerManager,
            clientDistortionManager,
            playerContextDetector,
            avoidanceManager,
            groupDreadManager,
            skyborneHorrorManager
    );
    private final ItPersistentDataManager persistentDataManager = new ItPersistentDataManager(progressionManager, receiverManager);
    private final List<HorrorExtension> extensions = new ArrayList<>();

    public ItManagers() {
        manifestationManager.setChaseManager(chaseManager);
        manifestationManager.setCaveStalkerManager(caveStalkerManager);
        manifestationManager.setMultiplayerDreadManager(multiplayerDreadManager);
    }

    public void registerExtension(HorrorExtension extension) {
        extensions.add(extension);
        extension.onRegister(this);
    }

    public List<HorrorExtension> extensions() {
        return Collections.unmodifiableList(extensions);
    }

    @Override
    public WatchingLevelManager watchingLevelManager() {
        return watchingLevelManager;
    }

    @Override
    public HorrorProgressionManager progressionManager() {
        return progressionManager;
    }

    @Override
    public ReceiverManager receiverManager() {
        return receiverManager;
    }

    @Override
    public AnalogHorrorManager analogHorrorManager() {
        return analogHorrorManager;
    }

    @Override
    public MinorAnomalyAccumulator minorAnomalyAccumulator() {
        return minorAnomalyAccumulator;
    }

    public ManifestationManager manifestationManager() {
        return manifestationManager;
    }

    public JumpscareManager jumpscareManager() {
        return jumpscareManager;
    }

    public ChaseManager chaseManager() {
        return chaseManager;
    }

    public CaveStalkerManager caveStalkerManager() {
        return caveStalkerManager;
    }

    public ClientDistortionManager clientDistortionManager() {
        return clientDistortionManager;
    }

    public HorrorEventManager horrorEventManager() {
        return horrorEventManager;
    }

    public WatcherSpawnManager watcherSpawnManager() {
        return watcherSpawnManager;
    }

    public WorldAnomalyManager worldAnomalyManager() {
        return worldAnomalyManager;
    }

    public MultiplayerDreadManager multiplayerDreadManager() {
        return multiplayerDreadManager;
    }

    public AnimalDisguiseManager animalDisguiseManager() {
        return animalDisguiseManager;
    }

    public NetherSignalManager netherSignalManager() {
        return netherSignalManager;
    }

    public PlayerContextDetector playerContextDetector() {
        return playerContextDetector;
    }

    public AvoidanceManager avoidanceManager() {
        return avoidanceManager;
    }

    public GroupDreadManager groupDreadManager() {
        return groupDreadManager;
    }

    public SkyborneHorrorManager skyborneHorrorManager() {
        return skyborneHorrorManager;
    }

    public HorrorDirectorManager horrorDirectorManager() {
        return horrorDirectorManager;
    }

    public ItPersistentDataManager persistentDataManager() {
        return persistentDataManager;
    }
}
