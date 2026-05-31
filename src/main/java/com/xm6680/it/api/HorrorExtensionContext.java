package com.xm6680.it.api;

import com.xm6680.it.analog.AnalogHorrorManager;
import com.xm6680.it.analog.MinorAnomalyAccumulator;
import com.xm6680.it.analog.ReceiverManager;
import com.xm6680.it.progression.HorrorProgressionManager;
import com.xm6680.it.watching.WatchingLevelManager;

/**
 * Stable access to core services for feature modules added later.
 */
public interface HorrorExtensionContext {
    WatchingLevelManager watchingLevelManager();

    HorrorProgressionManager progressionManager();

    ReceiverManager receiverManager();

    AnalogHorrorManager analogHorrorManager();

    MinorAnomalyAccumulator minorAnomalyAccumulator();
}
