package gg.xp.xivsupport.triggers.cactbot.shb;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.Job;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.BuffRemoved;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;

// cactbot（Apache License 2.0）の ui/raidboss/data/05-shb/raid/e1s.ts をもとに移植
@CalloutRepo(name = "Edens Gate Resurrection Savage", duty = KnownDuty.EdensGateResurrectionSavage)
public class E1s extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long PARADISE_REGAINED = 0x7B6;
	private static final long PREY = 0x840;
	private static final long VICE_MARKER = 0xAE;

	private XivState state;

	public E1s(XivState state) {
		this.state = state;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.EdensGateResurrectionSavage);
	}

	private enum Vice {
		NONE,
		DPS,
		TANK,
		HEALER
	}

	// マーカーが付いた後に詠唱が来るので、詠唱を見て次のマーカーの種類を決めておく
	private static final Vice[] VICE_ORDER = {
			Vice.NONE,
			Vice.DPS, Vice.TANK, Vice.HEALER,
			Vice.TANK, Vice.DPS, Vice.HEALER,
			Vice.TANK, Vice.DPS, Vice.HEALER,
			Vice.TANK, Vice.DPS, Vice.HEALER
	};

	private int viceCount;
	private Vice vice = Vice.NONE;
	private boolean paradise;

	@HandleEvents
	public void reset(EventContext context, PullStartedEvent event) {
		viceCount = 0;
		vice = Vice.NONE;
		paradise = false;
	}

	@HandleEvents
	public void paradiseGain(EventContext context, BuffApplied event) {
		if (event.buffIdMatches(PARADISE_REGAINED) && !event.getTarget().isPc()) {
			paradise = true;
		}
	}

	@HandleEvents
	public void paradiseLose(EventContext context, BuffRemoved event) {
		if (event.buffIdMatches(PARADISE_REGAINED) && !event.getTarget().isPc()) {
			paradise = false;
		}
	}

	@HandleEvents
	public void viceTracker(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x3D70)) {
			if (viceCount == 0) {
				viceCount = 1;
				vice = Vice.DPS;
			}
			return;
		}
		if (!event.abilityIdMatches(0x44EF, 0x3D7A, 0x44EE, 0x3D78, 0x44F0, 0x3D7D)) {
			return;
		}
		viceCount++;
		vice = viceCount < VICE_ORDER.length ? VICE_ORDER[viceCount] : Vice.NONE;
		if (event.abilityIdMatches(0x3D7A, 0x3D78)) {
			vice = Vice.DPS;
		}
		else if (event.abilityIdMatches(0x44EE)) {
			vice = Vice.HEALER;
		}
		else if (event.abilityIdMatches(0x44F0, 0x3D7D)) {
			vice = Vice.TANK;
		}
	}

	@NpcCastCallout(0x3D70)
	private final ModifiableCallout<AbilityCastStart> edensGravity = ModifiableCallout.durationBasedCall("Eden's Gravity", "Raidwide");

	@NpcCastCallout(0x3D8B)
	private final ModifiableCallout<AbilityCastStart> fragorMaximus = ModifiableCallout.durationBasedCall("Fragor Maximus", "Raidwide");

	@NpcCastCallout(0x3D7F)
	private final ModifiableCallout<AbilityCastStart> dimensionalShift = ModifiableCallout.durationBasedCall("Dimensional Shift", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> spearOnYou = ModifiableCallout.durationBasedCall("Spear of Paradise (On You)", "Buster on You");
	private final ModifiableCallout<AbilityCastStart> spearSwap = ModifiableCallout.durationBasedCall("Spear of Paradise (Other Tank)", "Tank Swap");
	private final ModifiableCallout<AbilityCastStart> spear = ModifiableCallout.durationBasedCall("Spear of Paradise", "Buster on {event.target}");

	@HandleEvents
	public void spearOfParadise(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x3D88)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept(spearOnYou.getModified(event));
		}
		else if (state.playerJobMatches(Job::isTank)) {
			context.accept(spearSwap.getModified(event));
		}
		else {
			context.accept(spear.getModified(event));
		}
	}

	@NpcCastCallout(0x3D73)
	private final ModifiableCallout<AbilityCastStart> edensFlare = ModifiableCallout.durationBasedCall("Eden's Flare", "Get Under");

	@NpcCastCallout(0x44F4)
	private final ModifiableCallout<AbilityCastStart> deltaAttack1 = ModifiableCallout.durationBasedCall("Delta Attack 1", "Cross Spread");

	private final ModifiableCallout<AbilityCastStart> deltaAttack2Tank = ModifiableCallout.durationBasedCall("Delta Attack 2 (Tank)", "In, Spread");
	private final ModifiableCallout<AbilityCastStart> deltaAttack2 = ModifiableCallout.durationBasedCall("Delta Attack 2", "In, Stack Behind");

	@HandleEvents
	public void deltaAttack2(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x44F8)) {
			context.accept((state.playerJobMatches(Job::isTank) ? deltaAttack2Tank : deltaAttack2).getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> vicePuddle = new ModifiableCallout<>("Vice and Virtue: DPS Marker", "Puddle Spread");
	private final ModifiableCallout<HeadMarkerEvent> viceTankLaser = new ModifiableCallout<>("Vice and Virtue: Tank Marker", "Tank Laser on You");

	@HandleEvents
	public void viceMarker(EventContext context, HeadMarkerEvent event) {
		if (!event.markerIdMatches(VICE_MARKER) || !event.getTarget().isThePlayer()) {
			return;
		}
		if (vice == Vice.DPS && !paradise) {
			context.accept(vicePuddle.getModified(event));
		}
		else if (vice == Vice.TANK) {
			context.accept(viceTankLaser.getModified(event));
		}
	}

	@NpcCastCallout(0x3D7A)
	private final ModifiableCallout<AbilityCastStart> viceDps2 = ModifiableCallout.durationBasedCall("Vice and Virtue: DPS 2", "Stack with Partner");

	private final ModifiableCallout<AbilityCastStart> viceTankStack = ModifiableCallout.durationBasedCall("Vice and Virtue: Tank 2", "Stack in Front of Tank");

	@HandleEvents
	public void viceTankStack(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x3D78) && !state.playerJobMatches(Job::isTank)) {
			context.accept(viceTankStack.getModified(event));
		}
	}

	private final ModifiableCallout<BuffApplied> preyToDps = new ModifiableCallout<BuffApplied>("Prey on You (Paradise)", "Pass Prey to DPS").statusIcon(PREY);
	private final ModifiableCallout<BuffApplied> preyToTank = new ModifiableCallout<BuffApplied>("Prey on You", "Pass Prey to Tank").statusIcon(PREY);
	private final ModifiableCallout<BuffApplied> takePrey = new ModifiableCallout<BuffApplied>("Prey: Take from Healer", "Take Prey from Healer").statusIcon(PREY);
	private final RepeatSuppressor takePreySupp = new RepeatSuppressor(Duration.ofSeconds(20));

	@HandleEvents
	public void prey(EventContext context, BuffApplied event) {
		if (!event.buffIdMatches(PREY)) {
			return;
		}
		if (event.getTarget().isThePlayer()) {
			context.accept((paradise ? preyToDps : preyToTank).getModified(event));
			return;
		}
		// 楽園の有無で受け取るロールが変わる
		boolean receiver = paradise ? state.playerJobMatches(Job::isDps) : state.playerJobMatches(Job::isTank);
		if (receiver && takePreySupp.check(event)) {
			context.accept(takePrey.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> manaBoost = ModifiableCallout.durationBasedCall("Mana Boost", "Interrupt {event.source}");
	private final RepeatSuppressor manaBoostSupp = new RepeatSuppressor(Duration.ofSeconds(1));

	@HandleEvents
	public void manaBoost(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x3D8D) && state.playerJobMatches(Job::caresAboutInterrupt) && manaBoostSupp.check(event)) {
			context.accept(manaBoost.getModified(event));
		}
	}

	@NpcCastCallout(0x3D8A)
	private final ModifiableCallout<AbilityCastStart> pureLight = ModifiableCallout.durationBasedCall("Pure Light", "Get Behind");

	@NpcCastCallout(0x3D80)
	private final ModifiableCallout<AbilityCastStart> pureBeam1 = ModifiableCallout.durationBasedCall("Pure Beam 1", "Get Outside Your Orb");

	@NpcCastCallout(0x3D82)
	private final ModifiableCallout<AbilityCastStart> pureBeam2 = ModifiableCallout.durationBasedCall("Pure Beam 2", "Bait Orb Lasers Outside");

}
