package gg.xp.xivsupport.triggers.cactbot.hw;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.xivdata.data.Job;
import gg.xp.xivdata.data.duties.KnownDuty;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.HeadMarkerEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.state.combatstate.StatusEffectRepository;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerHeadmarker;
import gg.xp.xivsupport.events.triggers.util.RepeatSuppressor;

import java.time.Duration;
import java.util.Map;

// cactbot（Apache License 2.0）の ui/raidboss/data/03-hw/raid/a6s.ts をもとに移植
@CalloutRepo(name = "Alexander The Cuff Of The Son Savage", duty = KnownDuty.AlexanderTheCuffOfTheSonSavage)
public class A6s extends AutoChildEventHandler implements FilteredEventHandler {

	private static final long magicVulnerabilityId = 0x292;
	private static final long compressedWaterId = 0x3FF;
	private static final long compressedLightningId = 0x400;
	private static final Duration dropWarningLeadTime = Duration.ofSeconds(5);

	private XivState state;
	private StatusEffectRepository buffs;

	public A6s(XivState state, StatusEffectRepository buffs) {
		this.state = state;
		this.buffs = buffs;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.AlexanderTheCuffOfTheSonSavage);
	}

	private boolean canStun() {
		return state.playerJobMatches(job -> job.isTank() || job.isMeleeDps() || job == Job.BLU);
	}

	private final ModifiableCallout<AbilityCastStart> mindBlast = ModifiableCallout.durationBasedCall("Mind Blast", "Interrupt {event.source}");

	@HandleEvents
	public void mindBlastCast(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x15F3) && state.playerJobMatches(job -> job.caresAboutInterrupt() || job == Job.BLU)) {
			context.accept(mindBlast.getModified(event));
		}
	}

	private final ModifiableCallout<AbilityCastStart> hiddenMinefieldGet = ModifiableCallout.durationBasedCall("Hidden Minefield (Tank)", "Get Mines");
	private final ModifiableCallout<AbilityCastStart> hiddenMinefieldAvoid = ModifiableCallout.durationBasedCall("Hidden Minefield", "Avoid Mines");

	@HandleEvents
	public void hiddenMinefield(EventContext context, AbilityCastStart event) {
		if (!event.abilityIdMatches(0x15F7)) {
			return;
		}
		// 魔法被ダメージ上昇が残っているタンクは地雷を踏めない
		if (state.playerJobMatches(Job::isTank) && !buffs.isStatusOnPlayer(magicVulnerabilityId)) {
			context.accept(hiddenMinefieldGet.getModified(event));
		}
		else {
			context.accept(hiddenMinefieldAvoid.getModified(event));
		}
	}

	@NpcCastCallout(value = 0x15FB, suppressMs = 1_000)
	private final ModifiableCallout<AbilityCastStart> supercharge = ModifiableCallout.durationBasedCall("Supercharge", "Dodge Mirage Charge");

	@NpcCastCallout(value = 0x15FC, onYou = true)
	private final ModifiableCallout<AbilityCastStart> blinder = ModifiableCallout.durationBasedCall("Blinder", "Look Away from Mirage");

	@NpcCastCallout(value = 0x15FD, onYou = true)
	private final ModifiableCallout<AbilityCastStart> powerTackle = ModifiableCallout.durationBasedCall("Power Tackle", "Look Towards Mirage");

	private final ModifiableCallout<BuffApplied> lowArithmeticks = new ModifiableCallout<BuffApplied>("Low Arithmeticks", "Go High").statusIcon(0x3FD);
	private final ModifiableCallout<BuffApplied> highArithmeticks = new ModifiableCallout<BuffApplied>("High Arithmeticks", "Go Low").statusIcon(0x3FE);
	private final RepeatSuppressor lowArithmeticksSupp = new RepeatSuppressor(Duration.ofSeconds(10));
	private final RepeatSuppressor highArithmeticksSupp = new RepeatSuppressor(Duration.ofSeconds(10));

	@HandleEvents
	public void arithmeticks(EventContext context, BuffApplied event) {
		if (!event.getTarget().isThePlayer()) {
			return;
		}
		if (event.buffIdMatches(0x3FD) && lowArithmeticksSupp.check(event)) {
			context.accept(lowArithmeticks.getModified(event));
		}
		else if (event.buffIdMatches(0x3FE) && highArithmeticksSupp.check(event)) {
			context.accept(highArithmeticks.getModified(event));
		}
	}

	@NpcCastCallout(0x1610)
	private final ModifiableCallout<AbilityCastStart> bioArithmeticks = ModifiableCallout.durationBasedCall("Bio-arithmeticks", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> midanHardhelm = ModifiableCallout.durationBasedCall("Midan Hardhelm", "Stun");
	private final ModifiableCallout<AbilityCastStart> midanHardmind = ModifiableCallout.durationBasedCall("Midan Hardmind", "Stun");

	@HandleEvents
	public void midanCasts(EventContext context, AbilityCastStart event) {
		if (event.abilityIdMatches(0x1612) && canStun()) {
			context.accept(midanHardhelm.getModified(event));
		}
		else if (event.abilityIdMatches(0x1613) && canStun()) {
			context.accept(midanHardmind.getModified(event));
		}
	}

	private final ModifiableCallout<HeadMarkerEvent> enumeration = new ModifiableCallout<>("Enumeration", "{event.target}: {count}");

	@HandleEvents
	public void enumerationMarker(EventContext context, HeadMarkerEvent event) {
		if (event.markerIdMatches(0x40, 0x41, 0x42)) {
			// 0x40 が2人、0x41 が3人、0x42 が4人
			long count = 2 + event.getMarkerId() - 0x40;
			context.accept(enumeration.getModified(event, Map.of("count", count)));
		}
	}

	@NpcCastCallout(0x1627)
	private final ModifiableCallout<AbilityCastStart> superCyclone = ModifiableCallout.durationBasedCall("Super Cyclone", "Knockback");

	@NpcCastCallout(0x161A)
	private final ModifiableCallout<AbilityCastStart> ultraFlash = ModifiableCallout.durationBasedCall("Ultra Flash", "Hide Behind Ice");

	@PlayerHeadmarker(0x43)
	private final ModifiableCallout<HeadMarkerEvent> iceMarker = new ModifiableCallout<>("Ice Marker", "Ice: Freeze Tornado");

	@PlayerHeadmarker(0x19)
	private final ModifiableCallout<HeadMarkerEvent> fireBeam = new ModifiableCallout<>("Fire Beam", "Fire Beam on You");

	private final ModifiableCallout<BuffApplied> compressedWater = new ModifiableCallout<BuffApplied>("Compressed Water", "Water on You").statusIcon(compressedWaterId);
	private final ModifiableCallout<BuffApplied> compressedWaterDrop = ModifiableCallout.<BuffApplied>durationBasedCall("Compressed Water (Drop)", "Drop Water Soon").statusIcon(compressedWaterId);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> compressedWaterSq = SqtTemplates.sq(60_000,
			BuffApplied.class, ba -> ba.buffIdMatches(compressedWaterId) && ba.getTarget().isThePlayer(),
			(e1, s) -> {
				s.updateCall(compressedWater, e1);
				long msToWait = e1.getEstimatedRemainingDuration().minus(dropWarningLeadTime).toMillis();
				if (msToWait > 0) {
					s.waitMs(msToWait);
				}
				// 途中で他人に渡していれば何も言わない
				if (buffs.isStatusOnPlayer(compressedWaterId)) {
					s.updateCall(compressedWaterDrop, e1);
				}
			});

	private final ModifiableCallout<BuffApplied> compressedLightning = new ModifiableCallout<BuffApplied>("Compressed Lightning", "Lightning on You").statusIcon(compressedLightningId);
	private final ModifiableCallout<BuffApplied> compressedLightningDrop = ModifiableCallout.<BuffApplied>durationBasedCall("Compressed Lightning (Drop)", "Drop Lightning Soon").statusIcon(compressedLightningId);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> compressedLightningSq = SqtTemplates.sq(60_000,
			BuffApplied.class, ba -> ba.buffIdMatches(compressedLightningId) && ba.getTarget().isThePlayer(),
			(e1, s) -> {
				s.updateCall(compressedLightning, e1);
				long msToWait = e1.getEstimatedRemainingDuration().minus(dropWarningLeadTime).toMillis();
				if (msToWait > 0) {
					s.waitMs(msToWait);
				}
				if (buffs.isStatusOnPlayer(compressedLightningId)) {
					s.updateCall(compressedLightningDrop, e1);
				}
			});

}
